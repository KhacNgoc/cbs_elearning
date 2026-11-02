"""Pure helpers shared by chat, summary, retrieval, and quiz services."""

import json
import re
import unicodedata

from app.config import QUERY_STOP_WORDS
from app.schemas import ConversationMessage, RewriteResult


SUMMARY_LABELS = (
    "khóa học chính:",
    "thực thể được tham chiếu:",
    "câu hỏi chính:",
    "đính chính/phủ định mới nhất:",
    "điểm chưa giải quyết:",
)
COURSE_SUMMARY_PATTERN = re.compile(
    r"(tóm\s+tắt|tom\s+tat|tổng\s+quan|tong\s+quan|"
    r"nội\s+dung\s+(?:của\s+)?(?:khóa|khoá)\s+học|"
    r"noi\s+dung\s+(?:cua\s+)?khoa\s+hoc|course\s+summary|"
    r"summari[sz]e\s+(?:this\s+)?course)",
    re.IGNORECASE,
)


def strip_model_thinking(content: str) -> str:
    cleaned = content.strip()
    if "</think>" in cleaned.lower():
        closing_index = cleaned.lower().rfind("</think>")
        cleaned = cleaned[closing_index + len("</think>"):].strip()
    cleaned = re.sub(
        r"<think>.*?</think>", "", cleaned, flags=re.DOTALL | re.IGNORECASE
    ).strip()
    fenced = re.fullmatch(
        r"```(?:json)?\s*(.*?)\s*```", cleaned, flags=re.DOTALL | re.IGNORECASE
    )
    return fenced.group(1).strip() if fenced else cleaned


def sanitize_conversation_summary(summary: str) -> str:
    cleaned = strip_model_thinking(summary)
    if not cleaned or "<think>" in cleaned.lower() or "</think>" in cleaned.lower():
        return ""
    return cleaned[-4000:]


def looks_like_internal_summary(answer: str) -> bool:
    normalized = answer.lower()
    return sum(label in normalized for label in SUMMARY_LABELS) >= 2


def is_course_summary_question(question: str) -> bool:
    return bool(COURSE_SUMMARY_PATTERN.search(question))


def fold_vietnamese(text: str) -> str:
    """Lowercase and strip diacritics so "Tín dụng" matches "tin dung"."""
    decomposed = unicodedata.normalize("NFD", text.lower().replace("đ", "d"))
    folded = "".join(ch for ch in decomposed if unicodedata.category(ch) != "Mn")
    return " ".join(re.findall(r"\w+", folded, flags=re.UNICODE))


def find_mentioned_course(question: str, course_names: dict[str, str]) -> str | None:
    """Return the id of the longest course name written in the question."""
    folded_question = f" {fold_vietnamese(question)} "
    best_id, best_length = None, 0
    for course_id, name in course_names.items():
        folded_name = fold_vietnamese(name or "")
        if len(folded_name) < 4 or len(folded_name) <= best_length:
            continue
        if f" {folded_name} " in folded_question:
            best_id, best_length = course_id, len(folded_name)
    return best_id


def diversify_course_summary_results(results: list) -> list:
    """Prefer different document sections before another nearby chunk."""
    diversified = []
    repeated = []
    seen_sections = set()
    for item in results:
        payload = item.payload or {}
        section_key = (
            str(payload.get("document_id", "")),
            str(payload.get("section", "")),
        )
        if section_key in seen_sections:
            repeated.append(item)
            continue
        seen_sections.add(section_key)
        diversified.append(item)
    return diversified + repeated


def ollama_compatible_schema(schema: dict, definitions: dict | None = None) -> dict:
    """Keep the JSON Schema subset supported by Ollama's grammar sampler."""
    definitions = definitions or schema.get("$defs", {})
    if "$ref" in schema:
        reference_name = str(schema["$ref"]).rsplit("/", 1)[-1]
        referenced = definitions.get(reference_name)
        return ollama_compatible_schema(referenced, definitions) if referenced else {}
    compatible = {}
    if "type" in schema:
        compatible["type"] = schema["type"]
    if "enum" in schema:
        compatible["enum"] = schema["enum"]
    if "properties" in schema:
        compatible["properties"] = {
            key: ollama_compatible_schema(value, definitions)
            for key, value in schema["properties"].items()
        }
    if "required" in schema:
        compatible["required"] = schema["required"]
    if "items" in schema:
        compatible["items"] = ollama_compatible_schema(schema["items"], definitions)
    return compatible


def format_messages(messages: list[ConversationMessage]) -> str:
    return "\n".join(
        f"{message.role.upper()}: {message.content}" for message in messages
    ) or "(không có)"


def parse_rewrite_result(raw_content: str) -> RewriteResult:
    cleaned = strip_model_thinking(raw_content)
    parsed = json.loads(cleaned)
    result = RewriteResult.model_validate(parsed)
    result.standalone_question = result.standalone_question.strip()
    result.reason = result.reason.strip()
    if not result.needs_clarification and not result.standalone_question:
        raise ValueError(
            "standalone_question is required when clarification is not needed"
        )
    return result


def split_text(text: str, chunk_size: int = 1100, overlap: int = 180) -> list[str]:
    cleaned = re.sub(r"\s+", " ", text).strip()
    if not cleaned:
        return []
    return [
        cleaned[start:start + chunk_size]
        for start in range(0, len(cleaned), chunk_size - overlap)
    ]


def relevance_score(question: str, full_text: str, vector_score: float) -> float:
    query_terms = {
        term for term in re.findall(r"\w+", question.lower(), flags=re.UNICODE)
        if term not in QUERY_STOP_WORDS
    }
    if not query_terms:
        return vector_score
    document_terms = set(re.findall(r"\w+", full_text.lower(), flags=re.UNICODE))
    lexical_coverage = len(query_terms & document_terms) / len(query_terms)
    return vector_score * (0.65 + 0.35 * lexical_coverage)
