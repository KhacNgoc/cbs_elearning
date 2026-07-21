import os
import re
import uuid
import asyncio
from io import BytesIO
from typing import Annotated

import httpx
from docx import Document as DocxDocument
from fastapi import FastAPI, File, Form, HTTPException, UploadFile
from pydantic import BaseModel, Field
from pypdf import PdfReader
from qdrant_client import AsyncQdrantClient, models

app = FastAPI(title="CBS eLearning RAG", version="0.1.0")
QDRANT_URL = os.getenv("QDRANT_URL", "http://localhost:6333")
OLLAMA_URL = os.getenv("OLLAMA_URL", "http://localhost:11434").rstrip("/")
CHAT_MODEL = os.getenv("OLLAMA_CHAT_MODEL", "qwen3:4b")
EMBEDDING_MODEL = os.getenv("OLLAMA_EMBEDDING_MODEL", "nomic-embed-text")
COLLECTION = "course_documents"
EMBEDDING_BATCH_SIZE = int(os.getenv("EMBEDDING_BATCH_SIZE", "32"))
EXCERPT_LENGTH = 500
qdrant = AsyncQdrantClient(url=QDRANT_URL)
collection_lock = asyncio.Lock()

class ChatRequest(BaseModel):
    question: str = Field(min_length=1, max_length=4000)
    course_id: str | None = None
    page: int | None = Field(default=None, ge=1)
    limit: int = Field(default=5, ge=1, le=10)

class Source(BaseModel):
    document_id: str
    file_name: str
    page: int
    excerpt: str
    score: float

class ChatResponse(BaseModel):
    answer: str
    sources: list[Source]

def split_text(text: str, chunk_size: int = 1100, overlap: int = 180) -> list[str]:
    cleaned = re.sub(r"\s+", " ", text).strip()
    if not cleaned:
        return []
    return [cleaned[start:start + chunk_size] for start in range(0, len(cleaned), chunk_size - overlap)]

def extract_pages(content: bytes, filename: str) -> list[tuple[int, str]]:
    suffix = filename.lower().rsplit(".", 1)[-1]
    if suffix == "pdf":
        reader = PdfReader(BytesIO(content))
        return [(index + 1, page.extract_text() or "") for index, page in enumerate(reader.pages)]
    if suffix == "docx":
        document = DocxDocument(BytesIO(content))
        text = "\n".join(paragraph.text for paragraph in document.paragraphs)
        return [(1, text)]
    raise HTTPException(415, "Only PDF and DOCX files are supported")

async def embeddings(texts: list[str]) -> list[list[float]]:
    if not texts:
        return []
    if EMBEDDING_BATCH_SIZE < 1:
        raise RuntimeError("EMBEDDING_BATCH_SIZE must be greater than zero")

    vectors: list[list[float]] = []
    async with httpx.AsyncClient(timeout=120) as client:
        for start in range(0, len(texts), EMBEDDING_BATCH_SIZE):
            batch = texts[start:start + EMBEDDING_BATCH_SIZE]
            response = await client.post(
                f"{OLLAMA_URL}/api/embed",
                json={"model": EMBEDDING_MODEL, "input": batch},
            )
            if response.status_code != 200:
                raise HTTPException(503, f"Embedding model is unavailable: {response.text}")
            batch_vectors = response.json()["embeddings"]
            if len(batch_vectors) != len(batch):
                raise HTTPException(502, "Embedding model returned an unexpected number of vectors")
            vectors.extend(batch_vectors)
    return vectors

async def ensure_collection() -> None:
    if await qdrant.collection_exists(COLLECTION):
        return
    async with collection_lock:
        if await qdrant.collection_exists(COLLECTION):
            return
        vector = (await embeddings(["dimension probe"]))[0]
        await qdrant.create_collection(
            COLLECTION,
            vectors_config=models.VectorParams(size=len(vector), distance=models.Distance.COSINE),
        )

@app.get("/health")
async def health():
    try:
        async with httpx.AsyncClient(timeout=3) as client:
            await client.get(f"{OLLAMA_URL}/api/tags")
        await qdrant.get_collections()
        return {"status": "ok", "chat_model": CHAT_MODEL, "embedding_model": EMBEDDING_MODEL}
    except Exception as error:
        raise HTTPException(503, f"Dependency unavailable: {error}")

@app.post("/ingest")
async def ingest(
    file: Annotated[UploadFile, File(...)],
    document_id: Annotated[str, Form(...)],
    course_id: Annotated[str, Form(...)],
):
    content = await file.read()
    pages = extract_pages(content, file.filename or "document")
    chunks = [(page, chunk) for page, text in pages for chunk in split_text(text)]
    if not chunks:
        raise HTTPException(400, "No extractable text was found in the document")
    await ensure_collection()
    await qdrant.delete(COLLECTION, models.FilterSelector(filter=models.Filter(must=[models.FieldCondition(key="document_id", match=models.MatchValue(value=document_id))])))
    for start in range(0, len(chunks), EMBEDDING_BATCH_SIZE):
        chunk_batch = chunks[start:start + EMBEDDING_BATCH_SIZE]
        vectors = await embeddings([full_text for _, full_text in chunk_batch])
        await qdrant.upsert(COLLECTION, models.PointsList(points=[models.PointStruct(
            id=str(uuid.uuid4()), vector=vector,
            payload={
                "document_id": document_id,
                "course_id": course_id,
                "file_name": file.filename,
                "page": page,
                "excerpt": full_text[:EXCERPT_LENGTH],
                "full_text": full_text,
            },
        ) for (page, full_text), vector in zip(chunk_batch, vectors)]))
    return {"document_id": document_id, "chunks_indexed": len(chunks), "pages": len(pages)}

@app.post("/chat", response_model=ChatResponse)
async def chat(request: ChatRequest):
    await ensure_collection()
    query = (await embeddings([request.question]))[0]
    must = []
    if request.course_id:
        must.append(models.FieldCondition(key="course_id", match=models.MatchValue(value=request.course_id)))
    if request.page:
        must.append(models.FieldCondition(key="page", match=models.MatchValue(value=request.page)))
    results = (await qdrant.query_points(
        collection_name=COLLECTION,
        query=query,
        query_filter=models.Filter(must=must) if must else None,
        limit=request.limit,
    )).points
    sources = [Source(document_id=item.payload["document_id"], file_name=item.payload["file_name"], page=item.payload["page"], excerpt=item.payload["excerpt"], score=item.score) for item in results]
    context = "\n\n".join(
        f"[{index + 1}] {item.payload['file_name']}, trang {item.payload['page']}: {item.payload['full_text']}"
        for index, item in enumerate(results)
    )
    prompt = "Trả lời bằng tiếng Việt, chỉ dựa trên ngữ cảnh. Nếu thiếu dữ liệu, nói rõ. Nêu số nguồn trong ngoặc vuông khi sử dụng.\n\nNgữ cảnh:\n" + context + "\n\nCâu hỏi: " + request.question
    async with httpx.AsyncClient(timeout=180) as http:
        response = await http.post(f"{OLLAMA_URL}/api/chat", json={"model": CHAT_MODEL, "stream": False, "messages": [{"role": "user", "content": prompt}]})
    if response.status_code != 200:
        raise HTTPException(503, f"Chat model is unavailable: {response.text}")
    return ChatResponse(answer=response.json()["message"]["content"], sources=sources)
