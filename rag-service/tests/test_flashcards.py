import asyncio
import json
import unittest
from types import SimpleNamespace
from unittest.mock import AsyncMock, patch

from fastapi import HTTPException
from pydantic import ValidationError

from app.schemas import FlashcardGenerationRequest, GeneratedFlashcard, QuizGenerationRequest
from app.services import quiz_jobs
from app.services.quiz import generate_quiz
from app.services.flashcards import (
    flashcard_validation_error,
    generate_flashcards,
    normalize_flashcard,
)
from app.services.quiz_jobs import get_flashcard_job, get_quiz_job, start_flashcard_job


SOURCE_TEXT = ("CIF là mã định danh duy nhất của khách hàng trên hệ thống. "
               "Hồ sơ mở CIF phải được kiểm soát viên phê duyệt trong vòng 2 ngày làm việc.")
POINT = SimpleNamespace(score=1., payload={"document_id": "D1", "course_id": "C1", "file_name": "CIF.pdf",
                                           "page": 3, "section": "Mở CIF", "full_text": SOURCE_TEXT})
TERM = json.dumps({"f": "CIF", "b": "Mã định danh duy nhất của khách hàng trên hệ thống."}, ensure_ascii=False)
CLOZE = json.dumps({"f": "Hồ sơ mở CIF phải được kiểm soát viên phê duyệt trong vòng ___.",
                    "b": "2 ngày làm việc"}, ensure_ascii=False)
LEAKED = json.dumps({"f": "Hồ sơ được duyệt trong 2 ngày làm việc, tức ____.", "b": "2 ngày làm việc"},
                    ensure_ascii=False)
ACCEPT = '{"reason":"Đúng theo nguồn.","grounded":true,"well_formed":true}'
REJECT = '{"reason":"Không có trong nguồn.","grounded":false,"well_formed":true}'


def is_review(messages):
    return messages[0]["content"].startswith("Bạn kiểm định")


def request(count=2, types=None):
    return FlashcardGenerationRequest(
        course_id="C1", course_name="Khách hàng", lesson_id="L1", lesson_name="Mở CIF",
        sources=[{"document_id": "D1", "page_from": 1, "page_to": 5}], count=count,
        **({"types": types} if types else {}),
    )


def card(card_type, front, back):
    return normalize_flashcard(GeneratedFlashcard(type=card_type, front=front, back=back, source_ids=["SOURCE_1"]))


def retrieval_patches(model):
    return (
        patch("app.infrastructure.ensure_collection", AsyncMock()),
        patch("app.infrastructure.embeddings", AsyncMock(return_value=[[1., 0.]] * 4)),
        patch("app.infrastructure.qdrant.query_points", AsyncMock(return_value=SimpleNamespace(points=[POINT]))),
        patch("app.infrastructure.ollama_chat", side_effect=model),
        patch("app.config.QUIZ_GENERATION_MAX_ATTEMPTS", 2),
    )


class FlashcardValidationTests(unittest.TestCase):
    def test_cloze_blank_is_normalized_and_answer_must_come_from_source(self):
        good = card("CLOZE", "Hồ sơ mở CIF được duyệt trong vòng ___.", "2 ngày làm việc")
        self.assertIn("____", good.front)
        self.assertIsNone(flashcard_validation_error(good, SOURCE_TEXT))
        invented = card("CLOZE", "Hồ sơ mở CIF được duyệt trong vòng ___.", "5 ngày làm việc")
        self.assertEqual("cloze_answer_not_in_source", flashcard_validation_error(invented, SOURCE_TEXT))
        two_blanks = card("CLOZE", "___ được duyệt trong ___.", "CIF")
        self.assertEqual("cloze_blank_count", flashcard_validation_error(two_blanks, SOURCE_TEXT))

    def test_cloze_answer_must_not_be_revealed(self):
        leaked = card("CLOZE", "Duyệt trong 2 ngày làm việc, tức ___.", "2 ngày làm việc")
        self.assertEqual("cloze_answer_revealed", flashcard_validation_error(leaked, SOURCE_TEXT))

    def test_term_must_appear_in_source_and_back_must_explain_it(self):
        self.assertIsNone(flashcard_validation_error(
            card("TERM", "CIF", "Mã định danh duy nhất của khách hàng."), SOURCE_TEXT))
        self.assertEqual("term_not_in_source", flashcard_validation_error(
            card("TERM", "CASA", "Tài khoản tiền gửi không kỳ hạn của khách hàng."), SOURCE_TEXT))
        self.assertEqual("term_back_repeats_front", flashcard_validation_error(
            card("TERM", "CIF", "CIF"), SOURCE_TEXT))
        self.assertEqual("mentions_source", flashcard_validation_error(
            card("TERM", "CIF", "Theo tài liệu, đây là mã khách hàng."), SOURCE_TEXT))

    def test_request_requires_lesson_sources(self):
        with self.assertRaises(ValidationError):
            FlashcardGenerationRequest(course_id="C1", course_name="K", lesson_id="L1", lesson_name="B",
                                       sources=[], count=3)
        with self.assertRaises(ValidationError):
            FlashcardGenerationRequest(course_id="C1", course_name="K", lesson_id="L1", lesson_name="B",
                                       sources=[{"document_id": "D1"}], count=21)


