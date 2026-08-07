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

Giữ chủ đề chính, thực thể được tham chiếu, câu hỏi chính, đính chính/phủ định mới nhất, thay đổi chủ đề và điểm chưa giải quyết.
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
