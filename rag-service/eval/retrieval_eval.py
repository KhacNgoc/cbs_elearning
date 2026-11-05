#!/usr/bin/env python3
"""Repeatable retrieval evaluation for the learning chatbot.

Every question goes through the real ``app.services.chat.answer_chat``, so scope,
candidate limits, the focus-course boost, the threshold, de-duplication and the
context budget are production code. A config only replaces how candidates are found
and scored. Without ``--llm`` the chat model is stubbed, which measures retrieval alone.

    python eval/retrieval_eval.py index --embed ollama:bge-m3
    python eval/retrieval_eval.py run --configs current,bge_rerank --llm

Only ``eval_*`` collections are ever written; the live collection is read-only here.
See eval/README.md for the metric definitions.
"""

import argparse
import asyncio
import hashlib
import json
import math
import os
import re
import statistics
import sys
import time
from collections import Counter
from dataclasses import dataclass, field
from pathlib import Path
from types import SimpleNamespace
from unittest.mock import AsyncMock, patch

# Same deadline as the running container (docker-compose.yml), not the code default.
os.environ.setdefault("OLLAMA_CHAT_TIMEOUT_SECONDS", "50")
os.environ.setdefault("OLLAMA_REWRITE_MODEL", "qwen3:1.7b")

EVAL_DIR = Path(__file__).resolve().parent
sys.path.insert(0, str(EVAL_DIR.parent))

import httpx  # noqa: E402
from fastapi import HTTPException  # noqa: E402
from qdrant_client import AsyncQdrantClient, models  # noqa: E402

from app import config as app_config  # noqa: E402
from app import infrastructure  # noqa: E402
from app.schemas import ChatRequest  # noqa: E402
from app.services import chat as chat_service  # noqa: E402
from app.utils import drop_navigation_results, fold_vietnamese, relevance_score  # noqa: E402

LIVE_COLLECTION = app_config.COLLECTION
EVAL_COLLECTION_PREFIX = "eval_"
PRODUCTION_THRESHOLD = app_config.RETRIEVAL_SCORE_THRESHOLD
QUESTIONS_PATH = EVAL_DIR / "questions.jsonl"
COURSE_NAMES = {"CIF": "CIF cơ bản", "GL": "GL cơ bản"}
FOLDS = 2


# ---------------------------------------------------------------- configs


@dataclass(frozen=True)
class EmbedSpec:
    backend: str  # "ollama" or "st" (sentence-transformers)
    model: str
    query_prefix: str = ""
    document_prefix: str = ""
    variant: str = ""

    @property
    def collection(self) -> str:
        return eval_collection_name(f"{self.backend}:{self.model}{self.variant}")


NOMIC = EmbedSpec("ollama", "nomic-embed-text", "search_query: ", "search_document: ")
# The configuration in use before the switch to bge-m3, kept as a comparison point.
NOMIC_THRESHOLD = 0.55
PRODUCTION_EMBED = EmbedSpec(
    "ollama", app_config.EMBEDDING_MODEL,
    app_config.EMBEDDING_QUERY_PREFIX, app_config.EMBEDDING_DOCUMENT_PREFIX,
)
EMBEDDERS = {
    "ollama:nomic-embed-text": NOMIC,
    "ollama:bge-m3": EmbedSpec("ollama", "bge-m3"),
    # What changing only OLLAMA_EMBEDDING_MODEL would do: the code still adds nomic's prefixes.
    "ollama:bge-m3+nomic-prefix": EmbedSpec(
        "ollama", "bge-m3", NOMIC.query_prefix, NOMIC.document_prefix, "_nomic_prefix"
    ),
    "st:AITeamVN/Vietnamese_Embedding": EmbedSpec("st", "AITeamVN/Vietnamese_Embedding"),
}
RERANK_BGE = "BAAI/bge-reranker-v2-m3"
RERANK_VI = "AITeamVN/Vietnamese_Reranker"
RERANK_SMALL = "cross-encoder/mmarco-mMiniLMv2-L12-H384-v1"


@dataclass(frozen=True)
class EvalConfig:
    name: str
    embed: EmbedSpec = NOMIC
    live_collection: bool = False
    top_k: int = 2
    threshold: float | None = None  # None: choose it by cross-validation.
    reranker: str | None = None
    rerank_pool: int | None = None  # None: rerank every candidate production fetched.
    hybrid: bool = False
    fold_lexical: bool = False  # Lexical coverage ignores diacritics.
    description: str = ""

    @property
    def collection(self) -> str:
        return LIVE_COLLECTION if self.live_collection else self.embed.collection

    @property
    def custom_scoring(self) -> bool:
        return bool(self.reranker or self.hybrid or self.fold_lexical)


