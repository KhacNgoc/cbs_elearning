"""FastAPI routes kept thin by delegating work to domain services."""

from typing import Annotated

import httpx
from fastapi import APIRouter, File, Form, HTTPException, UploadFile, Response

from app import config, infrastructure
from app.schemas import (
    ChatRequest,
    ChatResponse,
    QuizGenerationRequest,
    QuizGenerationResponse,
    SummaryRequest,
    SummaryResponse,
)
from app.services.chat import answer_chat
from app.services.documents import ingest_document, document_outline, delete_document
from app.services.quiz import generate_quiz as generate_quiz_service
from app.services.summary import summarize_conversation


router = APIRouter()


@router.get("/health")
async def health():
    try:
        async with httpx.AsyncClient(timeout=3) as client:
            await client.get(f"{config.OLLAMA_URL}/api/tags")
        await infrastructure.qdrant.get_collections()
        return {
            "status": "ok",
            "chat_model": config.CHAT_MODEL,
            "embedding_model": config.EMBEDDING_MODEL,
        }
    except Exception as error:
        raise HTTPException(503, f"Dependency unavailable: {error}") from error


@router.post("/ingest")
async def ingest(
    file: Annotated[UploadFile, File(...)],
    document_id: Annotated[str, Form(...)],
    course_id: Annotated[str, Form(...)],
):
    return await ingest_document(file, document_id, course_id)


@router.post("/chat", response_model=ChatResponse)
async def chat(request: ChatRequest):
    return await answer_chat(request)


@router.get("/documents/{document_id}/outline")
async def outline(document_id: str, course_id: str):
    return await document_outline(document_id, course_id)


@router.delete("/documents/{document_id}", status_code=204)
async def delete(document_id: str, course_id: str):
    await delete_document(document_id, course_id)
    return Response(status_code=204)


@router.post("/quiz/generate", response_model=QuizGenerationResponse)
async def generate_quiz(request: QuizGenerationRequest):
    return await generate_quiz_service(request)


@router.post("/summarize", response_model=SummaryResponse)
async def summarize(request: SummaryRequest):
    return await summarize_conversation(request)


from app.schemas import LessonDiffSummaryRequest, LessonDiffSummaryResponse
from app.services.summary import summarize_lesson_diff

@router.post("/summarize-lesson-diff", response_model=LessonDiffSummaryResponse)
async def summarize_diff(request: LessonDiffSummaryRequest):
    return await summarize_lesson_diff(request)
