"""Background quiz generation jobs with pollable progress.

Jobs live in process memory: the RAG service runs as a single worker, and a job
only needs to outlive the request that started it, not a restart.
"""

import asyncio
import os
import time
import uuid
from typing import Literal

from fastapi import HTTPException
from pydantic import BaseModel, Field

from app import config, infrastructure
from app.schemas import QuizGenerationRequest, QuizGenerationResponse


QUIZ_JOB_RETENTION_SECONDS = float(os.getenv("QUIZ_JOB_RETENTION_SECONDS", "900"))
QUIZ_JOB_MAX_ACTIVE = max(1, int(os.getenv("QUIZ_JOB_MAX_ACTIVE", "20")))
QUIZ_JOB_TIMEOUT_SECONDS = float(os.getenv("QUIZ_JOB_TIMEOUT_SECONDS", "300"))

QuizJobStatus = Literal["queued", "running", "completed", "failed"]
QuizJobStage = Literal["queued", "retrieving", "generating", "verifying", "finalizing", "completed", "failed"]

STAGE_MESSAGES = {
    "queued": "Đang chờ xử lý",
    "retrieving": "Đang truy xuất nội dung từ tài liệu",
    "generating": "Đang sinh câu hỏi",
    "verifying": "Đang kiểm định câu hỏi",
    "finalizing": "Đang hoàn tất bản nháp",
    "completed": "Đã tạo xong",
    "failed": "Tạo câu hỏi thất bại",
}

class QuizGenerationJob(BaseModel):
    job_id: str
    status: QuizJobStatus
    stage: QuizJobStage
    message: str
    percent: int = Field(ge=0, le=100)
    requested_questions: int
    # Questions that passed the automatic checks so far.
    accepted_questions: int = 0
    attempt: int = 0
    max_attempts: int
    # 1 = generating, 2 = verifying; phase_count is 1 when verification is off.
    phase: int = 0
    phase_count: int = 1
    # Progress of the current phase, counted in questions.
    completed_steps: int = 0
    total_steps: int = 0
    error: str | None = None
    error_status: int | None = None
    result: QuizGenerationResponse | None = None


class QuizProgress:
    """Mutable progress tracker that ``generate_quiz`` reports into.

    The percent is counted in questions so it matches the text next to the bar:
    generation is "accepted / requested", verification is "checked / to check",
    and each phase gets an equal share of the bar. Retrieval shows 0%.
    """

    def __init__(self, job: QuizGenerationJob):
        self.job = job

    def _refresh_percent(self) -> None:
        job = self.job
        generation = min(1.0, job.accepted_questions / max(1, job.requested_questions))
        verification = 0.0
        if job.phase >= 2 or job.stage == "finalizing":
            generation = 1.0
        if job.stage == "verifying":
            verification = job.completed_steps / max(1, job.total_steps)
        elif job.stage == "finalizing":
            verification = 1.0
        share = 100 / job.phase_count
        percent = share * generation + (share * verification if job.phase_count > 1 else 0)
        # Only completion shows 100%; never let the bar move backwards.
        job.percent = max(job.percent, min(99, int(percent)))

    def _set_stage(self, stage: QuizJobStage) -> None:
        self.job.status = "running"
        self.job.stage = stage
        self.job.message = STAGE_MESSAGES[stage]

    def plan(self, requested: int, verification: bool) -> None:
        self.job.requested_questions = requested
        self.job.phase_count = 2 if verification else 1

    def retrieving(self) -> None:
        self._set_stage("retrieving")

    def generation_started(self, attempt: int, planned_steps: int) -> None:
        self._set_stage("generating")
        self.job.phase = 1
        self.job.attempt = attempt
        self.job.completed_steps = min(self.job.accepted_questions, self.job.requested_questions)
        self.job.total_steps = self.job.requested_questions
        self._refresh_percent()

    def accepted(self, count: int) -> None:
        self.job.accepted_questions = count
        if self.job.stage == "generating":
            self.job.completed_steps = min(count, self.job.requested_questions)
            self._refresh_percent()

    def verification_started(self, planned_steps: int) -> None:
        self._set_stage("verifying")
        self.job.phase = 2
        self.job.completed_steps = 0
        self.job.total_steps = planned_steps
        self._refresh_percent()

    def step_done(self) -> None:
        if self.job.stage == "verifying":
            self.job.completed_steps += 1
            self._refresh_percent()

    def finalizing(self) -> None:
        self._set_stage("finalizing")
        self._refresh_percent()