def _configs() -> dict[str, EvalConfig]:
    bge = EMBEDDERS["ollama:bge-m3"]
    vi = EMBEDDERS["st:AITeamVN/Vietnamese_Embedding"]
    items = [
        EvalConfig("current", embed=PRODUCTION_EMBED, live_collection=True, threshold=PRODUCTION_THRESHOLD,
                   description="Whatever the service is configured to use now"),
        EvalConfig("nomic", threshold=NOMIC_THRESHOLD,
                   description="Previous production: nomic, relevance_score, threshold 0.55, top-2"),
        EvalConfig("nomic_k5", top_k=5, threshold=NOMIC_THRESHOLD, description="Previous production with top-5"),
        EvalConfig("nomic_t50", threshold=0.50, description="Previous production ranking, threshold 0.50"),
        EvalConfig("hybrid_bm25", hybrid=True, threshold=NOMIC_THRESHOLD,
                   description="nomic + BM25 fused with RRF (gate on relevance_score)"),
        EvalConfig("rerank_bge", reranker=RERANK_BGE,
                   description=f"nomic candidates, {RERANK_BGE}"),
        EvalConfig("rerank_bge_pool15", reranker=RERANK_BGE, rerank_pool=15,
                   description=f"nomic top-15 by relevance_score, {RERANK_BGE}"),
        EvalConfig("rerank_vi", reranker=RERANK_VI,
                   description=f"nomic candidates, {RERANK_VI}"),
        EvalConfig("rerank_small", reranker=RERANK_SMALL,
                   description=f"nomic candidates, {RERANK_SMALL}"),
        EvalConfig("embed_bge", embed=bge, description="bge-m3 (Ollama), relevance_score"),
        *[EvalConfig(f"embed_bge_t{int(t * 100)}", embed=bge, threshold=t,
                     description=f"bge-m3, relevance_score, fixed threshold {t}")
          for t in (0.30, 0.35, 0.40, 0.45)],
        EvalConfig("embed_bge_nomic_prefix", embed=EMBEDDERS["ollama:bge-m3+nomic-prefix"],
                   description="bge-m3 with production's nomic prefixes left in place"),
        EvalConfig("embed_bge_fold", embed=bge, fold_lexical=True,
                   description="bge-m3, relevance_score with diacritics folded"),
        *[EvalConfig(f"embed_bge_fold_t{int(t * 100)}", embed=bge, fold_lexical=True, threshold=t,
                     description=f"bge-m3, diacritics folded, fixed threshold {t}")
          for t in (0.35, 0.40, 0.45)],
        EvalConfig("embed_bge_rerank", embed=bge, reranker=RERANK_BGE,
                   description=f"bge-m3 candidates, {RERANK_BGE}"),
        EvalConfig("embed_bge_rerank_pool15", embed=bge, reranker=RERANK_BGE, rerank_pool=15,
                   description=f"bge-m3 top-15 by relevance_score, {RERANK_BGE}"),
        EvalConfig("embed_vi", embed=vi, description="AITeamVN/Vietnamese_Embedding, relevance_score"),
        EvalConfig("embed_vi_rerank", embed=vi, reranker=RERANK_VI,
                   description=f"AITeamVN embedding, {RERANK_VI}"),
    ]
    return {item.name: item for item in items}


CONFIGS = _configs()


def eval_collection_name(embedder: str) -> str:
    slug = re.sub(r"[^a-z0-9]+", "_", embedder.lower()).strip("_")
    return f"{EVAL_COLLECTION_PREFIX}{slug}"


def require_eval_collection(name: str) -> str:
    """The only writes this tool performs must pass here."""
    if name == LIVE_COLLECTION or not name.startswith(EVAL_COLLECTION_PREFIX):
        raise ValueError(f"Refusing to write to collection {name!r}")
    return name


# ---------------------------------------------------------------- labels and questions


def label_matches(label: str, section: str) -> bool:
    """Match a short label such as "CIF-20 › Câu 5" against a full section path.

    Each part must be the start of the matching path part and end on a word boundary,
    so "Câu 1" never matches "Câu 10" and "CIF-09" never matches a subsection.
    """
    label_parts = [part.strip() for part in label.split("›")]
    section_parts = [part.strip() for part in section.split("›")]
    if len(label_parts) != len(section_parts):
        return False
    for wanted, actual in zip(label_parts, section_parts):
        if not actual.startswith(wanted):
            return False
        remainder = actual[len(wanted):]
        if remainder and remainder[0].isalnum():
            return False
    return True


