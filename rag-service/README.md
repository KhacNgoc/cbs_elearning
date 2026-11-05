# RAG service (week 1)

The FastAPI service accepts PDF/DOCX documents, splits extracted text by page, embeds chunks with Ollama, and stores them in Qdrant. Embeddings and Qdrant upserts run in configurable batches (`EMBEDDING_BATCH_SIZE=32` by default). It uses `AsyncQdrantClient`, so Qdrant network work does not block FastAPI's event loop.

Each Qdrant payload stores `excerpt` (the first 500 characters, returned to the frontend) separately from `full_text` (the entire chunk, used only to construct the LLM context).

## Nguồn kiến thức cho bài kiểm tra bài học

Trong phần **Tạo bằng AI**, người soạn chọn tài liệu AI của khóa học và lưu nguồn
cho bài học trước khi tạo câu hỏi. PDF hỗ trợ toàn bộ tài liệu hoặc nhiều khoảng
trang (số trang của file). DOCX hỗ trợ toàn bộ tài liệu hoặc nhiều mục theo các
heading của file; DOCX không có thông tin phân trang nên không chọn theo trang.
Có thể xem trích đoạn trong phạm vi đã chọn hoặc tải thêm tài liệu ngay tại đây.
Tài liệu phải xử lý xong trước khi chọn.

Nguồn được lưu trong bảng `lesson_knowledge_sources`, liên kết bài học với
`course_documents` và phạm vi trang/mục. Bảng được Hibernate tạo bổ sung khi
backend khởi động với cấu hình `ddl-auto: update` hiện tại. Cần cập nhật đồng thời
backend, frontend và RAG service. Không cần reset database hoặc embedding lại
tài liệu đã có: truy vấn sử dụng các payload `course_id`, `document_id`, `page`
và `section` hiện có. Một tài liệu/phần tài liệu có thể được dùng cho nhiều bài.

Backend đọc nguồn đã lưu để gửi `lesson_id`, `lesson_name` và `sources` vào
`/quiz/generate`; không nhận phạm vi do client tự khai báo cho lần tạo đề.
RAG lọc khóa học AND (tài liệu + phạm vi), OR giữa các nguồn. Bài học chưa có
nguồn bị từ chối; thiếu nội dung sẽ trả cảnh báo, không tự mở rộng sang cả khóa.
Kiểm định LLM về căn cứ và mức độ phù hợp với bài học luôn chạy cho đề bài học.
Chatbot và đề tổng hợp khóa học vẫn dùng phạm vi khóa học.

`GET /documents/{document_id}/outline?course_id=...` đọc metadata/trích đoạn
từ Qdrant để hỗ trợ chọn nguồn, không gọi embedding. Backend kiểm tra tài liệu
thuộc đúng khóa và đã sẵn sàng trước khi cho phép xem hoặc lưu nguồn.

Kiểm thử RAG không cần kết nối Ollama/Qdrant bên ngoài:

```sh
python -B -m unittest discover -s tests -v
```

## Code structure

- `app/main.py`: creates the FastAPI application and wires the router.
- `app/api/routes.py`: HTTP endpoints and request delegation only.
- `app/config.py`: environment-backed settings and constants.
- `app/infrastructure.py`: shared Ollama/Qdrant clients, embeddings, and lifecycle.
- `app/schemas.py`: request, response, and structured LLM schemas.
- `app/services/`: chat, document ingestion, quiz generation, and summary logic.
- `app/utils.py` and `app/quiz_utils.py`: reusable pure helper functions.

When adding behavior, keep transport concerns in `api`, business flows in
`services`, external clients in `infrastructure`, and side-effect-free helpers in
the utility modules.

## Run

1. Copy `.env.example` to `.env` and set a PostgreSQL password.
2. Run `docker compose --profile chatbot up -d --build` from the repository root. The initial Ollama model download may take several minutes.
3. Check `http://localhost:8001/health`.

The `chatbot` profile includes Qdrant, Ollama, model initialization, and the RAG
service. Plain `docker compose up -d --build` starts the core application without
these services. Chat, AI question generation, and AI document processing require
the chatbot services to be running.

To start only the chatbot stack using production environment settings:

```bash
docker compose --env-file .env.production --profile chatbot up -d --build qdrant ollama ollama-init rag-service
```

