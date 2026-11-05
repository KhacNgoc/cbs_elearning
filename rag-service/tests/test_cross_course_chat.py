import asyncio
import unittest
from types import SimpleNamespace
from unittest.mock import AsyncMock, patch

import httpx
from qdrant_client import models

from app import config
from app.schemas import ChatRequest
from app.services.chat import answer_chat, question_has_topic, resolve_course_scope, resume_clarified_question
from app.utils import drop_navigation_results, find_mentioned_course, find_replied_courses, is_course_summary_question, is_generic_question


def point(course_id, document_id, score=0.95, section="Giải ngân"):
    return SimpleNamespace(score=score, payload={
        "document_id": document_id, "course_id": course_id, "file_name": f"{document_id}.docx",
        "page": 1, "section": section,
        "full_text": "Quy trình giải ngân khoản vay: tạo hồ sơ, kiểm tra, phê duyệt.",
    })


class CrossCourseChatTests(unittest.IsolatedAsyncioTestCase):
    async def run_chat(self, request, points, answer='{"answer":"Tạo hồ sơ.","source_ids":["SOURCE_1"]}',
                       topic='{"has_topic":true}'):
        query = AsyncMock(return_value=SimpleNamespace(points=points))

        async def respond(model_name, messages, timeout, **kwargs):
            if "has_topic" in (kwargs.get("json_schema") or {}).get("properties", {}):
                if isinstance(topic, Exception):
                    raise topic
                return topic
            return answer

        model = AsyncMock(side_effect=respond)
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
        self.assertEqual(
            "Theo tài liệu khóa Tiền gửi:\n\nTạo hồ sơ.\n\nNếu bạn muốn hỏi về khóa khác, hãy nêu tên khóa đó.",
            result.answer,
        )
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

    async def test_generic_question_without_focus_asks_which_course(self):
        request = ChatRequest(question="Các bước nghiệp vụ quan trọng là gì?", course_ids=["C1", "C2"],
                              course_names={"C1": "Cho vay", "C2": "Tiền gửi"})
        result, query, model = await self.run_chat(request, [point("C1", "D1")])
        self.assertTrue(result.needsClarification)
        self.assertIn("Cho vay, Tiền gửi", result.answer)
        query.assert_not_awaited()
        model.assert_not_awaited()

    async def test_generic_question_inside_a_course_is_answered(self):
        request = ChatRequest(question="Những điều kiện nào cần lưu ý?", course_id="C1", course_name="Cho vay",
                              course_ids=["C1", "C2"], course_names={"C1": "Cho vay", "C2": "Tiền gửi"})
        result, _, _ = await self.run_chat(request, [point("C1", "D1", 0.95)])
        self.assertFalse(result.needsClarification)
        self.assertTrue(result.grounded)

    def test_generic_question_detection_keeps_topic_words(self):
        self.assertTrue(is_generic_question("Cac buoc nghiep vu quan trong la gi"))
        self.assertTrue(is_generic_question("Những điều kiện nào cần lưu ý?"))
        self.assertFalse(is_generic_question("Các bước giải ngân là gì?"))
        self.assertFalse(is_generic_question("Điều kiện mở thẻ là gì?"))
        self.assertFalse(is_generic_question("Tín dụng là gì?"))
        self.assertTrue(is_generic_question("Nội dung chính là gì?"))
        self.assertFalse(is_generic_question("Nội dung tín dụng tiêu dùng là gì?"))

    def test_course_summary_detection_allows_words_between_and_any_accent_style(self):
        self.assertTrue(is_course_summary_question("nội dung chính của khoá học?"))
        self.assertTrue(is_course_summary_question("noi dung chinh cua khoa hoc"))
        self.assertTrue(is_course_summary_question("Tóm tắt giúp tôi"))
        self.assertFalse(is_course_summary_question("Nội dung tờ trình gồm gì?"))

    async def test_course_summary_with_accent_variant_asks_which_course(self):
        request = ChatRequest(question="nội dung chính của khoá học?", course_ids=["C1", "C2"],
                              course_names={"C1": "Cho vay", "C2": "Tiền gửi"})
        result, query, _ = await self.run_chat(request, [point("C1", "D1")])
        self.assertTrue(result.needsClarification)
        query.assert_not_awaited()

    async def test_model_detects_generic_question_the_word_list_misses(self):
        request = ChatRequest(question="Hướng dẫn tôi với", course_ids=["C1", "C2"],
                              course_names={"C1": "Cho vay", "C2": "Tiền gửi"})
        result, query, model = await self.run_chat(request, [point("C1", "D1")], topic='{"has_topic":false}')
        self.assertTrue(result.needsClarification)
        self.assertIn("Cho vay, Tiền gửi", result.answer)
        query.assert_not_awaited()
        self.assertEqual(1, model.await_count)

    async def test_topic_check_failure_still_answers_and_names_the_course(self):
        request = ChatRequest(question="Hướng dẫn tôi với", course_ids=["C1", "C2"],
                              course_names={"C1": "Cho vay", "C2": "Tiền gửi"})
        for topic in (TimeoutError(), httpx.ConnectError("down"), "not json", '{"other":1}'):
            with self.subTest(topic=topic):
                result, _, _ = await self.run_chat(request, [point("C1", "D1")], topic=topic)
                self.assertFalse(result.needsClarification)
                self.assertTrue(result.answer.startswith("Theo tài liệu khóa Cho vay:"))

    async def test_slow_topic_check_is_abandoned_at_its_deadline(self):
        async def never_answers(*args, **kwargs):
            await asyncio.sleep(60)

        with patch("app.services.chat.infrastructure.ollama_chat", never_answers), \
             patch("app.services.chat.config.OLLAMA_TOPIC_TIMEOUT_SECONDS", 0.01):
            self.assertTrue(await question_has_topic("Hướng dẫn tôi với", "Cho vay"))

    async def test_timed_out_topic_check_pauses_later_checks(self):
        calls = 0

        async def never_answers(*args, **kwargs):
            nonlocal calls
            calls += 1
            await asyncio.sleep(1)

        with patch("app.services.chat.infrastructure.ollama_chat", never_answers), \
             patch("app.services.chat.config.OLLAMA_TOPIC_TIMEOUT_SECONDS", 0.01), \
             patch("app.services.chat.config.OLLAMA_TOPIC_PAUSE_SECONDS", 60), \
             patch("app.services.chat.topic_check_paused_until", 0.0):
            self.assertTrue(await question_has_topic("Hướng dẫn tôi với", "Cho vay"))
            self.assertTrue(await question_has_topic("Hướng dẫn tôi với", "Cho vay"))
        self.assertEqual(calls, 1)

    async def test_answer_from_several_courses_names_each_once(self):
        request = ChatRequest(question="quy trình giải ngân khoản vay", course_ids=["C1", "C2"],
                              course_names={"C1": "Cho vay", "C2": "Tiền gửi"})
        result, _, _ = await self.run_chat(
            request, [point("C1", "D1", 0.95), point("C2", "D2", 0.93)],
            answer='{"answer":"Tạo hồ sơ.","source_ids":["SOURCE_1","SOURCE_2"]}',
        )
        self.assertTrue(result.answer.startswith("Theo tài liệu khóa Cho vay, Tiền gửi:"))

    async def test_chosen_course_skips_topic_check_and_attribution(self):
        cases = {
            "viewing": ChatRequest(question="quy trình giải ngân khoản vay", course_id="C1", course_name="Cho vay",
                                   course_ids=["C1", "C2"], course_names={"C2": "Tiền gửi"}),
            "named": ChatRequest(question="quy trình giải ngân của Cho vay", course_ids=["C1", "C2"],
                                 course_names={"C1": "Cho vay", "C2": "Tiền gửi"}),
            "single": ChatRequest(question="quy trình giải ngân khoản vay", course_ids=["C1"],
                                  course_names={"C1": "Cho vay"}),
        }
        for name, request in cases.items():
            with self.subTest(name):
                result, _, model = await self.run_chat(request, [point("C1", "D1")], topic='{"has_topic":false}')
                self.assertEqual("Tạo hồ sơ.", result.answer)
                self.assertEqual(1, model.await_count)

    def clarified_request(self, reply, asked="Hướng dẫn tôi với", clarification=None):
        names = {"C1": "Cho vay", "C2": "Tiền gửi cơ bản", "C3": "CIF cơ bản"}
        return ChatRequest(question=reply, course_ids=list(names), course_names=names, recent_messages=[
            {"role": "user", "content": "Câu cũ hơn"},
            {"role": "assistant", "content": "Trả lời cũ"},
            {"role": "user", "content": asked},
            {"role": "assistant", "content": clarification or config.GENERIC_QUESTION_CLARIFICATION.format(
                courses=", ".join(names.values()))},
        ])

    async def test_course_reply_answers_the_original_question_in_that_course(self):
        for reply in ("CIF cơ bản", "CIF", "cif co ban", "khóa CIF nhé"):
            with self.subTest(reply):
                result, query, model = await self.run_chat(self.clarified_request(reply), [point("C3", "D3")])
                match = self.course_condition(query)
                self.assertIsInstance(match, models.MatchValue)
                self.assertEqual("C3", match.value)
                self.assertEqual("Hướng dẫn tôi với", result.standaloneQuestion)
                self.assertIn("Hướng dẫn tôi với", model.await_args.args[1][1]["content"])
                self.assertEqual("Tạo hồ sơ.", result.answer)

    async def test_course_reply_to_summary_clarification_summarizes_that_course(self):
        request = self.clarified_request("CIF", asked="Tóm tắt khóa học",
                                         clarification=config.COURSE_SUMMARY_CLARIFICATION)
        result, query, _ = await self.run_chat(request, [point("C3", "D3")])
        self.assertEqual("C3", self.course_condition(query).value)
        self.assertFalse(result.needsClarification)

    async def test_new_question_after_clarification_is_not_a_course_reply(self):
        result, query, _ = await self.run_chat(
            self.clarified_request("quy trình giải ngân khoản vay"), [point("C1", "D1")])
        self.assertIsInstance(self.course_condition(query), models.MatchAny)
        self.assertEqual("quy trình giải ngân khoản vay", result.standaloneQuestion)

    def test_course_name_without_a_clarification_stays_as_asked(self):
        request = self.clarified_request("CIF", clarification="Một câu trả lời bình thường")
        self.assertIs(request, resume_clarified_question(request))

    def test_course_reply_matches_whole_words_of_accessible_courses(self):
        names = {"C1": "LN cơ bản", "C2": "CIF cơ bản"}
        self.assertEqual(["C2"], find_replied_courses("CIF", names))
        self.assertEqual(["C1", "C2"], find_replied_courses("cơ bản", names))
        self.assertEqual([], find_replied_courses("khóa học", names))
        self.assertEqual([], find_replied_courses("GL", names))
        self.assertEqual([], find_replied_courses("CI", names))

    async def test_ambiguous_course_reply_asks_again_then_keeps_the_original_question(self):
        request = self.clarified_request("cơ bản")
        result, query, model = await self.run_chat(request, [point("C3", "D3")])
        self.assertTrue(result.needsClarification)
        self.assertIn("Tiền gửi cơ bản, CIF cơ bản", result.answer)
        self.assertNotIn("Cho vay", result.answer)
        query.assert_not_awaited()
        model.assert_not_awaited()

        follow_up = ChatRequest(
            question="CIF", course_ids=request.course_ids, course_names=request.course_names,
            recent_messages=[*request.recent_messages, {"role": "user", "content": "cơ bản"},
                             {"role": "assistant", "content": result.answer}],
        )
        result, query, _ = await self.run_chat(follow_up, [point("C3", "D3")])
        self.assertEqual("C3", self.course_condition(query).value)
        self.assertEqual("Hướng dẫn tôi với", result.standaloneQuestion)

    async def test_navigation_sections_do_not_take_the_answer_slots(self):
        request = ChatRequest(question="quy trình giải ngân khoản vay", course_id="C1", course_name="Cho vay")
        points = [point("C1", "NAV1", 0.99, "Kết luận"), point("C1", "NAV2", 0.98, "Mục lục nội dung"),
                  point("C1", "D1", 0.90, "Chương 5 › Quy trình"), point("C1", "D2", 0.89)]
        result, _, model = await self.run_chat(request, points)
        prompt = model.await_args.args[1][1]["content"]
        self.assertNotIn("NAV1", prompt)
        self.assertNotIn("NAV2", prompt)
        self.assertEqual("D1", result.sources[0].documentId)

    async def test_only_navigation_sections_found_is_a_refusal(self):
        request = ChatRequest(question="quy trình giải ngân khoản vay", course_id="C1", course_name="Cho vay")
        result, _, model = await self.run_chat(request, [point("C1", "NAV1", 0.99, "Kết luận")])
        self.assertEqual(config.REFUSAL_ANSWER, result.answer)
        model.assert_not_awaited()

    async def test_course_summary_keeps_navigation_sections(self):
        request = ChatRequest(question="Tóm tắt khóa học", course_id="C1", course_name="Cho vay")
        result, _, _ = await self.run_chat(request, [point("C1", "NAV1", 0.99, "Kết luận")])
        self.assertEqual("NAV1", result.sources[0].documentId)

    def test_navigation_filter_matches_whole_headings_unless_asked_for(self):
        points = [point("C1", d, section=s) for d, s in [
            ("toc", "Mục lục nội dung"), ("toc2", "Muc luc"), ("end", "12. Kết luận"),
            ("sub", "Chương 5 › Kết luận"), ("body", "Kết luận thẩm định tín dụng"),
        ]]
        kept = lambda question: [p.payload["document_id"] for p in drop_navigation_results(points, question)]
        self.assertEqual(["sub", "body"], kept("Quy trình là gì?"))
        self.assertEqual(5, len(kept("Mục lục của tài liệu gồm gì?")))
        self.assertEqual(5, len(kept("kết luận của tài liệu là gì?")))

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
