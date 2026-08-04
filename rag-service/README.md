# RAG service (week 1)

The FastAPI service accepts PDF/DOCX documents, splits extracted text by page, embeds chunks with Ollama, and stores them in Qdrant. Embeddings and Qdrant upserts run in configurable batches (`EMBEDDING_BATCH_SIZE=32` by default). It uses `AsyncQdrantClient`, so Qdrant network work does not block FastAPI's event loop.

Each Qdrant payload stores `excerpt` (the first 500 characters, returned to the frontend) separately from `full_text` (the entire chunk, used only to construct the LLM context).

## Run

1. Copy `.env.example` to `.env` and set a PostgreSQL password.
2. Run `docker compose up --build` from the repository root. The initial Ollama model download may take several minutes.
3. Check `http://localhost:8001/health`.

## Run application services locally (Qdrant in Docker)

Only Qdrant needs to run in Docker. Ollama, Python, Spring Boot, and Vue run
directly on the host machine.

1. Start Ollama and download the models once:

```bash
ollama serve
ollama pull nomic-embed-text
ollama pull qwen3:4b
```

2. Confirm that the Qdrant container exposes port `6333`, then create the Python
   environment and start the RAG service:

```bash
cd rag-service
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
QDRANT_URL=http://localhost:6333 \
OLLAMA_URL=http://localhost:11434 \
python -m uvicorn app.main:app --host 127.0.0.1 --port 8001 --reload
```

The first anti-hallucination gate uses a combined vector/lexical relevance
score. Its default threshold is `0.55`; override it with
`RAG_SCORE_THRESHOLD` only after measuring representative course questions.

3. Start Spring Boot with the H2 profile, avoiding a local PostgreSQL dependency:

```bash
cd CoreTrainingAPI
SPRING_PROFILES_ACTIVE=h2 APP_RAG_URL=http://localhost:8001 mvn spring-boot:run
```

4. Start the Vue frontend:

```bash
cd CoreTraining
npm run dev
```

The local endpoints are Vue `http://localhost:5173`, Spring Boot
`http://localhost:8080`, RAG `http://localhost:8001`, and Ollama
`http://localhost:11434`.

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
