"""Conversation summary maintenance."""

import json

import httpx
from fastapi import HTTPException

from app import config, infrastructure
from app.schemas import SummaryModelResult, SummaryRequest, SummaryResponse
from app.utils import (
    format_messages,
    ollama_compatible_schema,
    sanitize_conversation_summary,
    strip_model_thinking,
)


async def summarize_conversation(request: SummaryRequest) -> SummaryResponse:
    current_summary = sanitize_conversation_summary(request.current_summary)
    system_prompt = """Bạn là hệ thống tóm tắt ngữ cảnh hội thoại cho chatbot RAG.
Cập nhật bản tóm tắt dựa trên bản hiện tại và các tin nhắn mới.

Giữ khóa học chính, thực thể được tham chiếu, câu hỏi chính, đính chính/phủ định mới nhất, thay đổi khóa học và điểm chưa giải quyết.
Không thêm kiến thức ngoài hội thoại. Không coi câu trả lời của trợ lý là nguồn nghiệp vụ chính thức.
Viết ngắn gọn, giữ ngôn ngữ chính của hội thoại và tối đa 4000 ký tự.
Không trả suy luận, phân tích, thẻ think, Markdown hoặc giải thích cách tạo tóm tắt.
Chỉ trả JSON hợp lệ đúng dạng: {"summary":"bản tóm tắt"}."""

    prompt = f"""<current_summary>
{current_summary or '(không có)'}
</current_summary>

<new_messages>
{format_messages(request.new_messages)}
</new_messages>"""
    try:
        raw_summary = await infrastructure.ollama_chat(
            config.SUMMARY_MODEL,
            [
                {"role": "system", "content": system_prompt},
                {"role": "user", "content": prompt},
            ],
            config.OLLAMA_SUMMARY_TIMEOUT_SECONDS,
            json_schema=ollama_compatible_schema(
                SummaryModelResult.model_json_schema()
            ),
            num_predict=config.OLLAMA_SUMMARY_NUM_PREDICT,
        )
        summary = SummaryModelResult.model_validate(
            json.loads(strip_model_thinking(raw_summary))
        ).summary.strip()
    except (
        httpx.HTTPError,
        KeyError,
        TypeError,
        ValueError,
        json.JSONDecodeError,
        RuntimeError,
    ) as error:
        raise HTTPException(503, "Summary model is unavailable") from error
    if not summary or "<think>" in summary.lower() or "</think>" in summary.lower():
        raise HTTPException(502, "Summary model returned an empty response")
    return SummaryResponse(summary=summary)


async def summarize_lesson_diff(request: "LessonDiffSummaryRequest") -> "LessonDiffSummaryResponse":
    from app.schemas import LessonDiffSummaryResponse
    system_prompt = """Bạn là trợ lý đào tạo nội bộ chuyên nghiệp của ngân hàng BIDV.
Nhiệm vụ của bạn: So sánh nội dung cũ và nội dung mới của bài học, sau đó viết 1 đến 2 câu tóm tắt thật ngắn gọn, súc tích (dưới 200 ký tự) về những điểm cốt lõi đã thay đổi để gửi thông báo cho học viên đã hoàn thành bài học này.
Quy tắc:
1. Tập trung vào: quy trình thay đổi, quy định mới, số liệu điều chỉnh, tài liệu bổ sung, câu hỏi kiểm tra cập nhật.
2. Viết bằng tiếng Việt, văn phong thông báo trang trọng, rõ ràng.
3. Nếu nội dung cũ rỗng hoặc không có gì thay đổi nhiều, ghi tóm tắt cập nhật kiến thức bài giảng.
4. Không trả suy luận, thẻ think, giải thích hay markdown.
5. Chỉ trả về JSON hợp lệ đúng dạng: {"summary":"câu tóm tắt ngắn gọn"}."""

    prompt = f"""<lesson_title>{request.lesson_title}</lesson_title>
<old_content>
{request.old_content[:8000] if request.old_content else "(chưa có nội dung cũ)"}
</old_content>
<new_content>
{request.new_content[:8000] if request.new_content else "(chưa có nội dung mới)"}
</new_content>"""

    try:
        raw_summary = await infrastructure.ollama_chat(
            config.SUMMARY_MODEL,
            [
                {"role": "system", "content": system_prompt},
                {"role": "user", "content": prompt},
            ],
            config.OLLAMA_SUMMARY_TIMEOUT_SECONDS,
            json_schema=ollama_compatible_schema(
                SummaryModelResult.model_json_schema()
            ),
            num_predict=config.OLLAMA_SUMMARY_NUM_PREDICT,
        )
        summary = SummaryModelResult.model_validate(
            json.loads(strip_model_thinking(raw_summary))
        ).summary.strip()
        if summary and "<think>" not in summary.lower():
            return LessonDiffSummaryResponse(summary=summary)
    except Exception:
        pass
    return LessonDiffSummaryResponse(
        summary=f"Cập nhật nội dung và tài liệu mới cho bài học {request.lesson_title}."
    )