def load_questions(path: Path = QUESTIONS_PATH) -> list[dict]:
    return [json.loads(line) for line in path.read_text(encoding="utf-8").splitlines() if line.strip()]


def assign_fold(question_id: str) -> int:
    """Stable across runs and independent of the file's order."""
    return hashlib.sha256(question_id.encode()).digest()[0] % FOLDS


def validate_labels(questions: list[dict], course_sections: dict[str, set[str]]) -> list[str]:
    errors = []
    seen = set()
    for question in questions:
        if question["id"] in seen:
            errors.append(f"{question['id']}: duplicate id")
        seen.add(question["id"])
        for label in question["expected"]:
            found = {s for s in course_sections[question["course"]] if label_matches(label, s)}
            if len(found) != 1:
                errors.append(f"{question['id']}: label {label!r} matches {len(found)} sections")
    return errors


# ---------------------------------------------------------------- live data access


async def read_live_chunks(client: AsyncQdrantClient) -> list:
    points, offset = [], None
    while True:
        page, offset = await client.scroll(
            LIVE_COLLECTION, limit=256, offset=offset, with_payload=True, with_vectors=False
        )
        points.extend(page)
        if offset is None:
            return points


def course_keys_by_id(points: list) -> dict[str, str]:
    """Map the live course ids to CIF/GL using the indexed document's file name."""
    mapping = {}
    for point in points:
        name = str(point.payload.get("file_name", ""))
        for key in COURSE_NAMES:
            if f"_{key}_" in name:
                mapping[key] = str(point.payload["course_id"])
    return mapping


# ---------------------------------------------------------------- models


_st_models: dict[str, object] = {}
_rerankers: dict[str, object] = {}
_http: httpx.AsyncClient | None = None


def http_client() -> httpx.AsyncClient:
    global _http
    if _http is None:
        _http = httpx.AsyncClient()
    return _http


def torch_device() -> str:
    # The rag-service container has no GPU access on macOS, so CPU is the honest default.
    return os.getenv("RERANK_DEVICE", "cpu")


async def embed_texts(spec: EmbedSpec, texts: list[str], prefix: str) -> list[list[float]]:
    if spec.backend == "ollama":
        response = await http_client().post(
            f"{app_config.OLLAMA_URL}/api/embed", timeout=600,
            json={"model": spec.model, "input": [prefix + text for text in texts]},
        )
        response.raise_for_status()
        return response.json()["embeddings"]
    from sentence_transformers import SentenceTransformer

    model = _st_models.get(spec.model)
    if model is None:
        model = _st_models[spec.model] = SentenceTransformer(spec.model, device=torch_device())
    encoded = await asyncio.to_thread(
        model.encode, [prefix + text for text in texts], normalize_embeddings=True, batch_size=16
    )
    return encoded.tolist()


def get_reranker(name: str):
    reranker = _rerankers.get(name)
    if reranker is None:
        from sentence_transformers import CrossEncoder

        reranker = _rerankers[name] = CrossEncoder(name, device=torch_device(), max_length=512)
    return reranker


async def rerank_scores(name: str, question: str, documents: list[str]) -> list[float]:
    import torch

    reranker = get_reranker(name)
    scores = await asyncio.to_thread(
        reranker.predict, [(question, doc) for doc in documents],
        activation_fn=torch.nn.Sigmoid(), batch_size=16,
    )
    return [float(score) for score in scores]


# ---------------------------------------------------------------- BM25


class BM25:
    """Small in-memory BM25 over folded text, enough for a course-sized corpus."""

    def __init__(self, documents: dict[str, str], k1: float = 1.2, b: float = 0.75) -> None:
        self.k1, self.b = k1, b
        self.tokens = {key: fold_vietnamese(text).split() for key, text in documents.items()}
        self.lengths = {key: len(tokens) for key, tokens in self.tokens.items()}
        self.average = sum(self.lengths.values()) / max(1, len(self.lengths))
        self.frequency = {key: Counter(tokens) for key, tokens in self.tokens.items()}
        self.document_frequency: Counter = Counter()
        for tokens in self.tokens.values():
            self.document_frequency.update(set(tokens))

    def score(self, query: str, key: str) -> float:
        total, count = 0.0, len(self.tokens)
        for term in set(fold_vietnamese(query).split()):
            frequency = self.frequency[key].get(term, 0)
            if not frequency:
                continue
            idf = math.log(1 + (count - self.document_frequency[term] + 0.5) / (self.document_frequency[term] + 0.5))
            denominator = frequency + self.k1 * (1 - self.b + self.b * self.lengths[key] / self.average)
            total += idf * frequency * (self.k1 + 1) / denominator
        return total


