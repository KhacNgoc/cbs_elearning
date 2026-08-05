import json
import unittest
from types import SimpleNamespace
from unittest.mock import AsyncMock, patch

from app import main


async def no_op_collection(*_args):
    return None


async def fake_embedding(_texts, *_args):
    return [[0.1, 0.2, 0.3]]


class FakeQdrant:
    def __init__(self, points):
        self.points = points

    async def query_points(self, **_kwargs):
        return SimpleNamespace(points=self.points)


class FakeResponse:
    status_code = 200
    text = ""

    def __init__(self, content):
        self.content = content

    def json(self):
        return {"message": {"content": json.dumps(self.content)}}


class FakeHttpClient:
    response_content = {}

    def __init__(self, **_kwargs):
        pass

    async def __aenter__(self):
        return self

    async def __aexit__(self, *_args):
        return None

    async def post(self, *_args, **_kwargs):
        return FakeResponse(self.response_content)


class ChatGroundingTest(unittest.IsolatedAsyncioTestCase):
    def setUp(self):
        main.ollama_http_client = None

    async def test_low_retrieval_score_refuses_without_calling_llm(self):
        low_score_point = SimpleNamespace(score=0.1, payload={})
        with patch.object(main, "ensure_collection", no_op_collection), \
                patch.object(main, "embeddings", fake_embedding), \
                patch.object(main, "qdrant", FakeQdrant([low_score_point])), \
                patch.object(main.httpx, "AsyncClient", side_effect=AssertionError("LLM must not be called")):
            result = await main.chat(main.ChatRequest(question="Không có trong tài liệu", course_id="CIF"))

        self.assertFalse(result.grounded)
        self.assertEqual(main.REFUSAL_ANSWER, result.answer)
        self.assertEqual([], result.sources)

    async def test_follow_up_uses_rewritten_question_for_embedding(self):
        embedded_texts = []
        embedding_options = []

        async def capture_embedding(texts, *args):
            embedded_texts.extend(texts)
            embedding_options.append(args)
            return [[0.1, 0.2, 0.3]]

        rewrite = json.dumps({
            "standalone_question": "CIF được tạo khi nào?",
            "needs_clarification": False,
            "reason": "",
        })
        with patch.object(main, "ensure_collection", no_op_collection), \
                patch.object(main, "embeddings", capture_embedding), \
                patch.object(main, "qdrant", FakeQdrant([])), \
                patch.object(main, "ollama_chat", AsyncMock(return_value=rewrite)):
            result = await main.chat(main.ChatRequest(
                question="Nó được tạo khi nào?",
                recent_messages=[
                    main.ConversationMessage(role="user", content="CIF là gì?"),
                    main.ConversationMessage(role="assistant", content="CIF là hồ sơ khách hàng."),
                ],
            ))

        self.assertEqual(["CIF được tạo khi nào?"], embedded_texts)
        self.assertEqual(
            ("search_query: ", main.OLLAMA_QUERY_EMBEDDING_TIMEOUT_SECONDS),
            embedding_options[0],
        )
        self.assertEqual("CIF được tạo khi nào?", result.standaloneQuestion)

    async def test_invalid_rewrite_for_ambiguous_question_requests_clarification(self):
        with patch.object(main, "ollama_chat", AsyncMock(return_value="not json")), \
                patch.object(main, "embeddings", side_effect=AssertionError("Embedding must not be called")), \
                patch.object(main, "qdrant", side_effect=AssertionError("Qdrant must not be called")):
            result = await main.chat(main.ChatRequest(
                question="Giải thích kỹ hơn bước đó.",
                recent_messages=[main.ConversationMessage(role="user", content="Cho tôi biết quy trình.")],
            ))

        self.assertTrue(result.needsClarification)
        self.assertFalse(result.grounded)
        self.assertEqual("", result.standaloneQuestion)

    async def test_invalid_rewrite_for_clear_question_falls_back_to_original(self):
        embedded_texts = []

        async def capture_embedding(texts, *_args):
            embedded_texts.extend(texts)
            return [[0.1, 0.2, 0.3]]

        with patch.object(main, "ollama_chat", AsyncMock(return_value="not json")), \
                patch.object(main, "ensure_collection", no_op_collection), \
                patch.object(main, "embeddings", capture_embedding), \
                patch.object(main, "qdrant", FakeQdrant([])):
            result = await main.chat(main.ChatRequest(
                question="GL là gì?",
                conversation_summary="Trước đó người dùng hỏi về CIF.",
            ))

        self.assertEqual(["GL là gì?"], embedded_texts)
        self.assertEqual("GL là gì?", result.standaloneQuestion)

    def test_rewrite_parser_accepts_json_code_fence(self):
        result = main.parse_rewrite_result(
            '```json\n{"standalone_question":"CIF là gì?","needs_clarification":false,"reason":""}\n```'
        )
        self.assertEqual("CIF là gì?", result.standalone_question)

    def test_rewrite_parser_rejects_empty_standalone_question(self):
        with self.assertRaises(ValueError):
            main.parse_rewrite_result(
                '{"standalone_question":"","needs_clarification":false,"reason":""}'
            )

    async def test_summary_uses_only_current_summary_and_new_messages(self):
        model = AsyncMock(return_value=json.dumps({
            "summary": "Chủ đề hiện tại: CIF. Câu hỏi còn mở: thời điểm tạo CIF."
        }))
        with patch.object(main, "ollama_chat", model):
            result = await main.summarize(main.SummaryRequest(
                current_summary="Người dùng đang hỏi về CIF.",
                new_messages=[
                    main.ConversationMessage(role="user", content="Nó được tạo khi nào?"),
                    main.ConversationMessage(role="assistant", content="Chưa tìm thấy thông tin."),
                ],
            ))

        self.assertIn("CIF", result.summary)
        prompt = model.await_args.args[1][1]["content"]
        self.assertIn("Người dùng đang hỏi về CIF.", prompt)
        self.assertIn("Nó được tạo khi nào?", prompt)

    def test_summary_sanitizer_removes_qwen_reasoning(self):
        dirty_summary = """Okay, let's analyze the conversation.
The instructions ask for a concise summary.
</think>
Chủ đề chính: Posting giao dịch
Điểm chưa giải quyết: Thời điểm posting"""

        self.assertEqual(
            "Chủ đề chính: Posting giao dịch\nĐiểm chưa giải quyết: Thời điểm posting",
            main.sanitize_conversation_summary(dirty_summary),
        )

    def test_ollama_schema_removes_unsupported_grammar_keywords(self):
        schema = main.ollama_compatible_schema(main.SummaryModelResult.model_json_schema())

        self.assertEqual(
            {
                "type": "object",
                "properties": {"summary": {"type": "string"}},
                "required": ["summary"],
            },
            schema,
        )

    def test_ollama_schema_resolves_nested_quiz_question_reference(self):
        schema = main.ollama_compatible_schema(main.CompactSingleChoiceQuestion.model_json_schema())

        self.assertEqual("object", schema["type"])
        self.assertIn("q", schema["properties"])
        self.assertEqual("integer", schema["properties"]["a"]["type"])

    async def test_internal_summary_shaped_answer_is_blocked(self):
        point = SimpleNamespace(score=0.9, payload={
            "document_id": "doc-1",
            "file_name": "GL.docx",
            "page": 1,
            "section": "Posting",
            "full_text": "Posting cập nhật giao dịch vào sổ cái.",
        })
        FakeHttpClient.response_content = {
            "answer": "Chủ đề chính: Bút toán. Câu hỏi chính: Posting là gì? Điểm chưa giải quyết: thời điểm posting.",
            "source_ids": ["SOURCE_1"],
        }
        with patch.object(main, "ensure_collection", no_op_collection), \
                patch.object(main, "embeddings", fake_embedding), \
                patch.object(main, "qdrant", FakeQdrant([point])), \
                patch.object(main.httpx, "AsyncClient", FakeHttpClient):
            result = await main.chat(main.ChatRequest(
                question="Posting ghi sổ thông tin gì?", course_id="GL"
            ))

        self.assertFalse(result.grounded)
        self.assertEqual(main.REFUSAL_ANSWER, result.answer)

    async def test_valid_source_id_returns_grounded_answer(self):
        point = SimpleNamespace(score=0.9, payload={
            "document_id": "doc-1",
            "file_name": "Quy trình.pdf",
            "page": 12,
            "section": "Điều kiện phê duyệt",
            "full_text": "Hồ sơ được phê duyệt khi đáp ứng đủ điều kiện.",
        })
        FakeHttpClient.response_content = {
            "answer": "Hồ sơ được phê duyệt khi đáp ứng đủ điều kiện.",
            "source_ids": ["SOURCE_1"],
        }
        with patch.object(main, "ensure_collection", no_op_collection), \
                patch.object(main, "embeddings", fake_embedding), \
                patch.object(main, "qdrant", FakeQdrant([point])), \
                patch.object(main.httpx, "AsyncClient", FakeHttpClient):
            result = await main.chat(main.ChatRequest(question="Khi nào hồ sơ được duyệt?", course_id="CIF"))

        self.assertTrue(result.grounded)
        self.assertEqual("doc-1", result.sources[0].documentId)
        self.assertEqual("Điều kiện phê duyệt", result.sources[0].section)

    async def test_unknown_model_source_id_is_rejected(self):
        point = SimpleNamespace(score=0.9, payload={
            "document_id": "doc-1",
            "file_name": "Quy trình.pdf",
            "page": 1,
            "full_text": "Nội dung có liên quan.",
        })
        FakeHttpClient.response_content = {
            "answer": "Một câu trả lời không có nguồn hợp lệ.",
            "source_ids": ["SOURCE_999"],
        }
        with patch.object(main, "ensure_collection", no_op_collection), \
                patch.object(main, "embeddings", fake_embedding), \
                patch.object(main, "qdrant", FakeQdrant([point])), \
                patch.object(main.httpx, "AsyncClient", FakeHttpClient):
            result = await main.chat(main.ChatRequest(question="Câu hỏi", course_id="CIF"))

        self.assertFalse(result.grounded)
        self.assertEqual(main.REFUSAL_ANSWER, result.answer)
        self.assertEqual([], result.sources)

    async def test_quiz_generation_filters_by_topic_and_uses_lesson_name_as_scope(self):
        point = SimpleNamespace(score=0.91, payload={
            "document_id": "doc-1",
            "file_name": "Quy trình.pdf",
            "page": 4,
            "section": "Phê duyệt",
            "full_text": "Hồ sơ cần được kiểm tra đầy đủ trước khi phê duyệt.",
        })
        qdrant = FakeQdrant([point])
        model = AsyncMock(side_effect=[json.dumps({
            "q": "Hồ sơ cần được xử lý thế nào trước khi phê duyệt?",
            "o": ["Kiểm tra đầy đủ", "Bỏ qua", "Hủy ngay", "Chuyển ra ngoài"],
            "a": 0,
        }), json.dumps({"accepted_question_indexes": [0]})])

        async def quiz_embeddings(texts, *_args):
            return [[0.1, 0.2, 0.3] for _ in texts]

        with patch.object(main, "ensure_collection", no_op_collection), \
                patch.object(main, "embeddings", quiz_embeddings), \
                patch.object(main, "qdrant", qdrant), \
                patch.object(main, "QUIZ_LLM_VERIFICATION_ENABLED", True), \
                patch.object(main, "ollama_chat", model):
            result = await main.generate_quiz(main.QuizGenerationRequest(
                course_id="CIF",
                topic_name="Quy trình tín dụng",
                lesson_name="Điều kiện phê duyệt",
                single_choice_count=1,
                multiple_choice_count=0,
                option_count=4,
            ))

        self.assertEqual(1, len(result.questions))
        self.assertEqual([0], result.questions[0].correct_answers)
        self.assertEqual(["SOURCE_1"], result.questions[0].source_ids)
        self.assertNotIn("Theo tài liệu", result.questions[0].text)
        prompt = model.await_args_list[0].args[1][1]["content"]
        self.assertIn("Bài học được chọn: Điều kiện phê duyệt", prompt)
        self.assertNotIn("procedureSteps", prompt)


if __name__ == "__main__":
    unittest.main()