class NullQuizProgress:
    """Progress sink for the synchronous endpoint, which has nobody polling."""

    def __getattr__(self, _name):
        return lambda *args, **kwargs: None


NULL_PROGRESS = NullQuizProgress()

_jobs: dict[str, QuizGenerationJob] = {}
_finished_at: dict[str, float] = {}
_tasks: dict[str, asyncio.Task] = {}


def _prune_finished_jobs() -> None:
    cutoff = time.monotonic() - QUIZ_JOB_RETENTION_SECONDS
    for job_id in [job_id for job_id, finished in _finished_at.items() if finished < cutoff]:
        _jobs.pop(job_id, None)
        _finished_at.pop(job_id, None)


def _finish(job: QuizGenerationJob, **fields) -> None:
    for name, value in fields.items():
        setattr(job, name, value)
    job.message = STAGE_MESSAGES[job.stage]
    _finished_at[job.job_id] = time.monotonic()
    _tasks.pop(job.job_id, None)


async def _run_job(job: QuizGenerationJob, request: QuizGenerationRequest) -> None:
    # Imported lazily so tests can patch app.services.quiz.generate_quiz.
    from app.services import quiz

    progress = QuizProgress(job)
    try:
        result = await asyncio.wait_for(
            quiz.generate_quiz(request, progress=progress), QUIZ_JOB_TIMEOUT_SECONDS
        )
    except asyncio.TimeoutError:
        _finish(job, status="failed", stage="failed", error_status=504,
                error="Quá thời gian tạo câu hỏi")
    except HTTPException as error:
        _finish(job, status="failed", stage="failed", error_status=error.status_code,
                error=str(error.detail))
    except Exception as error:  # noqa: BLE001 - a background job must always settle.
        infrastructure.logger.exception(
            "Quiz job failed job_id=%s course_id=%s error_type=%s",
            job.job_id, request.course_id, type(error).__name__,
        )
        _finish(job, status="failed", stage="failed", error_status=500,
                error="Quiz generation failed")
    else:
        _finish(job, status="completed", stage="completed", percent=100,
                accepted_questions=len(result.questions), result=result)


def start_quiz_job(request: QuizGenerationRequest) -> QuizGenerationJob:
    requested_total = request.single_choice_count + request.multiple_choice_count
    if requested_total < 1 or requested_total > 20:
        raise HTTPException(400, "The total number of questions must be between 1 and 20")
    _prune_finished_jobs()
    if len(_tasks) >= QUIZ_JOB_MAX_ACTIVE:
        raise HTTPException(429, "Too many quiz generation jobs are running")
    job = QuizGenerationJob(
        job_id=uuid.uuid4().hex,
        status="queued",
        stage="queued",
        message=STAGE_MESSAGES["queued"],
        percent=0,
        requested_questions=requested_total,
        max_attempts=config.QUIZ_GENERATION_MAX_ATTEMPTS,
    )
    _jobs[job.job_id] = job
    _tasks[job.job_id] = asyncio.create_task(_run_job(job, request))
    return job


def get_quiz_job(job_id: str) -> QuizGenerationJob:
    _prune_finished_jobs()
    job = _jobs.get(job_id)
    if job is None:
        raise HTTPException(404, "Quiz generation job not found")
    return job
