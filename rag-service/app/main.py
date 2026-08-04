import os
import re
import uuid
import asyncio
import json
from io import BytesIO
from typing import Annotated

import httpx
from docx import Document as DocxDocument
from docx.document import Document as DocxDocumentType
from docx.oxml.table import CT_Tbl
from docx.oxml.text.paragraph import CT_P
from docx.table import Table
from docx.text.paragraph import Paragraph
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
RETRIEVAL_SCORE_THRESHOLD = float(os.getenv("RAG_SCORE_THRESHOLD", "0.55"))
EXCERPT_LENGTH = 500
RETRIEVAL_LIMIT = 5
RETRIEVAL_CANDIDATE_LIMIT = 25
REFUSAL_ANSWER = "Tôi chưa tìm thấy thông tin này trong tài liệu của khóa học."
QUERY_STOP_WORDS = {
    "ai", "bao", "các", "cho", "có", "công", "của", "được", "gì", "hay",
    "khi", "là", "một", "nào", "những", "ra", "sao", "thế", "theo", "thì",
    "tính", "tôi", "trong", "và", "về", "với",
}
qdrant = AsyncQdrantClient(url=QDRANT_URL)
collection_lock = asyncio.Lock()

class ChatRequest(BaseModel):
    question: str = Field(min_length=1, max_length=4000)
    course_id: str | None = None
    page: int | None = Field(default=None, ge=1)

class Source(BaseModel):
    documentId: str
    documentName: str
    page: int
    section: str

class ChatResponse(BaseModel):
    answer: str
    grounded: bool
    sources: list[Source]

def split_text(text: str, chunk_size: int = 1100, overlap: int = 180) -> list[str]:
    cleaned = re.sub(r"\s+", " ", text).strip()
    if not cleaned:
        return []
    return [cleaned[start:start + chunk_size] for start in range(0, len(cleaned), chunk_size - overlap)]

def relevance_score(question: str, full_text: str, vector_score: float) -> float:
    query_terms = {
        term for term in re.findall(r"\w+", question.lower(), flags=re.UNICODE)
        if len(term) > 2 and term not in QUERY_STOP_WORDS
    }
    if not query_terms:
        return vector_score
    document_terms = set(re.findall(r"\w+", full_text.lower(), flags=re.UNICODE))
    lexical_coverage = len(query_terms & document_terms) / len(query_terms)
    return vector_score * (0.65 + 0.35 * lexical_coverage)

def iter_docx_blocks(document: DocxDocumentType):
    for child in document.element.body.iterchildren():
        if isinstance(child, CT_P):
            yield Paragraph(child, document)
        elif isinstance(child, CT_Tbl):
            yield Table(child, document)

def extract_pages(content: bytes, filename: str) -> list[tuple[int, str, str]]:
    suffix = filename.lower().rsplit(".", 1)[-1]
    if suffix == "pdf":
        reader = PdfReader(BytesIO(content))
        return [(index + 1, f"Trang {index + 1}", page.extract_text() or "") for index, page in enumerate(reader.pages)]
    if suffix == "docx":
        document = DocxDocument(BytesIO(content))
        sections: list[tuple[int, str, str]] = []
        heading = "Nội dung tài liệu"
        paragraphs: list[str] = []
        for block in iter_docx_blocks(document):
            if isinstance(block, Paragraph):
                text = block.text.strip()
                if not text:
                    continue
                if block.style and block.style.name.lower().startswith("heading"):
                    if paragraphs:
                        sections.append((1, heading, "\n".join(paragraphs)))
                    heading = text
                    paragraphs = []
                else:
                    paragraphs.append(text)
            else:
                for row in block.rows:
                    cells = [cell.text.strip() for cell in row.cells if cell.text.strip()]
                    if cells:
                        paragraphs.append(" | ".join(cells))
        if paragraphs:
            sections.append((1, heading, "\n".join(paragraphs)))
        return sections
    raise HTTPException(415, "Only PDF and DOCX files are supported")

async def embeddings(texts: list[str], prefix: str = "") -> list[list[float]]:
    if not texts:
        return []
    if EMBEDDING_BATCH_SIZE < 1:
        raise RuntimeError("EMBEDDING_BATCH_SIZE must be greater than zero")

    vectors: list[list[float]] = []
    async with httpx.AsyncClient(timeout=120) as client:
        for start in range(0, len(texts), EMBEDDING_BATCH_SIZE):
            batch = [prefix + text for text in texts[start:start + EMBEDDING_BATCH_SIZE]]
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
        vector = (await embeddings(["dimension probe"], "search_document: "))[0]
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
    chunks = [(page, section, chunk) for page, section, text in pages for chunk in split_text(text)]
    if not chunks:
        raise HTTPException(400, "No extractable text was found in the document")
    await ensure_collection()
    await qdrant.delete(COLLECTION, models.FilterSelector(filter=models.Filter(must=[models.FieldCondition(key="document_id", match=models.MatchValue(value=document_id))])))
    for start in range(0, len(chunks), EMBEDDING_BATCH_SIZE):
        chunk_batch = chunks[start:start + EMBEDDING_BATCH_SIZE]
        vectors = await embeddings(
            [f"{section}\n{full_text}" for _, section, full_text in chunk_batch],
            "search_document: ",
        )
        await qdrant.upsert(COLLECTION, models.PointsList(points=[models.PointStruct(
            id=str(uuid.uuid4()), vector=vector,
            payload={
                "document_id": document_id,
                "course_id": course_id,
                "file_name": file.filename,
                "page": page,
                "section": section,
                "excerpt": full_text[:EXCERPT_LENGTH],
                "full_text": full_text,
            },
        ) for (page, section, full_text), vector in zip(chunk_batch, vectors)]))
    return {"document_id": document_id, "chunks_indexed": len(chunks), "pages": len(pages)}