def reciprocal_rank_fusion(rankings: list[list[str]], k: int = 60) -> dict[str, float]:
    fused: dict[str, float] = {}
    for ranking in rankings:
        for position, key in enumerate(ranking):
            fused[key] = fused.get(key, 0.0) + 1 / (k + position + 1)
    return fused


# ---------------------------------------------------------------- scoring


def chunk_key(payload: dict) -> str:
    return f"{payload.get('document_id')}|{payload.get('section')}|{payload.get('chunk_index')}"


def chunk_text(payload: dict) -> str:
    return f"{payload.get('heading') or payload.get('section') or ''}\n{payload.get('full_text', '')}"


def production_relevance(question: str, point) -> float:
    payload = point.payload or {}
    return relevance_score(question, f"{payload.get('section', '')} {payload.get('full_text', '')}", point.score)


FOLDED_STOP_WORDS = {fold_vietnamese(word) for word in app_config.QUERY_STOP_WORDS}


def folded_relevance(question: str, point) -> float:
    """relevance_score, except that "kyc lai" and "KYC lại" count as the same words."""
    payload = point.payload or {}
    terms = set(fold_vietnamese(question).split()) - FOLDED_STOP_WORDS
    if not terms:
        return point.score
    document = set(fold_vietnamese(f"{payload.get('section', '')} {payload.get('full_text', '')}").split())
    return point.score * (0.65 + 0.35 * len(terms & document) / len(terms))


@dataclass
class Scored:
    """A candidate with the scores needed to replay it at any threshold."""

    point: SimpleNamespace
    gate: float  # Compared to the threshold.
    rank: float  # Orders candidates.

    @property
    def payload(self) -> dict:
        return self.point.payload


async def score_candidates(cfg: EvalConfig, question: str, points: list, bm25: BM25 | None) -> list[Scored]:
    scorer = folded_relevance if cfg.fold_lexical else production_relevance
    relevance = {id(point): scorer(question, point) for point in points}
    if cfg.hybrid:
        by_relevance = sorted(points, key=lambda p: relevance[id(p)], reverse=True)
        keyed = {chunk_key(p.payload): p for p in points}
        bm25_scores = {key: bm25.score(question, key) for key in keyed}
        by_bm25 = sorted((k for k, s in bm25_scores.items() if s > 0), key=bm25_scores.get, reverse=True)
        fused = reciprocal_rank_fusion([[chunk_key(p.payload) for p in by_relevance], by_bm25])
        return [Scored(p, relevance[id(p)], fused[chunk_key(p.payload)]) for p in points]
    if cfg.reranker:
        pool = sorted(points, key=lambda p: relevance[id(p)], reverse=True)
        if cfg.rerank_pool:
            pool = pool[:cfg.rerank_pool]
        scores = await rerank_scores(cfg.reranker, question, [chunk_text(p.payload) for p in pool])
        return [Scored(p, score, score) for p, score in zip(pool, scores)]
    return [Scored(p, relevance[id(p)], relevance[id(p)]) for p in points]


# ---------------------------------------------------------------- the pipeline under test


def stub_chat_model():
    """Answer without a model so that a run measures retrieval alone."""

    async def respond(model, messages, timeout, **kwargs):
        properties = (kwargs.get("json_schema") or {}).get("properties", {})
        if "has_topic" in properties:
            return '{"has_topic":true}'
        return '{"answer":"stub","source_ids":["SOURCE_1"]}'

    return respond


@dataclass
class Trace:
    sent: list[str] = field(default_factory=list)  # Sections placed in the prompt.


def traced_model(inner, trace: Trace):
    async def wrapped(model, messages, timeout, **kwargs):
        prompt = messages[-1]["content"]
        if "<reference_documents>" in prompt:
            trace.sent = re.findall(r'<source id="SOURCE_\d+".*?section="(.*?)" score=', prompt)
        return await inner(model, messages, timeout, **kwargs)

    return wrapped


