"""Pure retrieval and validation helpers for quiz generation."""

import random
import re
import unicodedata

from app.schemas import GeneratedQuizQuestion, QuizGenerationRequest


def normalized_quiz_text(text: str) -> str:
    text = unicodedata.normalize("NFD", text.casefold()).replace("đ", "d")
    text = "".join(char for char in text if not unicodedata.combining(char))
    return " ".join(re.findall(r"\w+", text))


QUESTION_STOP_TERMS = {
    "trong", "phan", "he", "cho", "vay", "duoc", "dung", "de", "nao", "gi", "la", "cua",
}


def question_terms(text: str) -> frozenset[str]:
    return frozenset(normalized_quiz_text(text).split()) - QUESTION_STOP_TERMS


def similar_terms(left: frozenset[str], right: frozenset[str], threshold: float = 0.75) -> bool:
    return bool(left and right) and len(left & right) / len(left | right) >= threshold


def same_question_objective(left: GeneratedQuizQuestion, right: GeneratedQuizQuestion) -> bool:
    if left.type != right.type:
        return False
    answers = lambda q: {normalized_quiz_text(q.options[i]) for i in q.correct_answers}
    if answers(left) != answers(right):
        return False
    return similar_terms(question_terms(left.text), question_terms(right.text))


def question_quality_error(question: GeneratedQuizQuestion) -> str | None:
    stem = normalized_quiz_text(question.text)
    if "dien vao cho trong" in stem and not re.search(r"_{2,}|\.{3,}|…|\[\s*\]", question.text):
        return "missing_blank"
    for index in question.correct_answers:
        answer = question.options[index]
        normalized_answer = normalized_quiz_text(answer)
        tokens = normalized_answer.split()
        # Whole phrases and explicit acronyms, not arbitrary single words or substrings.
        acronym = len(tokens) == 1 and bool(re.fullmatch(r"[A-ZĐ]{2,8}", answer.strip()))
        if (len(tokens) >= 2 or acronym) and f" {normalized_answer} " in f" {stem} ":
            return "answer_in_question"
        if re.search(r"thuat ngu nao|duoc goi la gi|goi la gi", stem):
            description = re.sub(r"^(?:ma|thuat ngu|khai niem)\s+", "", normalized_answer)
            if len(description.split()) >= 4 and f" {description} " in f" {stem} ":
                return "answer_repeats_question"
    return None


QUIZ_RETRIEVAL_ASPECTS = (
    "khái niệm, điều kiện và quy định quan trọng",
    "vai trò, trách nhiệm và trường hợp áp dụng",
    "ngoại lệ, rủi ro và sai sót thường gặp",
    "số liệu, giới hạn và thời điểm áp dụng",
    "quy trình, các bước thực hiện và thứ tự xử lý",
    "chức năng, màn hình và thao tác trên hệ thống",
    "phân biệt các loại, phân loại và so sánh",
    "hồ sơ, chứng từ và thông tin cần kiểm tra",
)


def quiz_retrieval_queries(
    request: QuizGenerationRequest, rng: random.Random | None = None, count: int = 4
) -> list[str]:
    """Return ``count`` aspect queries; a random sample makes repeated drafts differ."""
    scope = f"Khóa học: {request.course_name}."
    if request.lesson_name:
        scope += f" Bài học: {request.lesson_name}."
    aspects = (
        rng.sample(QUIZ_RETRIEVAL_ASPECTS, count) if rng else QUIZ_RETRIEVAL_ASPECTS[:count]
    )
    return [f"{scope} Tìm nội dung về {aspect}." for aspect in aspects]


def low_value_quiz_section(section: str) -> bool:
    normalized = re.sub(r"\s+", " ", section.strip().casefold())
    return normalized in {
        "kết luận", "mục lục", "mục lục nội dung", "nội dung tài liệu"
    }


