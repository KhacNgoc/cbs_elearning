"""Pydantic request, response, and structured-model schemas."""

from typing import Literal

from pydantic import BaseModel, ConfigDict, Field, StrictBool, StrictStr


class ConversationMessage(BaseModel):
    role: Literal["user", "assistant", "system"]
    content: str = Field(min_length=1, max_length=12000)


class ChatRequest(BaseModel):
    question: str = Field(min_length=1, max_length=4000)
    course_id: str | None = None
    course_name: str | None = Field(default=None, max_length=500)
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


class ChatResponse(BaseModel):
    answer: str
    grounded: bool
    sources: list[Source]
    standaloneQuestion: str
    needsClarification: bool = False
    rewriteReason: str = ""


class QuizGenerationRequest(BaseModel):
    course_id: str = Field(min_length=1, max_length=200)
    course_name: str = Field(min_length=1, max_length=500)
    lesson_name: str | None = Field(default=None, max_length=500)
    single_choice_count: int = Field(default=5, ge=0, le=10)
    multiple_choice_count: int = Field(default=0, ge=0, le=10)
    option_count: int = Field(default=4, ge=3, le=6)


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