async def call_answer_chat(
    cfg: EvalConfig, request: ChatRequest, threshold: float, ranked: list[Scored], llm: bool
) -> tuple[object, Trace, float]:
    """Run production answer selection over already retrieved candidates."""
    trace = Trace()
    inner = infrastructure.ollama_chat if llm else stub_chat_model()
    if cfg.custom_scoring:
        # relevance_score is not this config's score, so hand production a list that is
        # already gated and carries the config's ranking score.
        visible = [s for s in ranked if s.gate >= threshold]
        points = [SimpleNamespace(score=s.rank, payload=s.point.payload) for s in visible]
        effective_threshold, relevance_patch = -math.inf, (lambda question, text, score: score)
    else:
        points = [s.point for s in ranked]
        effective_threshold, relevance_patch = threshold, relevance_score
    started = time.perf_counter()
    with patch.object(app_config, "RETRIEVAL_LIMIT", cfg.top_k), \
         patch.object(app_config, "RETRIEVAL_SCORE_THRESHOLD", effective_threshold), \
         patch.object(chat_service, "relevance_score", relevance_patch), \
         patch.object(infrastructure, "ensure_collection", AsyncMock()), \
         patch.object(infrastructure, "ensure_course_index", AsyncMock()), \
         patch.object(infrastructure, "embeddings", AsyncMock(return_value=[[0.0]])), \
         patch.object(infrastructure.qdrant, "query_points",
                      AsyncMock(return_value=SimpleNamespace(points=points))), \
         patch.object(infrastructure, "ollama_chat", traced_model(inner, trace)):
        try:
            response = await chat_service.answer_chat(request)
        except HTTPException as error:
            response = error
    return response, trace, (time.perf_counter() - started) * 1000


def build_request(question: dict, course_ids: dict[str, str], scope: str) -> ChatRequest:
    names = {course_ids[key]: COURSE_NAMES[key] for key in course_ids}
    focus = course_ids[question["course"]] if scope == "focus" else None
    return ChatRequest(
        question=question["question"],
        course_id=focus,
        course_name=names.get(focus) if focus else None,
        course_ids=list(names),
        course_names=names,
    )


async def retrieve(
    cfg: EvalConfig, question: dict, request: ChatRequest, client: AsyncQdrantClient, bm25: BM25
) -> tuple[list[Scored], dict]:
    """Capture production's own scope and candidate limit, then fetch from cfg's collection."""
    captured: dict = {}

    async def capture_query(**kwargs):
        captured.update(kwargs)
        return SimpleNamespace(points=[])

    with patch.object(infrastructure, "ensure_collection", AsyncMock()), \
         patch.object(infrastructure, "ensure_course_index", AsyncMock()), \
         patch.object(infrastructure, "embeddings", AsyncMock(return_value=[[0.0]])), \
         patch.object(infrastructure.qdrant, "query_points", capture_query), \
         patch.object(infrastructure, "ollama_chat", stub_chat_model()):
        await chat_service.answer_chat(request)

    timings = {}
    started = time.perf_counter()
    vector = (await embed_texts(cfg.embed, [question["question"]], cfg.embed.query_prefix))[0]
    timings["embed_ms"] = (time.perf_counter() - started) * 1000
    started = time.perf_counter()
    result = await client.query_points(
        collection_name=cfg.collection, query=vector,
        query_filter=captured.get("query_filter"), limit=captured.get("limit", 25), with_payload=True,
    )
    timings["search_ms"] = (time.perf_counter() - started) * 1000
    started = time.perf_counter()
    scored = await score_candidates(cfg, question["question"], result.points, bm25)
    timings["score_ms"] = (time.perf_counter() - started) * 1000
    timings["retrieval_ms"] = sum(timings.values())
    return scored, timings


# ---------------------------------------------------------------- metrics


def question_hit(sections: list[str], expected: list[str]) -> bool:
    return any(label_matches(label, section) for label in expected for section in sections)


def production_order(ranked: list[Scored], focus_course_id: str | None) -> list[Scored]:
    """The order answer_chat will use, including its focus-course boost."""

    def key(scored: Scored) -> float:
        focused = focus_course_id and str(scored.point.payload.get("course_id", "")) == focus_course_id
        return scored.rank * (app_config.CONTEXT_COURSE_BOOST if focused else 1.0)

    return sorted(ranked, key=key, reverse=True)


def rank_of_expected(ordered: list[Scored], expected: list[str]) -> int | None:
    for index, scored in enumerate(ordered, 1):
        if question_hit([str(scored.point.payload.get("section", ""))], expected):
            return index
    return None


def wilson_interval(hits: int, total: int, z: float = 1.96) -> tuple[float, float]:
    if not total:
        return (0.0, 0.0)
    rate = hits / total
    denominator = 1 + z * z / total
    centre = (rate + z * z / (2 * total)) / denominator
    margin = z * math.sqrt(rate * (1 - rate) / total + z * z / (4 * total * total)) / denominator
    return (max(0.0, centre - margin), min(1.0, centre + margin))


def mcnemar_exact(gained: int, lost: int) -> float:
    """Two-sided exact test on the discordant pairs of two paired hit lists."""
    total = gained + lost
    if not total:
        return 1.0
    tail = sum(math.comb(total, i) for i in range(0, min(gained, lost) + 1)) / 2 ** total
    return min(1.0, 2 * tail)


