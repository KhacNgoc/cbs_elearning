"""Environment-backed configuration for the RAG service."""

import os


QDRANT_URL = os.getenv("QDRANT_URL", "http://localhost:6333")
OLLAMA_URL = os.getenv("OLLAMA_URL", "http://localhost:11434").rstrip("/")
CHAT_MODEL = os.getenv("OLLAMA_CHAT_MODEL", "qwen3:4b")
QUIZ_MODEL = os.getenv("OLLAMA_QUIZ_MODEL", "qwen3:4b")
REWRITE_MODEL = os.getenv("OLLAMA_REWRITE_MODEL", CHAT_MODEL)
SUMMARY_MODEL = os.getenv("OLLAMA_SUMMARY_MODEL", REWRITE_MODEL)
EMBEDDING_MODEL = os.getenv("OLLAMA_EMBEDDING_MODEL", "nomic-embed-text")
COLLECTION = "course_documents"
EMBEDDING_BATCH_SIZE = int(os.getenv("EMBEDDING_BATCH_SIZE", "32"))
RETRIEVAL_SCORE_THRESHOLD = float(os.getenv("RAG_SCORE_THRESHOLD", "0.55"))
EXCERPT_LENGTH = 500
RETRIEVAL_LIMIT = int(os.getenv("RAG_TOP_K", "2"))
RETRIEVAL_CANDIDATE_LIMIT = int(os.getenv("RAG_CANDIDATE_LIMIT", "25"))
RETRIEVAL_CONTEXT_MAX_CHARS = int(os.getenv("RAG_CONTEXT_MAX_CHARS", "2400"))
# Cross-course chat searches a larger pool and slightly favours the course the
# learner is currently viewing without excluding the others.
MULTI_COURSE_CANDIDATE_LIMIT = int(os.getenv("RAG_MULTI_COURSE_CANDIDATE_LIMIT", "40"))
CONTEXT_COURSE_BOOST = float(os.getenv("RAG_CONTEXT_COURSE_BOOST", "1.08"))
COURSE_SUMMARY_CLARIFICATION = (
    "Bạn muốn tóm tắt khóa học nào? Hãy nêu tên khóa học trong câu hỏi "
    "hoặc mở khóa học đó rồi hỏi lại."
)
# {courses} is the comma-separated list of course names the learner can search.
GENERIC_QUESTION_CLARIFICATION = (
    "Câu hỏi này có thể áp dụng cho nhiều khóa học ({courses}). Bạn muốn hỏi về "
    "khóa nào? Hãy nêu tên khóa học trong câu hỏi hoặc mở khóa học đó rồi hỏi lại."
)
NO_ACCESSIBLE_COURSE_ANSWER = (
    "Bạn chưa có khóa học nào để trợ lý tra cứu tài liệu."
)
COURSE_SUMMARY_RETRIEVAL_LIMIT = int(os.getenv("RAG_COURSE_SUMMARY_TOP_K", "5"))
COURSE_SUMMARY_CONTEXT_MAX_CHARS = int(
    os.getenv("RAG_COURSE_SUMMARY_CONTEXT_MAX_CHARS", "6500")
)
# Upper bound of questions in one generation request, across all question types.
QUIZ_MAX_QUESTIONS = max(1, int(os.getenv("QUIZ_MAX_QUESTIONS", "10")))
QUIZ_RETRIEVAL_LIMIT = int(os.getenv("QUIZ_RETRIEVAL_LIMIT", "5"))
QUIZ_RETRIEVAL_CANDIDATE_LIMIT = int(os.getenv("QUIZ_RETRIEVAL_CANDIDATE_LIMIT", "12"))
QUIZ_SOURCE_MAX_CHARS = int(os.getenv("QUIZ_SOURCE_MAX_CHARS", "650"))
QUIZ_GENERATION_CONCURRENCY = max(1, int(os.getenv("QUIZ_GENERATION_CONCURRENCY", "2")))
QUIZ_GENERATION_MAX_ATTEMPTS = max(1, int(os.getenv("QUIZ_GENERATION_MAX_ATTEMPTS", "2")))
# Candidates wanted per missing question (each job queues at most
# QUIZ_GENERATION_CONCURRENCY at a time), and the total candidate budget per question.
QUIZ_OVERGENERATION_FACTOR = max(1.0, float(os.getenv("QUIZ_OVERGENERATION_FACTOR", "1.5")))
QUIZ_CANDIDATE_BUDGET_FACTOR = max(1.0, float(os.getenv("QUIZ_CANDIDATE_BUDGET_FACTOR", "3")))
# No new candidates start after this; the draft returns what passed so far.
QUIZ_GENERATION_TIME_BUDGET_SECONDS = float(os.getenv("QUIZ_GENERATION_TIME_BUDGET_SECONDS", "300"))
QUIZ_TEMPERATURE = float(os.getenv("QUIZ_TEMPERATURE", "0.6"))
OLLAMA_NUM_CTX = int(os.getenv("OLLAMA_NUM_CTX", "8192"))
# Ollama reloads a model whose num_ctx changes, so quiz calls share the chat context size.
QUIZ_NUM_CTX = int(os.getenv("QUIZ_NUM_CTX", str(OLLAMA_NUM_CTX)))
QUIZ_SINGLE_NUM_PREDICT = int(os.getenv("QUIZ_SINGLE_NUM_PREDICT", "320"))
QUIZ_MULTIPLE_NUM_PREDICT = int(os.getenv("QUIZ_MULTIPLE_NUM_PREDICT", "400"))
# The review's reason may be up to 600 characters, which is over 300 tokens in Vietnamese.
QUIZ_VERIFICATION_NUM_PREDICT = int(os.getenv("QUIZ_VERIFICATION_NUM_PREDICT", "512"))
# Flashcards share the quiz model, semaphore and job cap so AI load stays bounded.
FLASHCARD_NUM_PREDICT = int(os.getenv("FLASHCARD_NUM_PREDICT", "200"))
FLASHCARD_SOURCE_MAX_CHARS = int(os.getenv("FLASHCARD_SOURCE_MAX_CHARS", "650"))
OLLAMA_CHAT_TIMEOUT_SECONDS = float(os.getenv("OLLAMA_CHAT_TIMEOUT_SECONDS", "180"))
OLLAMA_QUIZ_GENERATION_TIMEOUT_SECONDS = float(
    os.getenv("OLLAMA_QUIZ_GENERATION_TIMEOUT_SECONDS", "210")
)
OLLAMA_QUIZ_VERIFICATION_TIMEOUT_SECONDS = float(
    os.getenv("OLLAMA_QUIZ_VERIFICATION_TIMEOUT_SECONDS", "60")
)
QUIZ_LLM_VERIFICATION_ENABLED = os.getenv(
    "QUIZ_LLM_VERIFICATION_ENABLED", "true"
).strip().lower() in {"1", "true", "yes", "on"}
OLLAMA_REWRITE_TIMEOUT_SECONDS = float(os.getenv("OLLAMA_REWRITE_TIMEOUT_SECONDS", "45"))
OLLAMA_QUERY_EMBEDDING_TIMEOUT_SECONDS = float(
    os.getenv("OLLAMA_QUERY_EMBEDDING_TIMEOUT_SECONDS", "30")
)
OLLAMA_SUMMARY_TIMEOUT_SECONDS = float(os.getenv("OLLAMA_SUMMARY_TIMEOUT_SECONDS", "30"))
OLLAMA_KEEP_ALIVE = os.getenv("OLLAMA_KEEP_ALIVE", "30m")
OLLAMA_CHAT_NUM_PREDICT = int(os.getenv("OLLAMA_CHAT_NUM_PREDICT", "384"))
COURSE_SUMMARY_NUM_PREDICT = int(os.getenv("RAG_COURSE_SUMMARY_NUM_PREDICT", "256"))
OLLAMA_REWRITE_NUM_PREDICT = int(os.getenv("OLLAMA_REWRITE_NUM_PREDICT", "96"))
OLLAMA_SUMMARY_NUM_PREDICT = int(os.getenv("OLLAMA_SUMMARY_NUM_PREDICT", "192"))

REFUSAL_ANSWER = "Tôi chưa tìm thấy thông tin này trong tài liệu của khóa học."
QUERY_STOP_WORDS = {
    "ai", "bao", "các", "cho", "có", "công", "của", "được", "gì", "hay",
    "khi", "là", "một", "nào", "những", "ra", "sao", "thế", "theo", "thì",
    "tính", "tôi", "trong", "và", "về", "với",
}
