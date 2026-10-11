import unittest
from types import SimpleNamespace
from unittest.mock import AsyncMock, patch
from fastapi import HTTPException
from app import infrastructure
from app.schemas import ChatRequest
from app.services.chat import answer_chat


class ChatResilienceTests(unittest.IsolatedAsyncioTestCase):
    async def run_chat(self, responses):
        point = SimpleNamespace(score=0.95, payload={
            "document_id": "D1", "course_id": "C1", "file_name": "course.docx",
            "page": 1, "section": "Giải ngân", "full_text": "Luồng thực hành quy trình giải ngân khoản vay: tạo hồ sơ, kiểm tra, phê duyệt."
        })
        model = AsyncMock(side_effect=responses)
        with patch("app.services.chat.infrastructure.ensure_collection", AsyncMock()), \
             patch("app.services.chat.infrastructure.embeddings", AsyncMock(return_value=[[1., 0.]])), \
             patch("app.services.chat.infrastructure.qdrant.query_points", AsyncMock(return_value=SimpleNamespace(points=[point]))), \
             patch("app.services.chat.infrastructure.ollama_chat", model):
            result = await answer_chat(ChatRequest(question="luồng thực hành quy trình giải ngân khoản vay", course_id="C1"))
        return result, model

    async def test_truncated_output_retries_without_exposing_partial_answer(self):
        result, model = await self.run_chat([
            infrastructure.ModelOutputTruncated("length"),
            '{"answer":"Tạo hồ sơ → kiểm tra → phê duyệt.","source_ids":["SOURCE_1"]}',
        ])
        self.assertTrue(result.grounded)
        self.assertEqual(2, model.await_count)
        self.assertEqual("D1", result.sources[0].documentId)

    async def test_invalid_json_retries_once(self):
        result, model = await self.run_chat([
            '{"answer":"unfinished',
            '{"answer":"Kiểm tra hồ sơ.","source_ids":["SOURCE_1"]}',
        ])
        self.assertTrue(result.grounded)
        self.assertEqual(2, model.await_count)

    async def test_repeated_truncation_is_output_error_not_connection_error(self):
        with self.assertRaises(HTTPException) as caught:
            await self.run_chat([infrastructure.ModelOutputTruncated("length")] * 2)
        self.assertEqual(502, caught.exception.status_code)

    async def test_generation_timeout_is_distinct(self):
        with self.assertRaises(HTTPException) as caught:
            await self.run_chat([TimeoutError()])
        self.assertEqual(504, caught.exception.status_code)

    async def test_transport_reports_output_token_exhaustion(self):
        response = SimpleNamespace(status_code=200, json=lambda: {
            "done_reason": "length", "eval_count": 192, "message": {"content": '{"answer":"unfinished'}
        })
        client = SimpleNamespace(post=AsyncMock(return_value=response))
        with patch("app.infrastructure.get_ollama_http_client", return_value=client):
            with self.assertRaises(infrastructure.ModelOutputTruncated):
                await infrastructure.ollama_chat("test", [], 5)