def choose_threshold(out_of_scope_gates: list[float], target_refusal: float) -> float:
    """The most permissive threshold that still refuses ``target_refusal`` of out-of-scope questions.

    Every config is held to production's refusal rate, so hit rates compare at equal risk.
    """
    if not out_of_scope_gates:
        return -math.inf
    needed = math.ceil(target_refusal * len(out_of_scope_gates))
    if needed <= 0:
        return -math.inf
    return sorted(out_of_scope_gates)[needed - 1] + 1e-6


def percentile(values: list[float], fraction: float) -> float | None:
    if not values:
        return None
    ordered = sorted(values)
    return ordered[min(len(ordered) - 1, int(fraction * len(ordered)))]


def rate(flags: list[bool]) -> float | None:
    return sum(flags) / len(flags) if flags else None


def summarise(cfg: EvalConfig, rows: list[dict], baseline: dict[str, bool] | None) -> dict:
    answerable = [r for r in rows if r["expected"]]
    paraphrase = [r for r in answerable if r["kind"] == "paraphrase"]
    oos = [r for r in rows if not r["expected"]]
    hits = sum(r["hit"] for r in answerable)
    summary = {
        "config": cfg.name,
        "description": cfg.description,
        "top_k": cfg.top_k,
        "thresholds": sorted({round(r["threshold"], 4) for r in rows}),
        "answerable": len(answerable),
        "paraphrase_count": len(paraphrase),
        "oos": len(oos),
        "hit": rate([r["hit"] for r in answerable]),
        "hit_ci95": wilson_interval(hits, len(answerable)),
        "hit_paraphrase": rate([r["hit"] for r in paraphrase]),
        "hit_ungated": rate([r["rank"] is not None and r["rank"] <= cfg.top_k for r in answerable]),
        "mrr10": statistics.fmean(
            1 / r["rank"] if r["rank"] and r["rank"] <= 10 else 0.0 for r in answerable
        ) if answerable else None,
        "in_pool": rate([r["rank"] is not None for r in answerable]),
        "refused_answerable": rate([r["refused"] for r in answerable]),
        "refused_oos": rate([r["refused"] for r in oos]),
        "retrieval_ms_p50": percentile([r["retrieval_ms"] for r in rows], 0.5),
        "retrieval_ms_p90": percentile([r["retrieval_ms"] for r in rows], 0.9),
    }
    if baseline is not None:
        gained = sum(1 for r in answerable if r["hit"] and not baseline[r["id"]])
        lost = sum(1 for r in answerable if not r["hit"] and baseline[r["id"]])
        summary.update(gained=gained, lost=lost, mcnemar_p=mcnemar_exact(gained, lost))
    if any("llm_outcome" in r for r in rows):
        summary.update(
            llm_refused_answerable=rate([r["llm_outcome"] != "answered" for r in answerable]),
            llm_cited_expected=rate([r["llm_cited_expected"] for r in answerable]),
            llm_answered_oos=rate([r["llm_outcome"] == "answered" for r in oos]),
            llm_errors=sum(r["llm_outcome"] == "error" for r in rows),
            e2e_ms_p50=percentile([r["e2e_ms"] for r in rows if r["llm_outcome"] != "error"], 0.5),
            e2e_ms_p90=percentile([r["e2e_ms"] for r in rows if r["llm_outcome"] != "error"], 0.9),
            e2e_answered_ms_p50=percentile(
                [r["e2e_ms"] for r in rows if r["llm_outcome"] == "answered"], 0.5
            ),
        )
    return summary


# ---------------------------------------------------------------- run


