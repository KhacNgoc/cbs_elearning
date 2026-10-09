"""Pure retrieval and validation helpers for quiz generation."""

import re

from app.schemas import GeneratedQuizQuestion, QuizGenerationRequest


def quiz_retrieval_queries(request: QuizGenerationRequest) -> list[str]:
    scope = f"Khóa học: {request.course_name}."
    if request.lesson_name:
        scope += f" Bài học: {request.lesson_name}."
    aspects = (
        "khái niệm, điều kiện và quy định quan trọng",
        "vai trò, trách nhiệm và trường hợp áp dụng",
        "ngoại lệ, rủi ro và sai sót thường gặp",
        "số liệu, giới hạn và thời điểm áp dụng",
    )
    return [f"{scope} Tìm nội dung về {aspect}." for aspect in aspects]


def low_value_quiz_section(section: str) -> bool:
    normalized = re.sub(r"\s+", " ", section.strip().casefold())
    return normalized in {
        "kết luận", "mục lục", "mục lục nội dung", "nội dung tài liệu"
    }


def quiz_focus_snippet(
    text: str, candidate_index: int, max_chars: int = 650
) -> str:
    sentences = [
        sentence.strip(" -•\t")
        for sentence in re.split(r"(?<=[.!?;])\s+|\n+", text)
        if len(sentence.strip()) >= 40
    ]
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
    if question.type == "SINGLE_CHOICE":
        return None if len(question.correct_answers) == 1 else "single_answer_count"
    return (
        None
        if 1 < len(question.correct_answers) < len(question.options)
        else "multiple_answer_count"
    )
