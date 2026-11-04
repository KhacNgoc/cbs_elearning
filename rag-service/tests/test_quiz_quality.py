import asyncio
import unittest
from types import SimpleNamespace
from unittest.mock import AsyncMock, patch
from app.schemas import GeneratedQuizQuestion
from app.schemas import QuizGenerationRequest
from app.quiz_utils import generated_question_validation_error, same_question_objective
from app.services.quiz import generate_quiz
from app import config
from tests.quiz_fakes import FakeQuizModel, REVIEW_SYSTEM_PREFIX


class QuizQualityTests(unittest.TestCase):
    def question(self, text, answer):
        return GeneratedQuizQuestion(type="SINGLE_CHOICE", text=text,
            options=[answer, "Hạn mức", "Dư nợ gốc", "Lịch trả nợ"],
            correct_answers=[0], explanation="", source_ids=["SOURCE_1"])

    def test_reworded_question_with_same_answer_is_duplicate(self):
        self.assertTrue(same_question_objective(
            self.question("CIF được dùng để nhận diện đối tượng nào?", "Khách hàng"),
            self.question("CIF trong phân hệ cho vay được dùng để nhận diện đối tượng nào?", "Khách hàng")))

    def test_different_concepts_are_not_merged(self):
        self.assertFalse(same_question_objective(
            self.question("CIF nhận diện đối tượng nào?", "Khách hàng"),
            self.question("Mã hồ sơ nhận diện đối tượng nào?", "Hồ sơ khoản vay")))

    def error(self, stem, options, answers=None, kind="SINGLE_CHOICE"):
        question = GeneratedQuizQuestion(type=kind, text=stem, options=options,
            correct_answers=[0] if answers is None else answers, explanation="", source_ids=["SOURCE_1"])
        return generated_question_validation_error(question, 4, {"SOURCE_1"}, {"SOURCE_1": "Nội dung khóa học"})

    def test_definition_that_names_its_answer_is_rejected(self):
        self.assertEqual("answer_in_question", self.error(
            "Mã hồ sơ nhận diện một hồ sơ cụ thể và không thay thế CIF là gì?",
            ["Mã hồ sơ", "CIF", "Thông tin khoản vay", "Số điện thoại khách hàng"]))

    def test_answer_repeating_stem_is_rejected(self):
        self.assertEqual("answer_repeats_question", self.error(
            "Thuật ngữ nào được dùng để xác định khách hàng trong phân hệ cho vay?",
            ["Mã dùng để xác định khách hàng", "Hạn mức", "Dư nợ gốc", "Lịch trả nợ"]))

    def test_fake_fill_in_blank_is_rejected(self):
        self.assertEqual("missing_blank", self.error(
            "Điền vào chỗ trống: 'Mã dùng để xác định khách hàng' là gì?",
            ["Mã dùng để xác định khách hàng", "Hạn mức", "Dư nợ gốc", "Lịch trả nợ"]))

    def test_accents_case_and_punctuation_do_not_hide_answer(self):
        self.assertEqual("answer_in_question", self.error(
            "MA HO SO: nhận diện hồ sơ cụ thể là gì?", ["Mã hồ sơ", "CIF", "Hạn mức", "Lịch trả nợ"]))

    def test_acronym_leak_is_rejected(self):
        self.assertEqual("answer_in_question", self.error(
            "CIF là mã định danh khách hàng được gọi là gì?", ["CIF", "LN", "TK", "CH"]))

    def test_legitimate_question_about_acronym_is_kept(self):
        self.assertIsNone(self.error("CIF được dùng để nhận diện đối tượng nào?",
            ["Khách hàng", "Lịch trả nợ", "Chứng từ", "Lãi suất"]))

    def test_scenario_and_numeric_input_are_not_answer_leaks(self):
        self.assertIsNone(self.error("Đã giải ngân 80 triệu và thu 10 triệu tiền gốc thì dư nợ còn bao nhiêu?",
            ["70 triệu VND", "80 triệu VND", "90 triệu VND", "10 triệu VND"]))

    def test_multiple_choice_checks_every_correct_option(self):
        self.assertEqual("answer_in_question", self.error(
            "Khi đối soát số tiền, cần kiểm tra những thông tin nào?",
            ["Mã hồ sơ", "Số tiền", "Tài khoản nhận tiền", "Màu giao diện"],
            [0, 1, 2], "MULTIPLE_CHOICE"))

    def test_recombining_correct_concepts_is_not_a_false_distractor(self):
        self.assertEqual("ambiguous_combined_option", self.error(
            "Trong phân hệ cho vay, các yếu tố nào được sử dụng để quản lý khoản vay?",
            ["CIF và mã hồ sơ", "Hạn mức và dư nợ gốc", "Lịch trả nợ và đối soát", "Mã hồ sơ và hạn mức"],
            [0, 1, 2], "MULTIPLE_CHOICE"))

    def test_broken_option_text_is_rejected(self):
        self.assertEqual("malformed_option", self.error(
            "Phân hệ cho vay hỗ trợ công việc nào?",
            ["Quản lý các giao: liên quan", "Kế toán", "Nhân sự", "Kho hàng"]))