def quiz_sentences(text: str) -> list[str]:
    return [
        sentence.strip(" -•\t")
        for sentence in re.split(r"(?<=[.!?;])\s+|\n+", text)
        if len(sentence.strip()) >= 40
    ]


def quiz_focus_snippet(
    text: str, candidate_index: int, max_chars: int = 650
) -> str:
    sentences = quiz_sentences(text)
    if not sentences:
        return text[:max_chars]
    start = candidate_index % len(sentences)
    focused = sentences[start]
    cursor = start + 1
    while cursor < len(sentences) and len(focused) < 220:
        addition = " " + sentences[cursor]
        if len(focused) + len(addition) > max_chars:
            break
        focused += addition
        cursor += 1
    return focused[:max_chars]


def generated_question_validation_error(
    question: GeneratedQuizQuestion,
    option_count: int,
    valid_source_ids: set[str],
    source_text_by_id: dict[str, str],
) -> str | None:
    question.text = re.sub(
        r"^\s*(?:theo|dựa\s+(?:trên|theo))\s+(?:tài\s+liệu|nguồn)"
        r"(?:\s+(?:được\s+)?cung\s+cấp)?\s*[,.:;\-]?\s*",
        "",
        question.text.strip(),
        flags=re.IGNORECASE,
    )
    question.options = [option.strip() for option in question.options]
    question.explanation = question.explanation.strip()
    normalized_source_ids = []
    for source_id in question.source_ids:
        cleaned_source_id = source_id.strip().upper().strip("[](){}<>")
        source_match = re.fullmatch(
            r"(?:SOURCE(?:[\s_:\-]*ID)?|S)?[\s_:\-]*(\d+)", cleaned_source_id
        )
        normalized_source_ids.append(
            f"SOURCE_{int(source_match.group(1))}"
            if source_match else cleaned_source_id
        )
    question.source_ids = list(dict.fromkeys(normalized_source_ids))
    question.correct_answers = sorted(set(question.correct_answers))
    if not question.text or len(question.options) != option_count:
        return "text_or_option_count"
    if any(not option for option in question.options):
        return "empty_option"
    if any(re.search(r"(?<=[^\W\d_]):|\b(\w+)\W+\1\b", option, re.IGNORECASE)
           for option in question.options):
        return "malformed_option"
    if len({option.casefold() for option in question.options}) != len(question.options):
        return "duplicate_option"
    generic_options = {
        "đúng", "sai", "không xác định", "chưa có trong tài liệu",
        "tất cả đáp án trên", "tất cả các đáp án trên",
    }
    if sum(
        option.casefold().rstrip(". ") in generic_options
        for option in question.options
    ) >= 2:
        return "generic_option_set"
    if not question.source_ids or any(
        source_id not in valid_source_ids for source_id in question.source_ids
    ):
        return "invalid_source"
    if any(
        index < 0 or index >= len(question.options)
        for index in question.correct_answers
    ):
        return "answer_out_of_range"
    if question.type == "MULTIPLE_CHOICE":
        # Recombining already-correct independent items cannot make a false distractor.
        stem = normalized_quiz_text(question.text)
        if re.search(r"cac.*(?:yeu to|thong tin|thuat ngu)|nhung.*(?:yeu to|thong tin|thuat ngu)", stem):
            parts = [set(re.split(r"\s+va\s+", normalized_quiz_text(option))) for option in question.options]
            if all(len(items) > 1 for items in parts):
                correct_items = set().union(*(parts[i] for i in question.correct_answers))
                if any(items <= correct_items for i, items in enumerate(parts)
                       if i not in question.correct_answers):
                    return "ambiguous_combined_option"
    quality_error = question_quality_error(question)
    if quality_error:
        return quality_error
    if question.type == "SINGLE_CHOICE":
        return None if len(question.correct_answers) == 1 else "single_answer_count"
    return (
        None
        if 1 < len(question.correct_answers) < len(question.options)
        else "multiple_answer_count"
    )
