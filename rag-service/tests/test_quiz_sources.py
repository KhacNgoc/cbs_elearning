import unittest
from types import SimpleNamespace
from unittest.mock import AsyncMock, patch
from pydantic import ValidationError
from qdrant_client import QdrantClient, models
from app.quiz_scope import quiz_source_filter
from app.schemas import QuizGenerationRequest
from app.services.quiz import generate_quiz
from app.services.documents import document_outline


class SourceBoundaryTests(unittest.TestCase):
    def setUp(self):
        self.client = QdrantClient(":memory:")
        self.client.create_collection("test", vectors_config=models.VectorParams(size=2, distance=models.Distance.COSINE))
        self.points = [
            (1, "C1", "D1", 1, "Hồ sơ"), (2, "C1", "D1", 2, "Giải ngân"),
            (3, "C1", "D1", 3, "Giải ngân"), (4, "C1", "D1", 4, "Phê duyệt"),
            (5, "C1", "D2", 1, "Giải ngân"), (6, "C1", "D2", 3, "Phê duyệt"),
            (7, "C2", "D1", 2, "Giải ngân"),
        ]
        self.client.upsert("test", points=[models.PointStruct(id=i, vector=[1., 0.], payload={
            "course_id": course, "document_id": doc, "page": page, "section": section,
        }) for i, course, doc, page, section in self.points])

    def tearDown(self):
        self.client.close()

    def retrieve(self, sources):
        request = QuizGenerationRequest(course_id="C1", course_name="Cho vay", lesson_id="L1", lesson_name="Giải ngân", sources=sources)
        points, _ = self.client.scroll("test", scroll_filter=quiz_source_filter(request), limit=100)
        return {point.id for point in points}

    def test_pdf_range_stays_inside_document_and_course(self):
        self.assertEqual({2, 3}, self.retrieve([{"document_id": "D1", "page_from": 2, "page_to": 3}]))

    def test_multiple_sources_keep_individual_ranges(self):
        self.assertEqual({2, 3, 5}, self.retrieve([
            {"document_id": "D1", "page_from": 2, "page_to": 3},
            {"document_id": "D2", "page_from": 1, "page_to": 1},
        ]))

    def test_word_section_is_exact_and_course_scoped(self):
        self.assertEqual({2, 3}, self.retrieve([{"document_id": "D1", "section": "Giải ngân"}]))

    def test_whole_document_is_not_whole_course(self):
        self.assertEqual({1, 2, 3, 4}, self.retrieve([{"document_id": "D1"}]))

    def test_empty_selection_has_no_course_fallback(self):
        self.assertEqual(set(), self.retrieve([{"document_id": "D1", "page_from": 8, "page_to": 9}]))

    def test_lesson_sources_are_mandatory_even_for_legacy_lesson_name(self):
        for extra in [{"lesson_id": "L1"}, {"lesson_name": "Giải ngân"}]:
            with self.assertRaises(ValidationError):
                QuizGenerationRequest(course_id="C1", course_name="Cho vay", **extra)

    def test_invalid_ranges_and_mixed_modes_are_rejected(self):
        for source in [
            {"document_id": "D1", "page_from": 1},
            {"document_id": "D1", "page_from": 4, "page_to": 2},
            {"document_id": "D1", "page_from": 0, "page_to": 2},
            {"document_id": "D1", "page_from": 1, "page_to": 2, "section": "Giải ngân"},
        ]:
            with self.assertRaises(ValidationError):
                QuizGenerationRequest(course_id="C1", course_name="Cho vay", sources=[source])

    def test_course_quiz_keeps_course_scope(self):
        request = QuizGenerationRequest(course_id="C1", course_name="Cho vay")
        points, _ = self.client.scroll("test", scroll_filter=quiz_source_filter(request), limit=100)
        self.assertEqual({1, 2, 3, 4, 5, 6}, {point.id for point in points})