class FlashcardGenerationTests(unittest.IsolatedAsyncioTestCase):
    def fake_model(self, generations, reviews, log=None):
        """Answers generation and review calls from separate queues, since they interleave."""
        generations, reviews = list(generations), list(reviews)

        async def model(_model, messages, *args, **kwargs):
            review = is_review(messages)
            if log is not None:
                log.append(("review" if review else "generate", messages[-1]["content"]))
            return (reviews if review else generations).pop(0)

        return model

    async def run_job(self, model, **kwargs):
        patches = retrieval_patches(model)
        with patches[0], patches[1], patches[2], patches[3], patches[4]:
            return await generate_flashcards(request(**kwargs))

    async def test_generates_both_types_replaces_rejects_and_verifies(self):
        calls = []
        model = self.fake_model([TERM, LEAKED, CLOZE], [ACCEPT, ACCEPT], calls)
        result = await self.run_job(model, count=2)

        self.assertEqual(["TERM", "CLOZE"], [item.type for item in result.cards])
        self.assertEqual("2 ngày làm việc", result.cards[1].back)
        self.assertEqual([], result.warnings)
        self.assertEqual("CIF.pdf", result.sources[0].documentName)
        # The replacement names the rejection reason; verification checks each kept card.
        generations = [prompt for role, prompt in calls if role == "generate"]
        self.assertIn("cloze_answer_revealed", generations[2])
        self.assertEqual(5, len(calls))

    async def test_card_rejected_in_review_is_replaced(self):
        other = json.dumps({"f": "Kiểm soát viên", "b": "Người phê duyệt hồ sơ mở CIF trong vòng 2 ngày."}, ensure_ascii=False)
        calls = []
        model = self.fake_model([TERM, other], [REJECT, ACCEPT], calls)
        result = await self.run_job(model, count=1, types=["TERM"])
        self.assertEqual(["Kiểm soát viên"], [item.front for item in result.cards])
        self.assertEqual([], result.warnings)
        # The replacement is told not to draft the rejected card again.
        self.assertIn("CIF", [prompt for role, prompt in calls if role == "generate"][1].split("đã có:")[1])

    async def test_candidate_budget_reports_the_shortfall(self):
        model = self.fake_model([TERM] * 3, [REJECT])
        with patch("app.config.QUIZ_CANDIDATE_BUDGET_FACTOR", 3):
            result = await self.run_job(model, count=1, types=["TERM"])
        self.assertEqual([], result.cards)
        self.assertIn("0/1", result.warnings[0])

    async def test_reviews_do_not_wait_behind_every_generation(self):
        calls = []
        fronts = ["Hồ sơ", "Khách hàng", "Kiểm soát viên", "Ngày làm việc"]
        cards = [json.dumps({"f": front, "b": f"Nội dung giải thích đủ dài về {front}."}, ensure_ascii=False)
                 for front in fronts]
        with patch("app.config.QUIZ_GENERATION_CONCURRENCY", 2):
            await self.run_job(self.fake_model(cards, [ACCEPT] * 4, calls), count=4, types=["TERM"])
        self.assertEqual("review", calls[2][0])

    async def test_time_budget_returns_the_cards_already_reviewed(self):
        reviewed = asyncio.Event()

        async def model(_model, messages, *args, **kwargs):
            if is_review(messages):
                reviewed.set()
                return ACCEPT
            if not model.first_done:
                model.first_done = True
                return TERM
            await asyncio.sleep(30)

        model.first_done = False
        with patch("app.config.QUIZ_GENERATION_TIME_BUDGET_SECONDS", 0.05), \
             patch("app.config.OLLAMA_QUIZ_VERIFICATION_TIMEOUT_SECONDS", 0.05):
            result = await self.run_job(model, count=2, types=["TERM"])
        self.assertTrue(reviewed.is_set())
        self.assertEqual(1, len(result.cards))
        self.assertIn("1/2", result.warnings[0])

    async def test_unverifiable_cards_fail_the_job_only_when_all_fail(self):
        async def model(_model, messages, *args, **kwargs):
            if is_review(messages):
                raise RuntimeError("model down")
            return TERM

        with self.assertRaises(HTTPException) as failed:
            await self.run_job(model, count=1, types=["TERM"])
        self.assertEqual(503, failed.exception.status_code)

    async def test_flashcard_and_quiz_jobs_take_turns_on_the_model_queue(self):
        order = []
        quiz_output = '{"q":"Câu hỏi?","o":["A","B","C","D"],"a":0}'

        async def model(_model, messages, *args, **kwargs):
            flashcard = "flashcard" in messages[0]["content"]
            order.append("F" if flashcard else "Q")
            await asyncio.sleep(0)
            if is_review(messages):
                return REJECT if flashcard else '{"reason":"Sai đáp án.","correct_answers":[1],"well_formed":true}'
            return TERM if flashcard else quiz_output

        patches = retrieval_patches(model)
        quiz_request = QuizGenerationRequest(course_id="C1", course_name="Khách hàng",
            single_choice_count=10, multiple_choice_count=0)
        with patches[0], patches[1], patches[2], patches[3], patches[4], \
             patch("app.config.QUIZ_GENERATION_CONCURRENCY", 2), \
             patch("app.infrastructure.quiz_generation_semaphore", asyncio.Semaphore(1)):
            await asyncio.gather(generate_flashcards(request(count=10)), generate_quiz(quiz_request))
        self.assertIn("F", order[:4])
        self.assertIn("Q", order[:4])

    async def test_no_sources_never_calls_the_model(self):
        generate = AsyncMock()
        with patch("app.infrastructure.ensure_collection", AsyncMock()), \
             patch("app.infrastructure.embeddings", AsyncMock(return_value=[[1., 0.]] * 4)), \
             patch("app.infrastructure.qdrant.query_points", AsyncMock(return_value=SimpleNamespace(points=[]))), \
             patch("app.infrastructure.ollama_chat", generate):
            result = await generate_flashcards(request())
        self.assertEqual([], result.cards)
        self.assertTrue(result.warnings)
        generate.assert_not_called()


