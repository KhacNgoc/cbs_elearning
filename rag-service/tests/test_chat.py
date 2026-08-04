import json
import unittest
from types import SimpleNamespace
from unittest.mock import patch

from app import main


async def no_op_collection():
    return None


async def fake_embedding(_texts, *_args):
    return [[0.1, 0.2, 0.3]]


class FakeQdrant:
    def __init__(self, points):
        self.points = points

    async def query_points(self, **_kwargs):
        return SimpleNamespace(points=self.points)


class FakeResponse:
    status_code = 200
    text = ""

    def __init__(self, content):
        self.content = content

    def json(self):
        return {"message": {"content": json.dumps(self.content)}}


class FakeHttpClient:
    response_content = {}

    def __init__(self, **_kwargs):
        pass

    async def __aenter__(self):
        return self

    async def __aexit__(self, *_args):
        return None

    async def post(self, *_args, **_kwargs):
        return FakeResponse(self.response_content)


class ChatGroundingTest(unittest.IsolatedAsyncioTestCase):
    async def test_low_retrieval_score_refuses_without_calling_llm(self):
        low_score_point = SimpleNamespace(score=0.1, payload={})
        with patch.object(main, "ensure_collection", no_op_collection), \
                patch.object(main, "embeddings", fake_embedding), \
                patch.object(main, "qdrant", FakeQdrant([low_score_point])), \
                patch.object(main.httpx, "AsyncClient", side_effect=AssertionError("LLM must not be called")):
            result = await main.chat(main.ChatRequest(question="Không có trong tài liệu", course_id="CIF"))

        self.assertFalse(result.grounded)
        self.assertEqual(main.REFUSAL_ANSWER, result.answer)
        self.assertEqual([], result.sources)

    async def test_valid_source_id_returns_grounded_answer(self):
        point = SimpleNamespace(score=0.9, payload={
            "document_id": "doc-1",
            "file_name": "Quy trình.pdf",
            "page": 12,
            "section": "Điều kiện phê duyệt",
            "full_text": "Hồ sơ được phê duyệt khi đáp ứng đủ điều kiện.",
        })
        FakeHttpClient.response_content = {
            "answer": "Hồ sơ được phê duyệt khi đáp ứng đủ điều kiện.",
            "source_ids": ["SOURCE_1"],
        }
        with patch.object(main, "ensure_collection", no_op_collection), \
                patch.object(main, "embeddings", fake_embedding), \
                patch.object(main, "qdrant", FakeQdrant([point])), \
                patch.object(main.httpx, "AsyncClient", FakeHttpClient):
            result = await main.chat(main.ChatRequest(question="Khi nào hồ sơ được duyệt?", course_id="CIF"))

        self.assertTrue(result.grounded)
        self.assertEqual("doc-1", result.sources[0].documentId)
        self.assertEqual("Điều kiện phê duyệt", result.sources[0].section)

    async def test_unknown_model_source_id_is_rejected(self):
        point = SimpleNamespace(score=0.9, payload={
            "document_id": "doc-1",
            "file_name": "Quy trình.pdf",
            "page": 1,
            "full_text": "Nội dung có liên quan.",
        })
        FakeHttpClient.response_content = {
            "answer": "Một câu trả lời không có nguồn hợp lệ.",
            "source_ids": ["SOURCE_999"],
        }
        with patch.object(main, "ensure_collection", no_op_collection), \
                patch.object(main, "embeddings", fake_embedding), \
                patch.object(main, "qdrant", FakeQdrant([point])), \
                patch.object(main.httpx, "AsyncClient", FakeHttpClient):
            result = await main.chat(main.ChatRequest(question="Câu hỏi", course_id="CIF"))

        self.assertFalse(result.grounded)
        self.assertEqual(main.REFUSAL_ANSWER, result.answer)
        self.assertEqual([], result.sources)


if __name__ == "__main__":
    unittest.main()
