"""CBS eLearning RAG application entry point.

Business logic lives in ``services``; this module only wires the FastAPI app.
Selected imports are re-exported to avoid breaking existing Python consumers.
"""

from fastapi import FastAPI

from app.api.routes import router
from app.config import OLLAMA_QUERY_EMBEDDING_TIMEOUT_SECONDS, REFUSAL_ANSWER
from app.infrastructure import close_clients
from app.quiz_utils import generated_question_validation_error
from app.schemas import (
    ChatRequest,
    CompactSingleChoiceQuestion,
    ConversationMessage,
    GeneratedQuizQuestion,
    QuizGenerationRequest,
    SummaryModelResult,
    SummaryRequest,
)
from app.services.chat import answer_chat as chat
from app.services.quiz import generate_quiz
from app.services.summary import summarize_conversation as summarize
from app.utils import (
    ollama_compatible_schema,
    parse_rewrite_result,
    sanitize_conversation_summary,
)


app = FastAPI(title="CBS eLearning RAG", version="0.1.0")
app.include_router(router)


@app.on_event("shutdown")
async def close_http_clients() -> None:
    await close_clients()