Adding profiles does not stop containers that are already running. To stop only
the chatbot stack without removing stored models or vector data:

```bash
docker compose --env-file .env.production --profile chatbot stop rag-service ollama-init ollama qdrant
```

On Apple Silicon, Ollama inside Docker is CPU-only. For low-latency quiz
generation, run Ollama natively with Metal and apply the macOS Compose override:

```bash
OLLAMA_HOST=127.0.0.1:11435 OLLAMA_NUM_PARALLEL=5 ollama serve
OLLAMA_HOST=http://127.0.0.1:11435 ollama pull qwen3:4b
OLLAMA_HOST=http://127.0.0.1:11435 ollama pull bge-m3
docker compose --env-file .env.production \
  -f docker-compose.yml -f docker-compose.macos.yml --profile chatbot up -d --build
```

The native Ollama process must remain running. The RAG service keeps the quiz
model loaded for 30 minutes, avoiding cold-start cost on normal requests.

## Run application services locally (Qdrant in Docker)

Only Qdrant needs to run in Docker. Ollama, Python, Spring Boot, and Vue run
directly on the host machine.

1. Start Ollama and download the models once:

```bash
ollama serve
ollama pull bge-m3
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
score. Its default threshold is `0.40`, chosen for `bge-m3` in `eval/results/REPORT.md`; override it with
`RAG_SCORE_THRESHOLD` only after measuring with `eval/retrieval_eval.py`.

Context-aware chat uses `OLLAMA_REWRITE_MODEL` and `OLLAMA_SUMMARY_MODEL`
(both default to `OLLAMA_CHAT_MODEL`). Retrieval can be tuned with `RAG_TOP_K`,
`RAG_CANDIDATE_LIMIT`, and `RAG_CONTEXT_MAX_CHARS`. Ollama timeouts are
configured independently through `OLLAMA_CHAT_TIMEOUT_SECONDS`,
`OLLAMA_REWRITE_TIMEOUT_SECONDS`, and `OLLAMA_SUMMARY_TIMEOUT_SECONDS`.

Quiz generation defaults to `OLLAMA_QUIZ_MODEL=qwen3:4b`. Each candidate is a compact,
single-source generation. Retrieval scales with the request: about 1.5 chunks per
question and at least `QUIZ_RETRIEVAL_LIMIT` (5), so `QUIZ_MAX_QUESTIONS` (10, shared
with the backend and the frontend build) also bounds retrieval. Chunks are
spread across the selected documents and sampled among the most relevant chunks,
so repeated drafts use different passages. Candidates run as a pipeline:
generate, structural and duplicate checks, then review; every rejection is
replaced until each type is filled. `QUIZ_OVERGENERATION_FACTOR` (1.5) sets the
candidates wanted per missing question; each job queues at most
`QUIZ_GENERATION_CONCURRENCY` of them at a time, so reviews and other jobs do not
wait behind a whole batch. `QUIZ_CANDIDATE_BUDGET_FACTOR` (3) caps
total candidates per requested question, and `QUIZ_GENERATION_TIME_BUDGET_SECONDS`
(300) stops starting new candidates so a slow model still returns a partial draft.
Requests may carry `existing_questions` (saved quiz, question bank and unsaved
drafts); candidates repeating them are rejected and the closest ones are listed
in the prompt. `QUIZ_TEMPERATURE` (0.6) controls wording variety.
Runtime capacity is configurable: use `QUIZ_GENERATION_CONCURRENCY=1` on
constrained CPU machines, and increase it to match the inference server's
parallel slots on GPU servers. `QUIZ_NUM_CTX` defaults to `OLLAMA_NUM_CTX`: Ollama
reloads a model whose context size changes, so a different value makes chat,
quiz generation and review evict each other when they share a model.
`QUIZ_SOURCE_MAX_CHARS`, `QUIZ_SINGLE_NUM_PREDICT`, and `QUIZ_MULTIPLE_NUM_PREDICT`
bound token costs without changing code. Flashcards use the same pipeline, candidate
and time budgets; `QUIZ_GENERATION_MAX_ATTEMPTS` is only reported in job status.
The per-question timeout is controlled by
`OLLAMA_QUIZ_GENERATION_TIMEOUT_SECONDS` (120s). A second LLM verification pass
is always enabled for lesson quizzes and enabled by default for course-wide
quizzes with `QUIZ_LLM_VERIFICATION_ENABLED=true`. Each question is reviewed
independently using `OLLAMA_CHAT_MODEL`: the reviewer receives the stem and
options without the proposed answer key, identifies all correct options from
the source, and the result must match the generated key. Its timeout is controlled by
`OLLAMA_QUIZ_VERIFICATION_TIMEOUT_SECONDS` (60s).

Quiz generation defaults to qwen3:4b. Structural checks reject answer phrases
in the stem, malformed fill-in questions, circular definitions, broken option
text, and false distractors made by recombining already-correct independent items.
Repeated objectives are removed. Replacements receive rejection feedback.
If too few candidates pass, the response returns fewer questions with a warning
instead of filling the requested count with invalid questions.

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
include `rewrite_ms`, `topic_ms`, `embedding_ms`, `retrieval_ms`, and `generation_ms` so the
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
- `POST /quiz/generate`: creates grounded single/multiple-choice drafts. Retrieval is filtered by `course_id` and saved document/page/section sources for lesson quizzes. Lesson requests require explicit sources; the lesson title guides semantic search but is not a knowledge source.
- `POST /summarize`: internal endpoint receiving `current_summary` and `new_messages`

Spring Boot provides the authenticated application-facing endpoints:

- `POST /api/admin/courses/{courseId}/documents` (admin, multipart `file`)
- `GET /api/admin/courses/{courseId}/documents` (admin)
- `GET /api/admin/courses/{courseId}/documents/{documentId}/outline` (admin)
- `GET /api/admin/lessons/{lessonId}/knowledge-sources` (admin)
- `PUT /api/admin/lessons/{lessonId}/knowledge-sources` (admin; save an array of document/page/section selections)
- `POST /api/rag/chat` (authenticated)
- `POST /api/admin/quizzes/generate` (admin; course is required; a selected lesson must have saved sources)
- `GET /api/rag/chat/history?courseId=...&conversationId=...` (authenticated; `conversationId` is optional)

The application-facing chat request accepts an optional `conversationId`. If
omitted, Spring creates a conversation owned by the authenticated user and
returns its ID. Subsequent requests must send that ID. Spring validates both
ownership and course scope before reading any history or calling the RAG
service. Recent-window and summary thresholds are configured with
`RAG_RECENT_MESSAGE_LIMIT`, `RAG_RECENT_CHARACTER_LIMIT`,
`RAG_SUMMARY_MESSAGE_THRESHOLD`, and `RAG_SUMMARY_CHARACTER_THRESHOLD`.

The Vue API helpers are in `CoreTraining/src/api/adminApi.ts` (upload/list) and `CoreTraining/src/api/ragApi.ts` (chat). Upload uses `FormData`; never set the global JSON header for this request.
# Ollama GPU on macOS

The macOS override connects RAG to native Ollama on localhost port 11435.
The embedding model must remain the same as the one used to index existing vectors.
To change it, follow "Changing the embedding model" below.
Install the included LaunchAgent after installing Ollama at /usr/local/bin/ollama:

```sh
mkdir -p "$HOME/Library/LaunchAgents"
cp scripts/com.cbs-elearning.ollama.plist "$HOME/Library/LaunchAgents/"
launchctl bootstrap "gui/$(id -u)" "$HOME/Library/LaunchAgents/com.cbs-elearning.ollama.plist"
OLLAMA_HOST=http://127.0.0.1:11435 ollama pull qwen3:4b
OLLAMA_HOST=http://127.0.0.1:11435 ollama pull bge-m3
docker compose --env-file .env.production -f docker-compose.yml -f docker-compose.macos.yml --profile chatbot up -d --build --no-deps backend frontend rag-service
```

The launch agent starts at login and restarts the native server if it exits.
Chat output is limited to a short answer with a 384-token budget. Truncated or
invalid JSON receives at most one compact retry within the same generation
deadline. Output errors use HTTP 502, generation timeouts use HTTP 504, and
transport/model availability errors use HTTP 503. Ollama timings and stop
reasons are logged without logging the answer body.

# Ollama GPU on NVIDIA

Ollama in Docker runs on the CPU unless it is given a GPU. On Linux or Windows with an NVIDIA GPU, add
`docker-compose.gpu.yml` to every Compose command, including those in the next section. This has not
been run on real NVIDIA hardware yet, so do the check below on the first machine.

Linux needs the NVIDIA driver and the NVIDIA Container Toolkit. Windows needs the NVIDIA driver and
Docker Desktop with the WSL 2 backend.

```bash
docker compose --env-file .env.production -f docker-compose.yml -f docker-compose.gpu.yml --profile chatbot up -d --build
```

Ask the chatbot a question, then check where the model runs while it answers. `PROCESSOR` must show
`GPU`. If it shows `CPU`, the container did not get the GPU:

```bash
docker compose --env-file .env.production -f docker-compose.yml -f docker-compose.gpu.yml --profile chatbot exec ollama ollama ps
```

Do not use this file on a machine without an NVIDIA GPU, because Ollama may fail to start. It is
also not for macOS, where Docker cannot use the GPU.

# Changing the embedding model

Vectors from different models cannot share a collection, so a new model needs a new collection
(`RAG_COLLECTION`). Models may also expect task prefixes (`EMBEDDING_QUERY_PREFIX`,
`EMBEDDING_DOCUMENT_PREFIX`), and `RAG_SCORE_THRESHOLD` must be measured again for each model.

These steps were run on macOS only. The Linux and Windows commands follow from the Compose files
and have not been run. On Windows, use Docker Desktop; the commands are the same in PowerShell.

## New machine with no chatbot data

Do not run the reindex: there is no source collection to copy from, so it stops with an error.
Indexed documents live in the `qdrant_data` Docker volume and do not come with the code. Upload the
course documents again through the admin pages, or copy the volume from the old machine.

On Linux or Windows, this also downloads every model, so the first run can take a while:

```bash
docker compose --env-file .env.production --profile chatbot up -d --build
```

On macOS, Ollama runs outside Docker. Pull each model named in `.env.production`, including this one:

```bash
ollama pull bge-m3
```

```bash
docker compose --env-file .env.production -f docker-compose.yml -f docker-compose.macos.yml --profile chatbot up -d --build
```

## Machine that already has chatbot data

Set `OLLAMA_EMBEDDING_MODEL` and `RAG_COLLECTION` in `.env.production`, then keep this order. If the
service restarts before the reindex, it finds no documents. Qdrant and Ollama must already be
running, because the reindex uses `--no-deps`. The source collection is only read.

On Linux or Windows:

```bash
docker compose --env-file .env.production --profile chatbot up ollama-init
```

```bash
docker compose --env-file .env.production --profile chatbot build rag-service
```

```bash
docker compose --env-file .env.production --profile chatbot run --rm --no-deps rag-service python -m app.reindex --source course_documents
```

```bash
docker compose --env-file .env.production --profile chatbot up -d --no-deps rag-service
```

On macOS:

```bash
ollama pull bge-m3
```

```bash
docker compose --env-file .env.production -f docker-compose.yml -f docker-compose.macos.yml --profile chatbot build rag-service
```

```bash
docker compose --env-file .env.production -f docker-compose.yml -f docker-compose.macos.yml --profile chatbot run --rm --no-deps rag-service python -m app.reindex --source course_documents
```

```bash
docker compose --env-file .env.production -f docker-compose.yml -f docker-compose.macos.yml --profile chatbot up -d --no-deps rag-service
```

If the target already has chunks, the reindex stops rather than risk restoring documents deleted
since; `--overwrite` rebuilds it. Documents uploaded between the reindex and the restart must be
uploaded again.

## Going back to `nomic-embed-text`

Set these in `.env.production` and restart `rag-service`. Documents uploaded since the switch must be
uploaded again, because they exist only in the new collection:

```bash
OLLAMA_EMBEDDING_MODEL=nomic-embed-text
RAG_COLLECTION=course_documents
RAG_SCORE_THRESHOLD=0.55
EMBEDDING_QUERY_PREFIX="search_query: "
EMBEDDING_DOCUMENT_PREFIX="search_document: "
```

## Speed on other machines

Retrieval results do not depend on the operating system, but speed does. The answer times in
`eval/results/REPORT.md` were measured on a Mac with Ollama running outside Docker. Answers can be
much slower with Ollama inside Docker, especially without a GPU, so measure again on the new machine.
