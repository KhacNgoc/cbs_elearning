import unittest
from types import SimpleNamespace
from unittest.mock import AsyncMock, patch

from qdrant_client import models

from app import config
from app.schemas import ChatRequest
from app.services.chat import answer_chat, resolve_course_scope
from app.utils import find_mentioned_course


def point(course_id, document_id, score=0.95):
    return SimpleNamespace(score=score, payload={
        "document_id": document_id, "course_id": course_id, "file_name": f"{document_id}.docx",
        "page": 1, "section": "Giải ngân",
        "full_text": "Quy trình giải ngân khoản vay: tạo hồ sơ, kiểm tra, phê duyệt.",
    })


class CrossCourseChatTests(unittest.IsolatedAsyncioTestCase):
    async def run_chat(self, request, points, answer='{"answer":"Tạo hồ sơ.","source_ids":["SOURCE_1"]}'):
        query = AsyncMock(return_value=SimpleNamespace(points=points))
        model = AsyncMock(return_value=answer)
        with patch("app.services.chat.infrastructure.ensure_collection", AsyncMock()), \
             patch("app.services.chat.infrastructure.ensure_course_index", AsyncMock()), \
             patch("app.services.chat.infrastructure.embeddings", AsyncMock(return_value=[[1., 0.]])), \
             patch("app.services.chat.infrastructure.qdrant.query_points", query), \
             patch("app.services.chat.infrastructure.ollama_chat", model):
            result = await answer_chat(request)
        return result, query, model

    @staticmethod
    def course_condition(query):
        conditions = query.await_args.kwargs["query_filter"].must
        return next(c for c in conditions if c.key == "course_id").match

    async def test_searches_only_accessible_courses(self):
        request = ChatRequest(question="quy trình giải ngân khoản vay", course_ids=["C1", "C2"],
                              course_names={"C1": "Cho vay", "C2": "Tiền gửi"})
        result, query, _ = await self.run_chat(request, [point("C2", "D2")])
        match = self.course_condition(query)
        self.assertIsInstance(match, models.MatchAny)
        self.assertEqual(["C1", "C2"], match.any)
        self.assertTrue(result.grounded)
        self.assertEqual("C2", result.sources[0].courseId)
        self.assertEqual("Tiền gửi", result.sources[0].courseName)

    async def test_no_accessible_course_never_queries_qdrant(self):
        result, query, model = await self.run_chat(ChatRequest(question="giải ngân", course_ids=[]), [])
        self.assertEqual(config.NO_ACCESSIBLE_COURSE_ANSWER, result.answer)
        query.assert_not_awaited()
        model.assert_not_awaited()

    async def test_context_course_is_preferred_without_excluding_others(self):
        request = ChatRequest(question="quy trình giải ngân khoản vay", course_id="C2", course_name="Tiền gửi",
                              course_ids=["C1", "C2"], course_names={"C1": "Cho vay"})
        result, query, _ = await self.run_chat(request, [point("C1", "D1", 0.95), point("C2", "D2", 0.93)])
        self.assertIsInstance(self.course_condition(query), models.MatchAny)
        self.assertEqual("D2", result.sources[0].documentId)

    async def test_course_summary_without_focus_asks_which_course(self):
        request = ChatRequest(question="Tóm tắt nội dung chính của khóa học", course_ids=["C1", "C2"],
                              course_names={"C1": "Cho vay", "C2": "Tiền gửi"})
        result, query, _ = await self.run_chat(request, [])
        self.assertTrue(result.needsClarification)
        query.assert_not_awaited()

    async def test_mentioned_course_narrows_search(self):
        request = ChatRequest(question="Tóm tắt khóa tien gui", course_ids=["C1", "C2"],
                              course_names={"C1": "Cho vay", "C2": "Tiền gửi"})
        _, query, _ = await self.run_chat(request, [point("C2", "D2")])
        match = self.course_condition(query)
        self.assertIsInstance(match, models.MatchValue)
        self.assertEqual("C2", match.value)

    def test_legacy_single_course_request_is_unchanged(self):
        self.assertEqual((["C1"], "C1"), resolve_course_scope(ChatRequest(question="x", course_id="C1"), "x"))
        self.assertEqual((None, None), resolve_course_scope(ChatRequest(question="x"), "x"))

    def test_course_name_matching_ignores_diacritics_and_partial_words(self):
        names = {"C1": "Cho vay", "C2": "Cho vay tiêu dùng"}
        self.assertEqual("C2", find_mentioned_course("Hỏi về cho vay tieu dung", names))
        self.assertIsNone(find_mentioned_course("cho vayx", names))
