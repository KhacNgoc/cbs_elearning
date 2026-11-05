"""Fill the configured collection from another one using the configured embedding model.

Run this before pointing the service at a new embedding model:

    python -m app.reindex --source course_documents

The source is only read. Chunks keep their ids and payloads, so citations and document
outlines do not change.
"""

import argparse
import asyncio
import sys

from qdrant_client import models

from app import config, infrastructure


def chunk_embedding_text(payload: dict) -> str:
    """The text ingest_document embeds for a chunk."""
    return f"{payload.get('heading') or payload.get('section')}\n{payload.get('full_text', '')}"


async def read_chunks(source: str) -> list:
    points, offset = [], None
    while True:
        page, offset = await infrastructure.qdrant.scroll(
            source, limit=256, offset=offset, with_payload=True, with_vectors=False
        )
        points.extend(page)
        if offset is None:
            return points


async def reindex(source: str, overwrite: bool = False) -> int:
    target = config.COLLECTION
    client = infrastructure.qdrant
    if source == target:
        raise ValueError(f"Source and target are both {target}")
    if not await client.collection_exists(source):
        raise ValueError(f"Source collection {source} does not exist")
    if await client.collection_exists(target):
        existing = (await client.count(target, exact=True)).count
        if existing and not overwrite:
            # Re-running after the switch would bring back documents deleted since.
            raise ValueError(f"{target} already has {existing} chunks; pass --overwrite to rebuild it")
    points = await read_chunks(source)
    if not points:
        raise ValueError(f"Source collection {source} is empty")

    # Embed everything first, so a model failure leaves the target untouched.
    vectors = await infrastructure.embeddings(
        [chunk_embedding_text(point.payload or {}) for point in points],
        config.EMBEDDING_DOCUMENT_PREFIX,
    )
    if await client.collection_exists(target):
        await client.delete_collection(target)
    await client.create_collection(
        target,
        vectors_config=models.VectorParams(size=len(vectors[0]), distance=models.Distance.COSINE),
    )
    for start in range(0, len(points), config.EMBEDDING_BATCH_SIZE):
        end = start + config.EMBEDDING_BATCH_SIZE
        await client.upsert(
            target,
            models.PointsList(points=[
                models.PointStruct(id=point.id, vector=vector, payload=point.payload)
                for point, vector in zip(points[start:end], vectors[start:end])
            ]),
            wait=True,
        )
    written = (await client.count(target, exact=True)).count
    if written != len(points):
        raise RuntimeError(f"{target} has {written} chunks, expected {len(points)}")
    return written


async def run(source: str, overwrite: bool) -> int:
    try:
        written = await reindex(source, overwrite)
    except ValueError as error:
        print(error, file=sys.stderr)
        return 1
    finally:
        await infrastructure.close_clients()
    print(f"{config.COLLECTION}: {written} chunks embedded with {config.EMBEDDING_MODEL}")
    return 0


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--source", required=True, help="Collection to copy chunks from")
    parser.add_argument("--overwrite", action="store_true", help="Rebuild a target that already has chunks")
    args = parser.parse_args()
    return asyncio.run(run(args.source, args.overwrite))


if __name__ == "__main__":
    raise SystemExit(main())
