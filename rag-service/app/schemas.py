"""Pydantic request, response, and structured-model schemas."""

from typing import Literal

from pydantic import BaseModel, ConfigDict, Field, StrictBool, StrictStr, model_validator


class ConversationMessage(BaseModel):
    role: Literal["user", "assistant", "system"]
    content: str = Field(min_length=1, max_length=12000)


class ChatRequest(BaseModel):
    question: str = Field(min_length=1, max_length=4000)
    # Optional context course (e.g. the lesson the learner is viewing). It is a
    # ranking hint only when course_ids is provided.
    course_id: str | None = None
    course_name: str | None = Field(default=None, max_length=500)
    # Courses the learner may search. None keeps the legacy single-course
    # behaviour; an empty list means the learner has no searchable course.
    course_ids: list[str] | None = Field(default=None, max_length=2000)
    course_names: dict[str, str] = Field(default_factory=dict)
    page: int | None = Field(default=None, ge=1)
    conversation_summary: str = Field(default="", max_length=20000)
    recent_messages: list[ConversationMessage] = Field(default_factory=list, max_length=30)


class RewriteResult(BaseModel):
    model_config = ConfigDict(extra="forbid")

    standalone_question: StrictStr = Field(max_length=4000)
    needs_clarification: StrictBool
    reason: StrictStr = Field(max_length=1000)


class SummaryRequest(BaseModel):
    current_summary: str = Field(default="", max_length=20000)
    new_messages: list[ConversationMessage] = Field(min_length=1, max_length=100)


class SummaryResponse(BaseModel):
    summary: str


class SummaryModelResult(BaseModel):
    model_config = ConfigDict(extra="forbid")

    summary: StrictStr = Field(min_length=1, max_length=4000)


class AnswerModelResult(BaseModel):
    model_config = ConfigDict(extra="forbid")

    answer: StrictStr = Field(min_length=1, max_length=12000)
    source_ids: list[StrictStr]


class Source(BaseModel):
    documentId: str
    documentName: str
    page: int
    section: str
    courseId: str = ""
    courseName: str = ""


class ChatResponse(BaseModel):
    answer: str
    grounded: bool
    sources: list[Source]
    standaloneQuestion: str
    needsClarification: bool = False
    rewriteReason: str = ""


class QuizKnowledgeSource(BaseModel):
    model_config = ConfigDict(extra="forbid")
    document_id: str = Field(min_length=1, max_length=200)
    page_from: int | None = Field(default=None, ge=1)
    page_to: int | None = Field(default=None, ge=1)
    section: str | None = Field(default=None, min_length=1, max_length=2000)

    @model_validator(mode="after")
    def validate_range(self):
        if (self.page_from is None) != (self.page_to is None):
            raise ValueError("Both page bounds are required")
        if self.page_from is not None:
            if self.page_from > self.page_to or self.section is not None:
                raise ValueError("Invalid or mixed source range")
        return self


class QuizGenerationRequest(BaseModel):
    course_id: str = Field(min_length=1, max_length=200)
    course_name: str = Field(min_length=1, max_length=500)
    lesson_name: str | None = Field(default=None, max_length=500)
    lesson_id: str | None = Field(default=None, min_length=1, max_length=200)
    sources: list[QuizKnowledgeSource] = Field(default_factory=list, max_length=50)
    single_choice_count: int = Field(default=5, ge=0, le=10)
    multiple_choice_count: int = Field(default=0, ge=0, le=10)
    option_count: int = Field(default=4, ge=3, le=6)

    @model_validator(mode="after")
    def require_lesson_sources(self):
        if (self.lesson_id or self.lesson_name) and not self.sources:
            raise ValueError("Lesson quizzes require explicitly assigned document sources")
        return self


class GeneratedQuizQuestion(BaseModel):
    model_config = ConfigDict(extra="forbid")

    type: Literal["SINGLE_CHOICE", "MULTIPLE_CHOICE"]
    text: StrictStr = Field(min_length=1, max_length=2000)
    options: list[StrictStr]
    correct_answers: list[int]
    explanation: StrictStr = Field(default="", max_length=4000)
    source_ids: list[StrictStr]


class QuizModelResult(BaseModel):
    model_config = ConfigDict(extra="forbid")

    questions: list[GeneratedQuizQuestion]


class CompactSingleChoiceQuestion(BaseModel):
    model_config = ConfigDict(extra="forbid")

    q: StrictStr = Field(min_length=1, max_length=2000)
    o: list[StrictStr]
    a: int


class CompactMultipleChoiceQuestion(BaseModel):
    model_config = ConfigDict(extra="forbid")

    q: StrictStr = Field(min_length=1, max_length=2000)
    o: list[StrictStr]
    a: list[int]


class QuizVerificationResult(BaseModel):
    model_config = ConfigDict(extra="forbid")

    accepted_question_indexes: list[int]


class QuizQuestionReview(BaseModel):
    model_config = ConfigDict(extra="forbid")

    reason: StrictStr = Field(min_length=1, max_length=600)
    correct_answers: list[int]
    well_formed: StrictBool


class QuizGenerationSource(BaseModel):
    id: str
    documentId: str
    documentName: str
    page: int
    section: str


class QuizGenerationResponse(BaseModel):
    questions: list[GeneratedQuizQuestion]
    sources: list[QuizGenerationSource]
    warnings: list[str] = Field(default_factory=list)


class LessonDiffSummaryRequest(BaseModel):
    lesson_title: str = Field(default="", max_length=500)
    old_content: str = Field(default="", max_length=50000)
    new_content: str = Field(default="", max_length=50000)


class LessonDiffSummaryResponse(BaseModel):
    summary: str
