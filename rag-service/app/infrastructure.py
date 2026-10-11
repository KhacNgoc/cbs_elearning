"""Shared clients for Ollama and Qdrant."""

import asyncio
import logging

import httpx
from fastapi import HTTPException
from qdrant_client import AsyncQdrantClient, models

from app import config


qdrant = AsyncQdrantClient(url=config.QDRANT_URL)
collection_lock = asyncio.Lock()
quiz_generation_semaphore = asyncio.Semaphore(config.QUIZ_GENERATION_CONCURRENCY)
logger = logging.getLogger("uvicorn.error")
ollama_http_client: httpx.AsyncClient | None = None


class ModelOutputTruncated(RuntimeError):
    """The model exhausted its output budget before finishing."""


def get_ollama_http_client() -> httpx.AsyncClient:
    global ollama_http_client
    if ollama_http_client is None or getattr(ollama_http_client, "is_closed", False):
        ollama_http_client = httpx.AsyncClient()
    return ollama_http_client


async def close_clients() -> None:
    if ollama_http_client is not None:
        await ollama_http_client.aclose()
    await qdrant.close()


async def ollama_chat(
    model: str,
    messages: list[dict],
    timeout: float,
    json_format: bool = False,
    json_schema: dict | None = None,
    options: dict | None = None,
    num_predict: int | None = None,
) -> str:
    request_options = {
        "temperature": 0,
        "num_ctx": config.OLLAMA_NUM_CTX,
        "num_predict": (
            num_predict if num_predict is not None else config.OLLAMA_CHAT_NUM_PREDICT
        ),
    }
    if options:
        request_options.update(options)
    response = await get_ollama_http_client().post(
        f"{config.OLLAMA_URL}/api/chat",
        timeout=timeout,
        json={
            "model": model,
            "stream": False,
            "think": False,
            "keep_alive": config.OLLAMA_KEEP_ALIVE,
            "options": request_options,
            **({"format": json_schema or "json"} if json_format or json_schema else {}),
            "messages": messages,
        },
    )
    if response.status_code != 200:
        raise RuntimeError(f"Ollama returned HTTP {response.status_code}")
    result = response.json()
    logger.info(
        "Ollama generation model=%s done_reason=%s prompt_tokens=%s output_tokens=%s "
        "load_ms=%.0f prompt_ms=%.0f generation_ms=%.0f total_ms=%.0f",
        model, result.get("done_reason"), result.get("prompt_eval_count"), result.get("eval_count"),
        result.get("load_duration", 0) / 1e6, result.get("prompt_eval_duration", 0) / 1e6,
        result.get("eval_duration", 0) / 1e6, result.get("total_duration", 0) / 1e6,
    )
    if result.get("done_reason") == "length":
        raise ModelOutputTruncated("Model output token limit reached")
    return str(result["message"]["content"])


async def embeddings(
    texts: list[str], prefix: str = "", timeout: float = 120
) -> list[list[float]]:
    if not texts:
        return []
    if config.EMBEDDING_BATCH_SIZE < 1:
        raise RuntimeError("EMBEDDING_BATCH_SIZE must be greater than zero")

    vectors: list[list[float]] = []
    client = get_ollama_http_client()
    for start in range(0, len(texts), config.EMBEDDING_BATCH_SIZE):
        batch = [prefix + text for text in texts[start:start + config.EMBEDDING_BATCH_SIZE]]
        response = await client.post(
            f"{config.OLLAMA_URL}/api/embed",
            timeout=timeout,
            json={"model": config.EMBEDDING_MODEL, "input": batch},
        )
        if response.status_code != 200:
            raise HTTPException(503, f"Embedding model is unavailable: {response.text}")
        batch_vectors = response.json()["embeddings"]
        if len(batch_vectors) != len(batch):
            raise HTTPException(502, "Embedding model returned an unexpected number of vectors")
        vectors.extend(batch_vectors)
    return vectors


async def ensure_collection(probe_timeout: float = 120) -> None:
    if await qdrant.collection_exists(config.COLLECTION):
        return
    async with collection_lock:
        if await qdrant.collection_exists(config.COLLECTION):
            return
        vector = (await embeddings(
            ["dimension probe"], "search_document: ", probe_timeout
        ))[0]
        await qdrant.create_collection(
            config.COLLECTION,
            vectors_config=models.VectorParams(
                size=len(vector), distance=models.Distance.COSINE
            ),
        )
