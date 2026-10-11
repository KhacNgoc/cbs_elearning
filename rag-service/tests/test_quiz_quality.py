import unittest
from types import SimpleNamespace
from unittest.mock import AsyncMock, patch
from app.schemas import GeneratedQuizQuestion
from app.schemas import QuizGenerationRequest
from app.quiz_utils import generated_question_validation_error, same_question_objective
from app.services.quiz import generate_quiz
from app import config


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


class QuizQualityFlowTests(unittest.IsolatedAsyncioTestCase):
    async def test_bad_candidate_is_replaced_and_reviewed_before_returning(self):
        point = SimpleNamespace(score=1., payload={"document_id": "D1", "course_id": "C1",
            "page": 1, "section": "Tổng quan", "full_text": "CIF là mã định danh khách hàng. Mã hồ sơ nhận diện hồ sơ khoản vay cụ thể."})
        model = AsyncMock(side_effect=[
            '{"q":"Mã hồ sơ nhận diện hồ sơ khoản vay cụ thể là gì?","o":["Mã hồ sơ","CIF","Hạn mức","Dư nợ"],"a":0}',
            '{"q":"CIF dùng để nhận diện đối tượng nào?","o":["Khách hàng","Hồ sơ khoản vay","Lịch trả nợ","Lãi suất"],"a":0}',
            '{"reason":"Nguồn xác định CIF là mã định danh khách hàng, các lựa chọn khác không đúng.","correct_answers":[0],"well_formed":true}',
        ])
        with patch("app.infrastructure.ensure_collection", AsyncMock()), \
             patch("app.infrastructure.embeddings", AsyncMock(return_value=[[1., 0.]] * 4)), \
             patch("app.infrastructure.qdrant.query_points", AsyncMock(return_value=SimpleNamespace(points=[point]))), \
             patch("app.infrastructure.ollama_chat", model), \
             patch("app.config.QUIZ_LLM_VERIFICATION_ENABLED", True), \
             patch("app.config.QUIZ_GENERATION_MAX_ATTEMPTS", 2):
            result = await generate_quiz(QuizGenerationRequest(course_id="C1", course_name="Cho vay",
                single_choice_count=1, multiple_choice_count=0))
        self.assertEqual(1, len(result.questions))
        self.assertEqual("CIF dùng để nhận diện đối tượng nào?", result.questions[0].text)
        self.assertEqual(config.CHAT_MODEL, model.await_args_list[-1].args[0])
        self.assertIn("answer_in_question", model.await_args_list[1].args[1][1]["content"])
        candidate_json = model.await_args_list[-1].args[1][1]["content"].split("<candidate_questions>")[1].split("</candidate_questions>")[0]
        self.assertNotIn("correct_answers", candidate_json)

    async def test_independent_review_rejects_two_synonymous_correct_options(self):
        point = SimpleNamespace(score=1., payload={"document_id": "D1", "course_id": "C1",
            "page": 1, "section": "Tổng quan", "full_text": "CIF là mã định danh khách hàng."})
        model = AsyncMock(side_effect=[
            '{"q":"Mã định danh khách hàng được gọi là gì?","o":["CIF","Mã CIF","Hạn mức","Dư nợ"],"a":0}',
            '{"reason":"CIF và Mã CIF đồng nghĩa nên có hai đáp án đúng.","correct_answers":[0,1],"well_formed":true}',
        ])
        with patch("app.infrastructure.ensure_collection", AsyncMock()), \
             patch("app.infrastructure.embeddings", AsyncMock(return_value=[[1., 0.]] * 4)), \
             patch("app.infrastructure.qdrant.query_points", AsyncMock(return_value=SimpleNamespace(points=[point]))), \
             patch("app.infrastructure.ollama_chat", model), \
             patch("app.config.QUIZ_LLM_VERIFICATION_ENABLED", True), \
             patch("app.config.QUIZ_GENERATION_MAX_ATTEMPTS", 1):
            result = await generate_quiz(QuizGenerationRequest(course_id="C1", course_name="Cho vay",
                single_choice_count=1, multiple_choice_count=0))
        self.assertEqual([], result.questions)
        self.assertIn("0/1", result.warnings[0])
