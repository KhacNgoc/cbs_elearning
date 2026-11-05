"""Fake Ollama for quiz tests.

Quiz candidates run as a concurrent pipeline, so generation and review calls
interleave; the fake answers each role from its own queue instead of relying on
call order.
"""

REVIEW_SYSTEM_PREFIX = "Bạn kiểm định"


class FakeQuizModel:
    def __init__(self, generations=(), reviews=()):
        self.generations = list(generations)
        self.reviews = list(reviews)
        self.generation_calls = []
        self.review_calls = []

    async def __call__(self, model, messages, *args, **kwargs):
        review = messages[0]["content"].startswith(REVIEW_SYSTEM_PREFIX)
        (self.review_calls if review else self.generation_calls).append(
            {"model": model, "prompt": messages[1]["content"], "options": kwargs.get("options", {})}
        )
        queue = self.reviews if review else self.generations
        if not queue:
            raise RuntimeError("fake model has no more responses")
        item = queue.pop(0)
        if isinstance(item, BaseException):
            raise item
        return item
