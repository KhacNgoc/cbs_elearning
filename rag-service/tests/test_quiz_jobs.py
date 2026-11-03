import asyncio
import unittest
from types import SimpleNamespace
from unittest.mock import AsyncMock, patch

from fastapi import HTTPException

from app.api.routes import router
from app.schemas import QuizGenerationRequest, QuizGenerationResponse
from app.services import quiz_jobs
from app.services.quiz_jobs import QuizGenerationJob, QuizProgress, get_quiz_job, start_quiz_job


POINT = SimpleNamespace(score=1., payload={"document_id": "D1", "course_id": "C1",
    "page": 1, "section": "Tổng quan", "full_text": "CIF là mã định danh khách hàng. Mã hồ sơ nhận diện hồ sơ khoản vay cụ thể."})
BAD = '{"q":"Mã hồ sơ nhận diện hồ sơ khoản vay cụ thể là gì?","o":["Mã hồ sơ","CIF","Hạn mức","Dư nợ"],"a":0}'
GOOD = '{"q":"CIF dùng để nhận diện đối tượng nào?","o":["Khách hàng","Hồ sơ khoản vay","Lịch trả nợ","Lãi suất"],"a":0}'
REVIEW = '{"reason":"Nguồn xác định CIF là mã định danh khách hàng.","correct_answers":[0],"well_formed":true}'


def request(single=1):
    return QuizGenerationRequest(course_id="C1", course_name="Cho vay",
        single_choice_count=single, multiple_choice_count=0)


async def settle(job_id):
    for _ in range(200):
        job = get_quiz_job(job_id)
        if job.status in ("completed", "failed"):
            return job
        await asyncio.sleep(0.001)
    raise AssertionError("job did not settle")