class FlashcardJobTests(unittest.IsolatedAsyncioTestCase):
    def setUp(self):
        quiz_jobs._jobs.clear()
        quiz_jobs._finished_at.clear()
        quiz_jobs._tasks.clear()

    async def test_flashcard_job_completes_and_is_not_readable_as_quiz_job(self):
        from app.schemas import FlashcardGenerationResponse
        result = FlashcardGenerationResponse(cards=[card("TERM", "CIF", "Mã định danh khách hàng.")], sources=[])
        with patch("app.services.flashcards.generate_flashcards", AsyncMock(return_value=result)):
            started = start_flashcard_job(request(count=1))
            self.assertEqual(("flashcard", 1), (started.kind, started.requested_questions))
            for _ in range(200):
                job = get_flashcard_job(started.job_id)
                if job.status == "completed":
                    break
                await asyncio.sleep(0.001)
        self.assertEqual((100, 1), (job.percent, job.accepted_questions))
        self.assertEqual("Đã tạo xong", job.message)
        with self.assertRaises(HTTPException) as wrong_kind:
            get_quiz_job(started.job_id)
        self.assertEqual(404, wrong_kind.exception.status_code)

    async def test_flashcard_and_quiz_jobs_share_the_capacity_limit(self):
        blocker = asyncio.Event()

        async def blocked(*args, **kwargs):
            await blocker.wait()
            raise HTTPException(503, "stopped")

        with patch("app.services.flashcards.generate_flashcards", side_effect=blocked), \
             patch.object(quiz_jobs, "QUIZ_JOB_MAX_ACTIVE", 1):
            first = start_flashcard_job(request())
            with self.assertRaises(HTTPException) as busy:
                start_flashcard_job(request())
            self.assertEqual(429, busy.exception.status_code)
            blocker.set()
            for _ in range(200):
                if get_flashcard_job(first.job_id).status == "failed":
                    break
                await asyncio.sleep(0.001)
        self.assertEqual("Tạo flashcard thất bại", get_flashcard_job(first.job_id).message)


if __name__ == "__main__":
    unittest.main()
