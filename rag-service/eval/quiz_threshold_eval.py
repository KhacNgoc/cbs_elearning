#!/usr/bin/env python3
"""Compare quiz source retrieval across embedding models and thresholds.

Quiz filters on the raw vector score, so its threshold differs from chat's. Each chapter
of the indexed documents stands in for a lesson, using the queries production builds. A
good threshold leaves no lesson without sources while keeping them on the lesson's chapter.

    python eval/quiz_threshold_eval.py --out eval/results/quiz_thresholds.json
"""

import argparse
import asyncio
import json
import statistics
import sys
from pathlib import Path

EVAL_DIR = Path(__file__).resolve().parent
sys.path.insert(0, str(EVAL_DIR))
sys.path.insert(0, str(EVAL_DIR.parent))

from qdrant_client import AsyncQdrantClient, models  # noqa: E402

from retrieval_eval import COURSE_NAMES, EMBEDDERS, NOMIC, course_keys_by_id, embed_texts, read_live_chunks  # noqa: E402

from app import config  # noqa: E402
from app.quiz_utils import low_value_quiz_section, quiz_retrieval_queries  # noqa: E402
from app.schemas import QuizGenerationRequest, QuizKnowledgeSource  # noqa: E402
from app.services.quiz import quiz_source_filter, quiz_source_target  # noqa: E402

SOURCES_WANTED = quiz_source_target(5)  # The default quiz of 5 questions.
CANDIDATE_LIMIT = max(config.QUIZ_RETRIEVAL_CANDIDATE_LIMIT, SOURCES_WANTED * 2)
GRID = {
    "nomic-embed-text": (NOMIC, [0.45, 0.50, 0.55, 0.60]),
    "bge-m3": (EMBEDDERS["ollama:bge-m3"], [0.30, 0.35, 0.40, 0.45, 0.50, 0.55]),
}


def chapter_of(section: str) -> str:
    return section.split("›")[0].strip()


def lessons(points: list) -> list[dict]:
    """One lesson per chapter that has content of its own or subsections."""
    keys = {value: key for key, value in course_keys_by_id(points).items()}
    found: dict[tuple, dict] = {}
    for point in points:
        payload = point.payload
        chapter = chapter_of(str(payload.get("section", "")))
        if low_value_quiz_section(chapter) or chapter in {"Phần mở đầu"}:
            continue
        found.setdefault((payload["course_id"], chapter), {
            "course_id": payload["course_id"], "document_id": payload["document_id"],
            "course_name": COURSE_NAMES[keys[payload["course_id"]]], "chapter": chapter,
        })
    return list(found.values())


async def pool_for(lesson: dict, spec, client: AsyncQdrantClient) -> list[tuple[float, str]]:
    """Best score per chunk over production's queries, highest first."""
    request = QuizGenerationRequest(
        course_id=lesson["course_id"], course_name=lesson["course_name"], lesson_name=lesson["chapter"],
        sources=[QuizKnowledgeSource(document_id=lesson["document_id"])],
    )
    vectors = await embed_texts(spec, quiz_retrieval_queries(request), spec.query_prefix)
    best: dict[str, tuple[float, str]] = {}
    for vector in vectors:
        result = await client.query_points(
            collection_name=spec.collection, query=vector,
            query_filter=quiz_source_filter(request), limit=CANDIDATE_LIMIT, with_payload=True,
        )
        for point in result.points:
            section = str(point.payload.get("section", ""))
            key = str(point.id)
            if key not in best or point.score > best[key][0]:
                best[key] = (point.score, section)
    return sorted(best.values(), reverse=True)


def summarise(pools: list[tuple[dict, list]], threshold: float) -> dict:
    sizes, shares, empty, short = [], [], 0, 0
    for lesson, pool in pools:
        passed = [item for item in pool if item[0] >= threshold]
        sizes.append(len(passed))
        empty += not passed
        short += len(passed) < SOURCES_WANTED
        top = passed[:SOURCES_WANTED]
        if top:
            shares.append(sum(chapter_of(section) == lesson["chapter"] for _, section in top) / len(top))
    return {
        "threshold": threshold, "lessons": len(pools), "no_sources": empty, "too_few": short,
        "median_pool": statistics.median(sizes), "on_chapter": statistics.fmean(shares) if shares else 0.0,
    }


async def main_async(out: Path) -> None:
    client = AsyncQdrantClient(url=config.QDRANT_URL)
    all_lessons = lessons(await read_live_chunks(client))
    results = {}
    print(f"{len(all_lessons)} lessons, {SOURCES_WANTED} sources wanted, {CANDIDATE_LIMIT} candidates per query")
    print("| Model | Threshold | No sources | Too few | Median pool | On chapter |\n|---|---|---|---|---|---|")
    for name, (spec, thresholds) in GRID.items():
        pools = [(lesson, await pool_for(lesson, spec, client)) for lesson in all_lessons]
        results[name] = [summarise(pools, threshold) for threshold in thresholds]
        for row in results[name]:
            print(f"| {name} | {row['threshold']:.2f} | {row['no_sources']} | {row['too_few']} "
                  f"| {row['median_pool']:.0f} | {row['on_chapter'] * 100:.0f}% |")
    out.write_text(json.dumps(results, indent=1), encoding="utf-8")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--out", default=str(EVAL_DIR / "results" / "quiz_thresholds.json"))
    arguments = parser.parse_args()
    asyncio.run(main_async(Path(arguments.out)))
