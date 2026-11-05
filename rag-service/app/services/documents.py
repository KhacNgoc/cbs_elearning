"""Document extraction and vector ingestion."""

import uuid
from io import BytesIO

from docx import Document as DocxDocument
from fastapi import HTTPException, UploadFile
from pypdf import PdfReader
from qdrant_client import models

from app import config, infrastructure
from app.section_outline import docx_sections, heading_page_ranges, pdf_page_headings
from app.utils import split_text


def extract_pages(content: bytes, filename: str) -> list[dict]:
    """Return text units in document order.

    Each unit has ``page``, ``section`` (the filter key: "Trang n" for PDF, the
    heading path for Word), ``text``, ``heading`` (heading path shown to users)
    and ``heading_starts`` (PDF headings that begin on that page).
    """
    suffix = filename.lower().rsplit(".", 1)[-1]
    if suffix == "pdf":
        reader = PdfReader(BytesIO(content))
        texts = [page.extract_text() or "" for page in reader.pages]
        headings = pdf_page_headings(reader, texts)
        return [
            {"page": index + 1, "section": f"Trang {index + 1}", "text": text,
             "heading": headings[index]["heading"], "heading_starts": headings[index]["starts"]}
            for index, text in enumerate(texts)
        ]
    if suffix == "docx":
        document = DocxDocument(BytesIO(content))
        return [
            {"page": 1, "section": section, "text": text, "heading": section, "heading_starts": []}
            for section, text in docx_sections(document)
        ]
    raise HTTPException(415, "Only PDF and DOCX files are supported")


async def ingest_document(
    file: UploadFile, document_id: str, course_id: str
) -> dict:
    content = await file.read()
    pages = extract_pages(content, file.filename or "document")
    # Keep document order so the outline lists sections and excerpts as written.
    chunks = [
        (unit, chunk, section_order, chunk_index)
        for section_order, unit in enumerate(pages)
        for chunk_index, chunk in enumerate(split_text(unit["text"]))
    ]
    if not chunks:
        raise HTTPException(400, "No extractable text was found in the document")

    await infrastructure.ensure_collection()
    await infrastructure.qdrant.delete(
        config.COLLECTION,
        models.FilterSelector(
            filter=models.Filter(
                must=[
                    models.FieldCondition(
                        key="document_id",
                        match=models.MatchValue(value=document_id),
                    )
                ]
            )
        ),
    )
    for start in range(0, len(chunks), config.EMBEDDING_BATCH_SIZE):
        chunk_batch = chunks[start:start + config.EMBEDDING_BATCH_SIZE]
        vectors = await infrastructure.embeddings(
            [f"{unit['heading'] or unit['section']}\n{full_text}" for unit, full_text, _, _ in chunk_batch],
            config.EMBEDDING_DOCUMENT_PREFIX,
        )
        await infrastructure.qdrant.upsert(
            config.COLLECTION,
            models.PointsList(
                points=[
                    models.PointStruct(
                        id=str(uuid.uuid4()),
                        vector=vector,
                        payload={
                            "document_id": document_id,
                            "course_id": course_id,
                            "file_name": file.filename,
                            "page": unit["page"],
                            "section": unit["section"],
                            "heading": unit["heading"],
                            "heading_starts": unit["heading_starts"],
                            "section_order": section_order,
                            "chunk_index": chunk_index,
                            "excerpt": full_text[:config.EXCERPT_LENGTH],
                            "full_text": full_text,
                        },
                    )
                    for (unit, full_text, section_order, chunk_index), vector
                    in zip(chunk_batch, vectors)
                ]
            ),
        )
    return {
        "document_id": document_id,
        "chunks_indexed": len(chunks),
        "pages": len(pages),
    }


async def delete_document(document_id: str, course_id: str) -> None:
    """Delete only this document's vectors, and wait until deletion is applied."""
    if not await infrastructure.qdrant.collection_exists(config.COLLECTION):
        return
    await infrastructure.qdrant.delete(
        collection_name=config.COLLECTION,
        points_selector=models.FilterSelector(filter=models.Filter(must=[
            models.FieldCondition(key="course_id", match=models.MatchValue(value=course_id)),
            models.FieldCondition(key="document_id", match=models.MatchValue(value=document_id)),
        ])),
        wait=True,
    )


async def document_outline(document_id: str, course_id: str) -> dict:
    """Read existing chunks for source selection, without generating embeddings."""
    if not await infrastructure.qdrant.collection_exists(config.COLLECTION):
        raise HTTPException(404, "Document has no indexed content")
    source_filter = models.Filter(must=[
        models.FieldCondition(key="course_id", match=models.MatchValue(value=course_id)),
        models.FieldCondition(key="document_id", match=models.MatchValue(value=document_id)),
    ])
    # Chunks indexed before section_order/chunk_index existed sort last, by name.
    unordered = float("inf")
    pages, section_orders, excerpts, heading_starts = set(), {}, {}, {}
    offset = None
    while True:
        points, offset = await infrastructure.qdrant.scroll(
            collection_name=config.COLLECTION, scroll_filter=source_filter,
            offset=offset, limit=256, with_vectors=False,
            with_payload=["page", "section", "excerpt", "heading", "heading_starts",
                          "section_order", "chunk_index"],
        )
        for point in points:
            payload = point.payload or {}
            page = int(payload.get("page", 1))
            section = str(payload.get("section", ""))
            order = payload.get("section_order")
            order = unordered if order is None else int(order)
            chunk_index = payload.get("chunk_index")
            chunk_index = unordered if chunk_index is None else int(chunk_index)
            pages.add(page)
            if section:
                section_orders[section] = min(order, section_orders.get(section, unordered))
            if payload.get("heading_starts"):
                heading_starts[page] = payload["heading_starts"]
            key = (page, section)
            # The first chunk of a section is where its text actually starts.
            if key not in excerpts or chunk_index < excerpts[key][0]:
                excerpts[key] = (chunk_index, order, {
                    "page": page, "section": section,
                    "heading": str(payload.get("heading") or ""),
                    "excerpt": str(payload.get("excerpt", ""))[:500],
                })
        if offset is None:
            break
    if not pages:
        raise HTTPException(404, "Document has no indexed content")
    ordered_excerpts = sorted(
        excerpts.values(), key=lambda item: (item[1], item[2]["page"], item[2]["section"])
    )
    return {"documentId": document_id, "pages": sorted(pages),
            "sections": sorted(section_orders, key=lambda name: (section_orders[name], name)),
            "excerpts": [excerpt for _, _, excerpt in ordered_excerpts],
            "headings": heading_page_ranges(heading_starts, max(pages))}
