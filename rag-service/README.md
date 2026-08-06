# RAG service (week 1)

The FastAPI service accepts PDF/DOCX documents, splits extracted text by page, embeds chunks with Ollama, and stores them in Qdrant. Embeddings and Qdrant upserts run in configurable batches (`EMBEDDING_BATCH_SIZE=32` by default). It uses `AsyncQdrantClient`, so Qdrant network work does not block FastAPI's event loop.

Each Qdrant payload stores `excerpt` (the first 500 characters, returned to the frontend) separately from `full_text` (the entire chunk, used only to construct the LLM context).

## Run

1. Copy `.env.example` to `.env` and set a PostgreSQL password.
2. Run `docker compose up --build` from the repository root. The initial Ollama model download may take several minutes.
3. Check `http://localhost:8001/health`.

On Apple Silicon, Ollama inside Docker is CPU-only. For low-latency quiz
generation, run Ollama natively with Metal and apply the macOS Compose override:

```bash
OLLAMA_HOST=127.0.0.1:11435 OLLAMA_NUM_PARALLEL=5 ollama serve
OLLAMA_HOST=http://127.0.0.1:11435 ollama pull qwen3:4b
OLLAMA_HOST=http://127.0.0.1:11435 ollama pull nomic-embed-text
docker compose --env-file .env.production \
  -f docker-compose.yml -f docker-compose.macos.yml up -d --build
```

The native Ollama process must remain running. The RAG service keeps the quiz
model loaded for 30 minutes, avoiding cold-start cost on normal requests.

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

Context-aware chat uses `OLLAMA_REWRITE_MODEL` and `OLLAMA_SUMMARY_MODEL`
(both default to `OLLAMA_CHAT_MODEL`). Retrieval can be tuned with `RAG_TOP_K`,
`RAG_CANDIDATE_LIMIT`, and `RAG_CONTEXT_MAX_CHARS`. Ollama timeouts are
configured independently through `OLLAMA_CHAT_TIMEOUT_SECONDS`,
`OLLAMA_REWRITE_TIMEOUT_SECONDS`, and `OLLAMA_SUMMARY_TIMEOUT_SECONDS`.
Quiz generation defaults to `OLLAMA_QUIZ_MODEL=qwen3:4b`. It runs one compact,
single-source generation per requested question. Runtime capacity is configurable:
use `QUIZ_GENERATION_CONCURRENCY=1` on constrained CPU machines, and increase it
to match the inference server's parallel slots on GPU servers. `QUIZ_NUM_CTX`,
`QUIZ_SOURCE_MAX_CHARS`, `QUIZ_SINGLE_NUM_PREDICT`, and
`QUIZ_MULTIPLE_NUM_PREDICT` bound memory and token costs without changing code.
`QUIZ_GENERATION_MAX_ATTEMPTS` controls targeted retries for questions rejected
by structural or grounding checks; the default `2` performs one replacement pass.
The per-question timeout is controlled by
`OLLAMA_QUIZ_GENERATION_TIMEOUT_SECONDS` (120s). A second LLM verification pass
is disabled by default; enable `QUIZ_LLM_VERIFICATION_ENABLED=true` when the
deployment has spare inference capacity. Its timeout is controlled by
`OLLAMA_QUIZ_VERIFICATION_TIMEOUT_SECONDS` (60s).

For production, `OLLAMA_URL` may point to any reachable Ollama server. Select
`OLLAMA_QUIZ_MODEL` and `QUIZ_GENERATION_CONCURRENCY` from benchmark results on
that server; no application-code change is required when moving between CPU,
Apple Metal, NVIDIA GPU, or a separate inference host.
Query embedding has its own shorter budget through
`OLLAMA_QUERY_EMBEDDING_TIMEOUT_SECONDS` (30 seconds by default), while document
ingestion retains the 120-second embedding timeout. The default request budget
is rewrite 45s + query embedding 30s + answer generation 180s, with Spring
allowing 300s and the browser allowing 330s. Summary is capped at 30s.

For lower latency, the service skips query rewriting when the current question
does not contain an ambiguous reference. Ollama generation keeps the model warm
for 30 minutes and uses bounded context/output budgets. Tune these with
`OLLAMA_KEEP_ALIVE`, `OLLAMA_NUM_CTX`, `OLLAMA_CHAT_NUM_PREDICT`,
`OLLAMA_REWRITE_NUM_PREDICT`, and `OLLAMA_SUMMARY_NUM_PREDICT`. Completion logs
include `rewrite_ms`, `embedding_ms`, `retrieval_ms`, and `generation_ms` so the
slow stage is visible without profiling. Conversation summaries are dispatched
to a single background worker by Spring and no longer delay the chat response.
The latency-oriented defaults send at most 2 chunks / 2400 characters to the
answer model. Raise `RAG_TOP_K` or `RAG_CONTEXT_MAX_CHARS` only for questions
that demonstrably need synthesis across more sources; prompt evaluation is the
dominant cost on CPU-only Ollama deployments.

Rewrite and summary responses use Ollama structured-output schemas. The answer
path uses Ollama JSON mode followed by strict Pydantic validation to avoid the
schema grammar's generation overhead on CPU. Qwen reasoning before `</think>` is removed before validation, stored summaries
are limited to the final clean answer, and summary-shaped answers are rejected
to prevent internal conversation memory from leaking into learner responses.

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
- `POST /chat`: `{ "question": "Nó được tạo khi nào?", "course_id": "CIF", "conversation_summary": "...", "recent_messages": [{"role": "user", "content": "CIF là gì?"}] }`
- `POST /quiz/generate`: creates grounded single/multiple-choice drafts. Retrieval is always filtered by `course_id`; optional `lesson_name` only guides semantic search and is never treated as a knowledge source.
- `POST /summarize`: internal endpoint receiving `current_summary` and `new_messages`

Spring Boot provides the authenticated application-facing endpoints:

- `POST /api/admin/topics/{topicId}/documents` (admin, multipart `file`)
- `GET /api/admin/topics/{topicId}/documents` (admin)
- `POST /api/rag/chat` (authenticated)
- `POST /api/admin/quizzes/generate` (admin; topic is required and lesson is optional)
- `GET /api/rag/chat/history?courseId=...&conversationId=...` (authenticated; `conversationId` is optional)

The application-facing chat request accepts an optional `conversationId`. If
omitted, Spring creates a conversation owned by the authenticated user and
returns its ID. Subsequent requests must send that ID. Spring validates both
ownership and course scope before reading any history or calling the RAG
service. Recent-window and summary thresholds are configured with
`RAG_RECENT_MESSAGE_LIMIT`, `RAG_RECENT_CHARACTER_LIMIT`,
`RAG_SUMMARY_MESSAGE_THRESHOLD`, and `RAG_SUMMARY_CHARACTER_THRESHOLD`.

The Vue API helpers are in `CoreTraining/src/api/adminApi.ts` (upload/list) and `CoreTraining/src/api/ragApi.ts` (chat). Upload uses `FormData`; never set the global JSON header for this request.