GOOD = '{"q":"CIF dùng để nhận diện đối tượng nào?","o":["Khách hàng","Hồ sơ khoản vay","Lịch trả nợ","Lãi suất"],"a":0}'
LEAK = '{"q":"Mã hồ sơ nhận diện hồ sơ khoản vay cụ thể là gì?","o":["Mã hồ sơ","CIF","Hạn mức","Dư nợ"],"a":0}'
ACCEPT = '{"reason":"Nguồn xác định CIF là mã định danh khách hàng, các lựa chọn khác không đúng.","correct_answers":[0],"well_formed":true}'
REJECT = '{"reason":"Câu hỏi lộ đáp án.","correct_answers":[0],"well_formed":false}'
POINT = SimpleNamespace(score=1., payload={"document_id": "D1", "course_id": "C1",
    "page": 1, "section": "Tổng quan", "full_text": "CIF là mã định danh khách hàng. Mã hồ sơ nhận diện hồ sơ khoản vay cụ thể."})


class QuizQualityFlowTests(unittest.IsolatedAsyncioTestCase):
    async def run_quiz(self, model, single=1, **request):
        with patch("app.infrastructure.ensure_collection", AsyncMock()), \
             patch("app.infrastructure.embeddings", AsyncMock(return_value=[[1., 0.]] * 4)), \
             patch("app.infrastructure.qdrant.query_points", AsyncMock(return_value=SimpleNamespace(points=[POINT]))), \
             patch("app.infrastructure.ollama_chat", new=model), \
             patch("app.config.QUIZ_LLM_VERIFICATION_ENABLED", True):
            return await generate_quiz(QuizGenerationRequest(course_id="C1", course_name="Cho vay",
                single_choice_count=single, multiple_choice_count=0, **request))

    async def test_bad_candidate_is_replaced_and_reviewed_before_returning(self):
        model = FakeQuizModel([LEAK, GOOD], [ACCEPT])
        result = await self.run_quiz(model)
        self.assertEqual(["CIF dùng để nhận diện đối tượng nào?"], [q.text for q in result.questions])
        self.assertEqual(1, len(model.review_calls))
        self.assertEqual(config.CHAT_MODEL, model.review_calls[0]["model"])
        candidate_json = model.review_calls[0]["prompt"].split("<candidate_questions>")[1].split("</candidate_questions>")[0]
        self.assertNotIn("correct_answers", candidate_json)

    async def test_replacements_carry_the_rejection_reasons(self):
        model = FakeQuizModel([LEAK, LEAK, GOOD], [ACCEPT])
        result = await self.run_quiz(model)
        self.assertEqual(1, len(result.questions))
        self.assertTrue(any("answer_in_question" in call["prompt"] for call in model.generation_calls[2:]))

    async def test_question_rejected_in_review_is_refilled(self):
        second = '{"q":"Mã hồ sơ dùng để nhận diện đối tượng nào?","o":["Hồ sơ khoản vay","Khách hàng","Lãi suất","Tài sản"],"a":0}'
        third = '{"q":"Hồ sơ khoản vay cụ thể được nhận diện bằng mã nào?","o":["Mã hồ sơ","Mã chi nhánh","Mã sản phẩm","Mã giao dịch viên"],"a":0}'
        model = FakeQuizModel([GOOD, second, third], [REJECT, ACCEPT, ACCEPT])
        result = await self.run_quiz(model, single=2)
        self.assertEqual(2, len(result.questions))
        self.assertEqual([], result.warnings)
        self.assertEqual(3, len(model.review_calls))

    async def test_existing_questions_are_not_generated_again(self):
        model = FakeQuizModel([GOOD], [ACCEPT])
        result = await self.run_quiz(model, existing_questions=["CIF được dùng để nhận diện đối tượng nào?"])
        self.assertEqual([], result.questions)
        self.assertEqual([], model.review_calls)
        self.assertIn("CIF được dùng để nhận diện đối tượng nào?", model.generation_calls[0]["prompt"])

    async def test_independent_review_rejects_two_synonymous_correct_options(self):
        model = FakeQuizModel(
            ['{"q":"Mã định danh khách hàng được gọi là gì?","o":["CIF","Mã CIF","Hạn mức","Dư nợ"],"a":0}'],
            ['{"reason":"CIF và Mã CIF đồng nghĩa nên có hai đáp án đúng.","correct_answers":[0,1],"well_formed":true}'],
        )
        result = await self.run_quiz(model)
        self.assertEqual([], result.questions)
        self.assertIn("0/1", result.warnings[0])

    async def test_reviews_do_not_wait_behind_every_generation(self):
        second = '{"q":"Mã hồ sơ dùng để nhận diện đối tượng nào?","o":["Hồ sơ khoản vay","Khách hàng","Lãi suất","Tài sản"],"a":0}'
        third = '{"q":"Hồ sơ khoản vay cụ thể được nhận diện bằng mã nào?","o":["Mã hồ sơ","Mã chi nhánh","Mã sản phẩm","Mã giao dịch viên"],"a":0}'
        fake = FakeQuizModel([GOOD, second, third], [ACCEPT] * 3)
        order = []

        async def model(name, messages, *args, **kwargs):
            order.append("review" if messages[0]["content"].startswith(REVIEW_SYSTEM_PREFIX) else "generate")
            return await fake(name, messages, *args, **kwargs)

        with patch("app.config.QUIZ_GENERATION_CONCURRENCY", 2):
            await self.run_quiz(model, single=3)
        self.assertEqual("review", order[2])

    async def test_generation_window_is_shared_between_question_types(self):
        model = FakeQuizModel([LEAK] * 12)
        with patch("app.config.QUIZ_GENERATION_CONCURRENCY", 2), \
             patch("app.infrastructure.ensure_collection", AsyncMock()), \
             patch("app.infrastructure.embeddings", AsyncMock(return_value=[[1., 0.]] * 4)), \
             patch("app.infrastructure.qdrant.query_points", AsyncMock(return_value=SimpleNamespace(points=[POINT]))), \
             patch("app.infrastructure.ollama_chat", new=model):
            await generate_quiz(QuizGenerationRequest(course_id="C1", course_name="Cho vay",
                single_choice_count=2, multiple_choice_count=2))
        first_two = " ".join(call["prompt"] for call in model.generation_calls[:2])
        self.assertIn("Tạo SINGLE_CHOICE", first_two)
        self.assertIn("Tạo MULTIPLE_CHOICE", first_two)

    async def test_concurrent_jobs_take_turns_on_the_model_queue(self):
        order = []

        async def model(name, messages, *args, **kwargs):
            order.append("B" if "Khóa học: Khóa B" in messages[1]["content"] else "A")
            await asyncio.sleep(0)
            return LEAK

        def job(name):
            return generate_quiz(QuizGenerationRequest(course_id="C1", course_name=name,
                single_choice_count=10, multiple_choice_count=0))

        with patch("app.config.QUIZ_GENERATION_CONCURRENCY", 2), \
             patch("app.infrastructure.quiz_generation_semaphore", asyncio.Semaphore(1)), \
             patch("app.infrastructure.ensure_collection", AsyncMock()), \
             patch("app.infrastructure.embeddings", AsyncMock(return_value=[[1., 0.]] * 4)), \
             patch("app.infrastructure.qdrant.query_points", AsyncMock(return_value=SimpleNamespace(points=[POINT]))), \
             patch("app.infrastructure.ollama_chat", new=model):
            await asyncio.gather(job("Khóa A"), job("Khóa B"))
        self.assertIn("A", order[:4])
        self.assertIn("B", order[:4])

    async def test_candidate_budget_bounds_model_calls(self):
        model = FakeQuizModel([LEAK] * 20)
        with patch("app.config.QUIZ_CANDIDATE_BUDGET_FACTOR", 3):
            result = await self.run_quiz(model, single=2)
        self.assertEqual([], result.questions)
        self.assertEqual(6, len(model.generation_calls))
