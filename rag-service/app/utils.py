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
# Matched against fold_vietnamese text, so "khoá"/"khóa" and missing accents all match.
COURSE_SUMMARY_PATTERN = re.compile(
    r"\b(tom tat|tong quan|noi dung(?: \w+){0,3}? khoa hoc|course summary|"
    r"summari[sz]e(?: this)? course)\b"
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
    return bool(COURSE_SUMMARY_PATTERN.search(fold_vietnamese(question)))


def fold_vietnamese(text: str) -> str:
    """Lowercase and strip diacritics so "Tín dụng" matches "tin dung"."""
    decomposed = unicodedata.normalize("NFD", text.lower().replace("đ", "d"))
    folded = "".join(ch for ch in decomposed if unicodedata.category(ch) != "Mn")
    return " ".join(re.findall(r"\w+", folded, flags=re.UNICODE))


# Folded words that fit any course ("các bước nghiệp vụ quan trọng là gì?"), so a
# question made only of these names no topic to search for. Folding merges words, so
# "the", "tin", "dung" stay out ("thẻ", "tín dụng" are topics); the generic phrases
# that use them are removed first.
GENERIC_QUESTION_PHRASES = re.compile(r"\b(noi dung|thong tin)\b")
GENERIC_QUESTION_WORDS = {
    "ai", "bao", "biet", "buoc", "can", "cac", "chi", "chinh", "cho", "co", "cua",
    "dieu", "duoc", "gi", "giai", "gom", "hay", "hoc", "kien", "khi", "khoa", "ke",
    "la", "liet", "luu", "mot", "nao", "nay", "neu", "nghiep", "nhat", "nhung",
    "quan", "quy", "sao", "thich", "tiet", "toi", "trinh", "trong", "va", "ve",
    "voi", "vu", "y",
}


def is_generic_question(question: str) -> bool:
    terms = GENERIC_QUESTION_PHRASES.sub(" ", fold_vietnamese(question)).split()
    return bool(terms) and all(term in GENERIC_QUESTION_WORDS for term in terms)


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


# Words a learner may wrap around a course name when answering "which course?".
COURSE_REPLY_FILLER_WORDS = {"a", "cho", "em", "hoc", "hoi", "khoa", "la", "minh", "nhe", "toi", "ve"}


def find_replied_courses(reply: str, course_names: dict[str, str]) -> list[str]:
    """Return the courses whose name contains the reply, so "CIF" finds "CIF cơ bản"."""
    terms = [term for term in fold_vietnamese(reply).split() if term not in COURSE_REPLY_FILLER_WORDS]
    if not terms:
        return []
    phrase = f" {' '.join(terms)} "
    return [
        course_id for course_id, name in course_names.items()
        if phrase in f" {fold_vietnamese(name or '')} "
    ]


# Whole-section headings that mention every topic but explain none. Matched on folded text.
NAVIGATION_TERMS = r"muc luc(?: noi dung)?|ket luan"
NAVIGATION_SECTION_PATTERN = re.compile(rf"(?:\d+ )?(?:{NAVIGATION_TERMS})")
NAVIGATION_QUESTION_PATTERN = re.compile(rf"\b(?:{NAVIGATION_TERMS})\b")


def drop_navigation_results(results: list, question: str) -> list:
    """Remove contents and conclusion chunks, unless the question asks for them."""
    if NAVIGATION_QUESTION_PATTERN.search(fold_vietnamese(question)):
        return results
    return [
        item for item in results
        if not NAVIGATION_SECTION_PATTERN.fullmatch(
            fold_vietnamese(str((item.payload or {}).get("section", "")))
        )
    ]


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