async def evaluate_config(
    cfg: EvalConfig, questions: list[dict], course_ids: dict[str, str], client: AsyncQdrantClient,
    bm25: BM25, scope: str, llm: bool, log, baseline_gates: dict[str, float] | None = None,
) -> list[dict]:
    # A first call loads models, so its time must not count as question latency.
    warm_up = questions[0]
    await retrieve(cfg, warm_up, build_request(warm_up, course_ids, scope), client, bm25)

    prepared = []
    for question in questions:
        request = build_request(question, course_ids, scope)
        ranked, timings = await retrieve(cfg, question, request, client, bm25)
        # answer_chat drops navigation sections before its threshold, so the metrics must too.
        visible = drop_navigation_results(ranked, question["question"])
        prepared.append({
            "question": question, "request": request, "ranked": ranked, "timings": timings,
            "ordered": production_order(visible, request.course_id),
            "top_gate": max((s.gate for s in visible), default=-math.inf),
        })

    # Each question's threshold comes from the other fold, so no question tunes itself.
    if cfg.threshold is None:
        thresholds = {}
        for fold in range(FOLDS):
            train = [
                p for p in prepared
                if assign_fold(p["question"]["id"]) != fold and not p["question"]["expected"]
            ]
            target = rate([
                baseline_gates[p["question"]["id"]] < PRODUCTION_THRESHOLD for p in train
            ]) or 0.0
            thresholds[fold] = choose_threshold([p["top_gate"] for p in train], target)
    else:
        thresholds = {fold: cfg.threshold for fold in range(FOLDS)}

    rows = []
    for index, item in enumerate(prepared, 1):
        question, ranked = item["question"], item["ranked"]
        threshold = thresholds[assign_fold(question["id"])]
        response, trace, _ = await call_answer_chat(cfg, item["request"], threshold, ranked, llm=False)
        row = {
            "id": question["id"], "question": question["question"], "kind": question["kind"],
            "expected": question["expected"], "threshold": threshold, "sent": trace.sent,
            "refused": not trace.sent,
            "hit": question_hit(trace.sent, question["expected"]),
            "rank": rank_of_expected(item["ordered"], question["expected"]),
            "top_gate": item["top_gate"], **item["timings"],
        }
        if llm:
            outcome, _, generation_ms = await call_answer_chat(cfg, item["request"], threshold, ranked, llm=True)
            if isinstance(outcome, HTTPException):
                row.update(llm_outcome="error", llm_cited_expected=False, llm_answer=str(outcome.detail))
            else:
                sources = [source.section for source in outcome.sources]
                row.update(
                    llm_outcome="answered" if outcome.grounded else "refused",
                    llm_cited_expected=outcome.grounded and question_hit(sources, question["expected"]),
                    llm_answer=outcome.answer, llm_sources=sources,
                )
            row["e2e_ms"] = row["retrieval_ms"] + generation_ms
            if index % 10 == 0:
                log(f"  {cfg.name}: {index}/{len(prepared)}")
        rows.append(row)
    return rows


def fmt_pct(value) -> str:
    return "–" if value is None else f"{value * 100:.0f}%"


def fmt_ms(value) -> str:
    return "–" if value is None else f"{value:,.0f}"


def render_table(summaries: list[dict]) -> str:
    lines = [
        "| Config | Hit (95% CI) | Paraphrase hit | Ungated hit@k | MRR@10 | Refused, answerable | Refused, out of scope | Gained / lost vs current | p | Retrieval p50 ms | Threshold |",
        "|---|---|---|---|---|---|---|---|---|---|---|",
    ]
    for s in summaries:
        low, high = s["hit_ci95"]
        change = f"+{s['gained']} / -{s['lost']}" if "gained" in s else "–"
        p_value = f"{s['mcnemar_p']:.3f}" if "mcnemar_p" in s else "–"
        thresholds = ", ".join(f"{t:.3f}" for t in s["thresholds"])
        lines.append(
            f"| `{s['config']}` | {fmt_pct(s['hit'])} ({low * 100:.0f}–{high * 100:.0f}) "
            f"| {fmt_pct(s['hit_paraphrase'])} | {fmt_pct(s['hit_ungated'])} | {s['mrr10']:.2f} "
            f"| {fmt_pct(s['refused_answerable'])} | {fmt_pct(s['refused_oos'])} | {change} | {p_value} "
            f"| {fmt_ms(s['retrieval_ms_p50'])} | {thresholds} |"
        )
    return "\n".join(lines)


def render_llm_table(summaries: list[dict]) -> str:
    lines = [
        "| Config | Refused or failed, answerable | Answered citing the expected chunk | Answered, out of scope | Errors | End-to-end p50 ms | End-to-end p90 ms |",
        "|---|---|---|---|---|---|---|",
    ]
    for s in summaries:
        if "e2e_ms_p50" not in s:
            continue
        lines.append(
            f"| `{s['config']}` | {fmt_pct(s['llm_refused_answerable'])} | {fmt_pct(s['llm_cited_expected'])} "
            f"| {fmt_pct(s['llm_answered_oos'])} | {s['llm_errors']} | {fmt_ms(s['e2e_ms_p50'])} | {fmt_ms(s['e2e_ms_p90'])} |"
        )
    return "\n".join(lines)