class QuizJobTests(unittest.IsolatedAsyncioTestCase):
    def setUp(self):
        quiz_jobs._jobs.clear()
        quiz_jobs._finished_at.clear()
        quiz_jobs._tasks.clear()

    async def test_job_reports_each_stage_and_returns_final_result(self):
        snapshots = []

        async def model(_model, messages, *args, **kwargs):
            job = next(iter(quiz_jobs._jobs.values()))
            snapshots.append((job.stage, job.attempt, job.percent, job.accepted_questions))
            if messages[0]["content"].startswith("Bạn kiểm định"):
                return REVIEW
            return [BAD, GOOD][len(snapshots) - 1]

        with patch("app.infrastructure.ensure_collection", AsyncMock()), \
             patch("app.infrastructure.embeddings", AsyncMock(return_value=[[1., 0.]] * 4)), \
             patch("app.infrastructure.qdrant.query_points", AsyncMock(return_value=SimpleNamespace(points=[POINT]))), \
             patch("app.infrastructure.ollama_chat", side_effect=model), \
             patch("app.config.QUIZ_LLM_VERIFICATION_ENABLED", True):
            started = start_quiz_job(request())
            self.assertEqual("queued", started.status)
            self.assertEqual(0, started.percent)
            self.assertEqual(1, started.requested_questions)
            job = await settle(started.job_id)

        self.assertEqual("completed", job.status)
        self.assertEqual(100, job.percent)
        self.assertEqual(1, job.accepted_questions)
        self.assertEqual("CIF dùng để nhận diện đối tượng nào?", job.result.questions[0].text)
        # Two candidates in flight, then the review of the one that passed local checks;
        # all of it is one generating phase.
        self.assertEqual([("generating", 1)] * 3, [(stage, attempt) for stage, attempt, _, _ in snapshots])
        # The bar counts questions that passed every check, review included.
        self.assertEqual([0, 0, 0, 100], [percent for _, _, percent, _ in snapshots] + [job.percent])
        self.assertEqual((1, 1), (job.phase, job.phase_count))

    async def test_generation_error_marks_job_failed_with_status(self):
        with patch("app.services.quiz.generate_quiz",
                   AsyncMock(side_effect=HTTPException(503, "Quiz generation model is unavailable"))):
            job = await settle(start_quiz_job(request()).job_id)
        self.assertEqual("failed", job.status)
        self.assertEqual("failed", job.stage)
        self.assertEqual(503, job.error_status)
        self.assertEqual("Quiz generation model is unavailable", job.error)
        self.assertIsNone(job.result)

    async def test_unexpected_error_and_timeout_still_settle_the_job(self):
        with patch("app.services.quiz.generate_quiz", AsyncMock(side_effect=RuntimeError("boom"))):
            job = await settle(start_quiz_job(request()).job_id)
        self.assertEqual((500, "Quiz generation failed"), (job.error_status, job.error))

        async def slow(*args, **kwargs):
            await asyncio.sleep(10)
        with patch("app.services.quiz.generate_quiz", side_effect=slow), \
             patch.object(quiz_jobs, "QUIZ_JOB_TIMEOUT_SECONDS", 0.01):
            job = start_quiz_job(request())
            await asyncio.sleep(0.05)
            job = await settle(job.job_id)
        self.assertEqual(504, job.error_status)

    async def test_invalid_total_unknown_job_and_capacity_are_rejected(self):
        with self.assertRaises(HTTPException) as invalid:
            start_quiz_job(request(single=0))
        self.assertEqual(400, invalid.exception.status_code)
        with self.assertRaises(HTTPException) as missing:
            get_quiz_job("missing")
        self.assertEqual(404, missing.exception.status_code)

        blocker = asyncio.Event()

        async def blocked(*args, **kwargs):
            await blocker.wait()
            return QuizGenerationResponse(questions=[], sources=[])
        with patch("app.services.quiz.generate_quiz", side_effect=blocked), \
             patch.object(quiz_jobs, "QUIZ_JOB_MAX_ACTIVE", 1):
            first = start_quiz_job(request())
            with self.assertRaises(HTTPException) as busy:
                start_quiz_job(request())
            self.assertEqual(429, busy.exception.status_code)
            blocker.set()
            await settle(first.job_id)

    async def test_finished_jobs_expire_after_retention(self):
        with patch("app.services.quiz.generate_quiz",
                   AsyncMock(return_value=QuizGenerationResponse(questions=[], sources=[]))):
            job = await settle(start_quiz_job(request()).job_id)
        with patch.object(quiz_jobs, "QUIZ_JOB_RETENTION_SECONDS", -1):
            with self.assertRaises(HTTPException):
                get_quiz_job(job.job_id)

    def test_percent_matches_question_counts_in_each_phase(self):
        def tracker(verification):
            job = QuizGenerationJob(job_id="j", status="queued", stage="queued", message="",
                                    percent=0, requested_questions=5, max_attempts=2)
            progress = QuizProgress(job)
            progress.plan(5, verification)
            progress.retrieving()
            return job, progress

        job, progress = tracker(True)
        self.assertEqual(0, job.percent)
        progress.generation_started(1, 5)
        seen = []
        for count in range(1, 6):
            progress.accepted(count)
            seen.append((job.completed_steps, job.total_steps, job.percent))
        self.assertEqual([(1, 5, 10), (2, 5, 20), (3, 5, 30), (4, 5, 40), (5, 5, 50)], seen)
        progress.verification_started(5)
        for _ in range(5):
            progress.step_done()
        self.assertEqual((2, 5, 5, 99), (job.phase, job.completed_steps, job.total_steps, job.percent))

        job, progress = tracker(False)
        progress.generation_started(1, 5)
        progress.accepted(2)
        self.assertEqual(40, job.percent)
        progress.generation_started(2, 3)
        self.assertEqual((2, 5, 40), (job.completed_steps, job.total_steps, job.percent))
        progress.accepted(4)
        progress.finalizing()
        self.assertEqual(99, job.percent)

    def test_job_routes_are_registered_next_to_legacy_endpoint(self):
        paths = {(route.path, tuple(sorted(route.methods))) for route in router.routes}
        self.assertIn(("/quiz/generate", ("POST",)), paths)
        self.assertIn(("/quiz/jobs", ("POST",)), paths)
        self.assertIn(("/quiz/jobs/{job_id}", ("GET",)), paths)


if __name__ == "__main__":
    unittest.main()