class SourceFlowTests(unittest.IsolatedAsyncioTestCase):
    async def test_lesson_scope_verification_runs_even_when_course_verification_is_disabled(self):
        point = SimpleNamespace(score=1.0, payload={"document_id": "D1", "course_id": "C1",
            "page": 2, "section": "Giải ngân", "full_text": "Kiểm tra thông tin khách hàng trước khi giải ngân khoản vay."})
        model = AsyncMock(side_effect=[
            '{"q":"Trước giải ngân cần kiểm tra thông tin nào?","o":["Khách hàng","Lịch nghỉ","Văn phòng","Trang phục"],"a":0}',
            '{"reason":"Nội dung không phù hợp nguồn bài học.","correct_answers":[],"well_formed":false}',
        ])
        with patch("app.infrastructure.ensure_collection", new=AsyncMock()), \
             patch("app.infrastructure.embeddings", new=AsyncMock(return_value=[[1., 0.]] * 4)), \
             patch("app.infrastructure.qdrant.query_points", new=AsyncMock(return_value=SimpleNamespace(points=[point]))), \
             patch("app.infrastructure.ollama_chat", new=model), \
             patch("app.config.QUIZ_LLM_VERIFICATION_ENABLED", False):
            request = QuizGenerationRequest(course_id="C1", course_name="Cho vay", lesson_id="L1", lesson_name="Giải ngân",
                single_choice_count=1, sources=[{"document_id": "D1", "page_from": 2, "page_to": 2}])
            result = await generate_quiz(request)
            self.assertEqual([], result.questions)
            self.assertEqual(2, model.await_count)
            verification = model.await_args_list[1].args[1][1]["content"]
            self.assertIn("trực tiếp phù hợp với bài học", verification)
            self.assertIn("Giải ngân", verification)
            self.assertIn("Phạm vi nguồn được giữ nguyên", result.warnings[-1])

    async def test_no_results_reports_insufficient_sources_without_broadening(self):
        query = AsyncMock(return_value=SimpleNamespace(points=[]))
        with patch("app.infrastructure.ensure_collection", new=AsyncMock()), \
             patch("app.infrastructure.embeddings", new=AsyncMock(return_value=[[1., 0.]] * 4)), \
             patch("app.infrastructure.qdrant.query_points", new=query), \
             patch("app.infrastructure.ollama_chat", new=AsyncMock()) as generate:
            request = QuizGenerationRequest(course_id="C1", course_name="Cho vay", lesson_id="L1",
                                            sources=[{"document_id": "D1", "page_from": 2, "page_to": 3}])
            result = await generate_quiz(request)
            self.assertEqual([], result.questions)
            self.assertIn("nguồn đã chọn", result.warnings[0])
            self.assertEqual(4, query.await_count)
            for call in query.await_args_list:
                self.assertEqual(quiz_source_filter(request), call.kwargs["query_filter"])
            generate.assert_not_called()

    async def test_outline_reads_all_batches_without_reembedding(self):
        point = lambda page, section: SimpleNamespace(payload={"page": page, "section": section, "excerpt": "Nội dung"})
        scroll = AsyncMock(side_effect=[([point(2, "A")], "next"), ([point(5, "B"), point(2, "A")], None)])
        with patch("app.infrastructure.qdrant.collection_exists", new=AsyncMock(return_value=True)), \
             patch("app.infrastructure.qdrant.scroll", new=scroll), \
             patch("app.infrastructure.embeddings", new=AsyncMock()) as embed:
            outline = await document_outline("D1", "C1")
            self.assertEqual([2, 5], outline["pages"])
            self.assertEqual(["A", "B"], outline["sections"])
            self.assertEqual(2, len(outline["excerpts"]))
            self.assertEqual("next", scroll.await_args_list[1].kwargs["offset"])
            must = scroll.await_args_list[0].kwargs["scroll_filter"].must
            self.assertEqual(["course_id", "document_id"], [condition.key for condition in must])
            embed.assert_not_called()


if __name__ == "__main__":
    unittest.main()