async def command_run(args) -> int:
    log = lambda message: print(message, file=sys.stderr, flush=True)  # noqa: E731
    names = list(CONFIGS) if args.configs == "all" else args.configs.split(",")
    unknown = [name for name in names if name not in CONFIGS]
    if unknown:
        raise SystemExit(f"Unknown configs: {unknown}. Known: {list(CONFIGS)}")
    # Everything is compared to production, and tuned configs match its refusal rate.
    names = ["current"] + [name for name in names if name != "current"]
    questions = load_questions()
    client = AsyncQdrantClient(url=app_config.QDRANT_URL)
    live = await read_live_chunks(client)
    course_ids = course_keys_by_id(live)
    sections: dict[str, set[str]] = {key: set() for key in course_ids}
    by_id = {value: key for key, value in course_ids.items()}
    for point in live:
        sections[by_id[str(point.payload["course_id"])]].add(str(point.payload.get("section", "")))
    errors = validate_labels(questions, sections)
    if errors:
        raise SystemExit("Invalid labels:\n" + "\n".join(errors))
    bm25 = BM25({chunk_key(p.payload): chunk_text(p.payload) for p in live})

    out = Path(args.out)
    out.parent.mkdir(parents=True, exist_ok=True)
    summaries, all_rows, baseline, baseline_gates = [], {}, None, None
    for name in names:
        cfg = CONFIGS[name]
        if not await client.collection_exists(cfg.collection):
            raise SystemExit(f"{cfg.collection} is missing. Run the index command for {cfg.embed.model}")
        log(f"Evaluating {name} ({len(questions)} questions, scope={args.scope}, llm={args.llm})")
        rows = await evaluate_config(cfg, questions, course_ids, client, bm25, args.scope, args.llm, log, baseline_gates)
        summary = summarise(cfg, rows, baseline)
        if name == "current":
            baseline = {r["id"]: r["hit"] for r in rows if r["expected"]}
            baseline_gates = {r["id"]: r["top_gate"] for r in rows}
        summaries.append(summary)
        all_rows[name] = rows
        # Saved after each config, so a failure late in a long run keeps earlier results.
        out.write_text(json.dumps({
            "scope": args.scope, "llm": args.llm, "chat_model": app_config.CHAT_MODEL,
            "rerank_device": torch_device(), "questions": len(questions),
            "summaries": summaries, "rows": all_rows,
        }, ensure_ascii=False, indent=1), encoding="utf-8")
        log(f"  hit={fmt_pct(summary['hit'])} refused_oos={fmt_pct(summary['refused_oos'])} "
            f"retrieval_p50={fmt_ms(summary['retrieval_ms_p50'])}ms")

    print(render_table(summaries))
    if args.llm:
        print()
        print(render_llm_table(summaries))
    return 0


async def command_index(args) -> int:
    spec = EMBEDDERS.get(args.embed)
    if spec is None:
        raise SystemExit(f"Unknown embedder {args.embed}. Known: {list(EMBEDDERS)}")
    target = require_eval_collection(spec.collection)
    client = AsyncQdrantClient(url=app_config.QDRANT_URL)
    live = await read_live_chunks(client)
    # Same text as documents.ingest_document, so only the model differs.
    texts = [f"{p.payload.get('heading') or p.payload.get('section')}\n{p.payload.get('full_text', '')}" for p in live]
    started = time.perf_counter()
    vectors: list[list[float]] = []
    for start in range(0, len(texts), app_config.EMBEDDING_BATCH_SIZE):
        vectors.extend(await embed_texts(spec, texts[start:start + app_config.EMBEDDING_BATCH_SIZE], spec.document_prefix))
    elapsed = time.perf_counter() - started
    if await client.collection_exists(target):
        await client.delete_collection(target)
    await client.create_collection(
        target, vectors_config=models.VectorParams(size=len(vectors[0]), distance=models.Distance.COSINE)
    )
    await client.upsert(target, models.PointsList(points=[
        models.PointStruct(id=point.id, vector=vector, payload=point.payload)
        for point, vector in zip(live, vectors)
    ]), wait=True)
    print(f"{target}: {len(vectors)} chunks, dimension {len(vectors[0])}, embedded in {elapsed:.1f}s")
    return 0


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    commands = parser.add_subparsers(dest="command", required=True)
    index = commands.add_parser("index", help="Build a separate eval_* collection from the live chunks")
    index.add_argument("--embed", required=True, choices=sorted(EMBEDDERS))
    run = commands.add_parser("run", help="Evaluate configs")
    run.add_argument("--configs", default="all", help=f"Comma separated, or all. Known: {', '.join(CONFIGS)}")
    run.add_argument("--scope", choices=["focus", "none"], default="focus",
                     help="focus: learner is viewing the question's course. none: no context course.")
    run.add_argument("--llm", action="store_true", help="Also run the real chat model")
    run.add_argument("--out", default=str(EVAL_DIR / "results" / "latest.json"))
    args = parser.parse_args()
    return asyncio.run(command_index(args) if args.command == "index" else command_run(args))


if __name__ == "__main__":
    raise SystemExit(main())
