# RAG service (week 1)

The FastAPI service accepts PDF/DOCX documents, splits extracted text by page, embeds chunks with Ollama, and stores them in Qdrant. Embeddings and Qdrant upserts run in configurable batches (`EMBEDDING_BATCH_SIZE=32` by default). It uses `AsyncQdrantClient`, so Qdrant network work does not block FastAPI's event loop.

Each Qdrant payload stores `excerpt` (the first 500 characters, returned to the frontend) separately from `full_text` (the entire chunk, used only to construct the LLM context).

## Run

1. Copy `.env.example` to `.env` and set a PostgreSQL password.
2. Run `docker compose up --build` from the repository root. The initial Ollama model download may take several minutes.
3. Check `http://localhost:8001/health`.

The chat model defaults to `qwen3:4b`; change `OLLAMA_CHAT_MODEL=qwen3:8b` in `.env` for a more capable machine. `nomic-embed-text` remains the embedding model because Qwen3 is used for generation, not vector search.

## Internal API

- `GET /health`
- `POST /ingest` multipart: `file`, `document_id`, `course_id`
- `POST /chat`: `{ "question": "Nội dung ở trang 3 là gì?", "course_id": "CIF", "page": 3 }`

Spring Boot provides the authenticated application-facing endpoints:

- `POST /api/admin/topics/{topicId}/documents` (admin, multipart `file`)
- `GET /api/admin/topics/{topicId}/documents` (admin)
- `POST /api/rag/chat` (authenticated)

The Vue API helpers are in `CoreTraining/src/api/adminApi.ts` (upload/list) and `CoreTraining/src/api/ragApi.ts` (chat). Upload uses `FormData`; never set the global JSON header for this request.