@app.post("/chat", response_model=ChatResponse)
async def chat(request: ChatRequest):
    await ensure_collection()
    query = (await embeddings([request.question], "search_query: "))[0]
    must = []
    if request.course_id:
        must.append(models.FieldCondition(key="course_id", match=models.MatchValue(value=request.course_id)))
    if request.page:
        must.append(models.FieldCondition(key="page", match=models.MatchValue(value=request.page)))
    results = (await qdrant.query_points(
        collection_name=COLLECTION,
        query=query,
        query_filter=models.Filter(must=must) if must else None,
        limit=RETRIEVAL_CANDIDATE_LIMIT,
    )).points
    ranked_results = sorted(
        results,
        key=lambda item: relevance_score(
            request.question,
            f"{(item.payload or {}).get('section', '')} {(item.payload or {}).get('full_text', '')}",
            item.score,
        ),
        reverse=True,
    )
    relevant_results = [
        item for item in ranked_results
        if relevance_score(
            request.question,
            f"{(item.payload or {}).get('section', '')} {(item.payload or {}).get('full_text', '')}",
            item.score,
        ) >= RETRIEVAL_SCORE_THRESHOLD
    ][:RETRIEVAL_LIMIT]
    if not relevant_results:
        return ChatResponse(answer=REFUSAL_ANSWER, grounded=False, sources=[])

    retrieved_sources = []
    context_parts = []
    for index, item in enumerate(relevant_results):
        source_id = f"SOURCE_{index + 1}"
        payload = item.payload or {}
        retrieved_sources.append({
            "source_id": source_id,
            "documentId": str(payload.get("document_id", "")),
            "documentName": str(payload.get("file_name", "Tài liệu")),
            "page": int(payload.get("page", 1)),
            "section": str(payload.get("section") or f"Trang {payload.get('page', 1)}"),
        })
        context_parts.append(
            f"<source id=\"{source_id}\" document=\"{payload.get('file_name', 'Tài liệu')}\" "
            f"page=\"{payload.get('page', 1)}\" section=\"{payload.get('section', '')}\">\n"
            f"{payload.get('full_text', '')}\n</source>"
        )

    system_prompt = f"""Bạn là trợ lý học tập nội bộ.

Chỉ được trả lời dựa trên CONTEXT được cung cấp.
Không được dùng kiến thức bên ngoài, suy đoán hoặc bịa nguồn.

Nếu CONTEXT không đủ để trả lời chính xác, chỉ trả lời đúng một câu:
\"{REFUSAL_ANSWER}\"

Trả lời bằng tiếng Việt, ngắn gọn, rõ ràng.
Phải trả về JSON hợp lệ gồm hai trường: answer và source_ids.
source_ids chỉ được chứa các id có trong CONTEXT và phải liệt kê các nguồn thực sự đã sử dụng.
Ví dụ: {{\"answer\": \"...\", \"source_ids\": [\"SOURCE_1\"]}}."""
    user_prompt = "CONTEXT:\n" + "\n\n".join(context_parts) + "\n\nCÂU HỎI:\n" + request.question
    async with httpx.AsyncClient(timeout=180) as http:
        response = await http.post(f"{OLLAMA_URL}/api/chat", json={
            "model": CHAT_MODEL,
            "stream": False,
            "think": False,
            "format": "json",
            "messages": [
                {"role": "system", "content": system_prompt},
                {"role": "user", "content": user_prompt},
            ],
        })
    if response.status_code != 200:
        raise HTTPException(503, f"Chat model is unavailable: {response.text}")
    try:
        model_result = json.loads(response.json()["message"]["content"])
        answer = str(model_result.get("answer", "")).strip()
        requested_source_ids = model_result.get("source_ids", [])
        if not isinstance(requested_source_ids, list):
            return ChatResponse(answer=REFUSAL_ANSWER, grounded=False, sources=[])
        valid_sources = [source for source in retrieved_sources if source["source_id"] in requested_source_ids]
    except (KeyError, TypeError, ValueError, json.JSONDecodeError):
        return ChatResponse(answer=REFUSAL_ANSWER, grounded=False, sources=[])

    if answer == REFUSAL_ANSWER or not answer or not valid_sources:
        return ChatResponse(answer=REFUSAL_ANSWER, grounded=False, sources=[])

    return ChatResponse(
        answer=answer,
        grounded=True,
        sources=[Source(
            documentId=source["documentId"],
            documentName=source["documentName"],
            page=source["page"],
            section=source["section"],
        ) for source in valid_sources],
    )
