import sys
import unittest
from pathlib import Path
from types import SimpleNamespace

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "eval"))

import retrieval_eval as ev  # noqa: E402


CIF, GL = "course-cif", "course-gl"


def scored(section, course, score, gate=None, text="nội dung"):
    payload = {"document_id": course, "course_id": course, "file_name": "a.docx", "page": 1,
               "section": section, "chunk_index": 0, "full_text": text}
    return ev.Scored(SimpleNamespace(score=score, payload=payload), score if gate is None else gate, score)


class LabelTests(unittest.TestCase):
    def test_label_does_not_match_a_longer_number(self):
        section = "CIF-20. Câu hỏi kiểm tra và đáp án › Câu 10"
        self.assertTrue(ev.label_matches("CIF-20 › Câu 10", section))
        self.assertFalse(ev.label_matches("CIF-20 › Câu 1", section))

    def test_chapter_label_does_not_match_a_subsection(self):
        self.assertTrue(ev.label_matches("CIF-09", "CIF-09. Trạng thái và vòng đời CIF"))
        self.assertFalse(ev.label_matches("CIF-09", "CIF-09. Trạng thái và vòng đời CIF › 9.1. Điều kiện đóng CIF"))

    def test_invalid_labels_are_reported(self):
        questions = [{"id": "x", "course": "CIF", "expected": ["CIF-99 › Câu 1"]}]
        self.assertEqual(len(ev.validate_labels(questions, {"CIF": {"CIF-20. A › Câu 1"}})), 1)

    def test_eval_set_keeps_the_known_failing_case(self):
        questions = ev.load_questions()
        case = next(q for q in questions if q["question"] == "Khi nào phải KYC lại?")
        self.assertIn("CIF-20 › Câu 5", case["expected"])
        self.assertGreaterEqual(sum(1 for q in questions if q["expected"]), 40)
        self.assertTrue(any(q["kind"] == "out_of_scope" and not q["expected"] for q in questions))


class SafetyTests(unittest.TestCase):
    def test_writes_to_the_live_collection_are_refused(self):
        with self.assertRaises(ValueError):
            ev.require_eval_collection(ev.LIVE_COLLECTION)
        self.assertEqual(ev.require_eval_collection("eval_x"), "eval_x")

    def test_every_new_embedder_uses_a_separate_collection(self):
        for spec in ev.EMBEDDERS.values():
            ev.require_eval_collection(spec.collection)


class MetricTests(unittest.TestCase):
    def test_mcnemar_is_exact_and_symmetric(self):
        self.assertEqual(ev.mcnemar_exact(0, 0), 1.0)
        self.assertAlmostEqual(ev.mcnemar_exact(6, 0), 2 / 64)
        self.assertEqual(ev.mcnemar_exact(6, 2), ev.mcnemar_exact(2, 6))

    def test_threshold_is_the_most_permissive_that_meets_the_refusal_target(self):
        gates = [0.3, 0.4, 0.5, 0.6]
        self.assertEqual(sum(g < ev.choose_threshold(gates, 0.5) for g in gates), 2)
        self.assertEqual(ev.choose_threshold(gates, 0.0), float("-inf"))

    def test_folds_are_stable(self):
        self.assertEqual(ev.assign_fold("CIF-001"), ev.assign_fold("CIF-001"))
        self.assertLess(ev.assign_fold("GL-012"), ev.FOLDS)


class HarnessParityTests(unittest.IsolatedAsyncioTestCase):
    """A custom scorer must see the same selection logic as the production path."""

    def setUp(self):
        self.request = ev.build_request(
            {"question": "Câu hỏi về nội dung?", "course": "CIF"}, {"CIF": CIF, "GL": GL}, "focus"
        )

    async def test_custom_path_selects_like_production_when_scores_match(self):
        # Focus boost must lift the CIF chunk over the slightly better GL chunk.
        items = [scored("GL A", GL, 0.80), scored("CIF A", CIF, 0.78), scored("CIF B", CIF, 0.40)]
        production = ev.EvalConfig("p", live_collection=True, threshold=0.55)
        custom = ev.EvalConfig("c", threshold=0.55, reranker="unused")

        def relevance(item):
            payload = item.point.payload
            return ev.relevance_score(
                self.request.question, f"{payload['section']} {payload['full_text']}", item.point.score
            )

        same_scores = [
            ev.Scored(SimpleNamespace(score=relevance(i), payload=i.point.payload), relevance(i), relevance(i))
            for i in items
        ]
        _, production_trace, _ = await ev.call_answer_chat(production, self.request, 0.55, items, llm=False)
        _, custom_trace, _ = await ev.call_answer_chat(custom, self.request, 0.55, same_scores, llm=False)
        self.assertEqual(production_trace.sent, custom_trace.sent)
        self.assertEqual(custom_trace.sent[0], "CIF A")

    async def test_gate_can_refuse_without_calling_the_model(self):
        custom = ev.EvalConfig("c", live_collection=True, reranker="unused")
        response, trace, _ = await ev.call_answer_chat(
            custom, self.request, 0.9, [scored("CIF A", CIF, 0.5)], llm=False
        )
        self.assertEqual(trace.sent, [])
        self.assertFalse(response.grounded)

    async def test_top_k_limits_what_is_sent(self):
        custom = ev.EvalConfig("c", live_collection=True, top_k=1, reranker="unused")
        _, trace, _ = await ev.call_answer_chat(
            custom, self.request, 0.1, [scored("CIF A", CIF, 0.9), scored("CIF B", CIF, 0.8)], llm=False
        )
        self.assertEqual(trace.sent, ["CIF A"])


if __name__ == "__main__":
    unittest.main()
