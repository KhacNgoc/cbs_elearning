"""Document extraction and vector ingestion."""

import uuid
from io import BytesIO

from docx import Document as DocxDocument
from docx.document import Document as DocxDocumentType
from docx.oxml.table import CT_Tbl
from docx.oxml.text.paragraph import CT_P
from docx.table import Table
from docx.text.paragraph import Paragraph
from fastapi import HTTPException, UploadFile
from pypdf import PdfReader
from qdrant_client import models

from app import config, infrastructure
from app.utils import split_text


def iter_docx_blocks(document: DocxDocumentType):
    for child in document.element.body.iterchildren():
        if isinstance(child, CT_P):
            yield Paragraph(child, document)
        elif isinstance(child, CT_Tbl):
            yield Table(child, document)


def extract_pages(content: bytes, filename: str) -> list[tuple[int, str, str]]:
    suffix = filename.lower().rsplit(".", 1)[-1]
    if suffix == "pdf":
        reader = PdfReader(BytesIO(content))
        return [
            (index + 1, f"Trang {index + 1}", page.extract_text() or "")
            for index, page in enumerate(reader.pages)
        ]
    if suffix == "docx":
        document = DocxDocument(BytesIO(content))
        sections: list[tuple[int, str, str]] = []
        heading = "Nội dung tài liệu"
        paragraphs: list[str] = []
        for block in iter_docx_blocks(document):
            if isinstance(block, Paragraph):
                text = block.text.strip()
                if not text:
                    continue
                if block.style and block.style.name.lower().startswith("heading"):
                    if paragraphs:
                        sections.append((1, heading, "\n".join(paragraphs)))
                    heading = text
                    paragraphs = []
                else:
                    paragraphs.append(text)
            else:
                for row in block.rows:
                    cells = [cell.text.strip() for cell in row.cells if cell.text.strip()]
                    if cells:
                        paragraphs.append(" | ".join(cells))
        if paragraphs:
            sections.append((1, heading, "\n".join(paragraphs)))
        return sections
    raise HTTPException(415, "Only PDF and DOCX files are supported")


async def ingest_document(
    file: UploadFile, document_id: str, course_id: str
) -> dict:
    content = await file.read()
    pages = extract_pages(content, file.filename or "document")
    chunks = [
        (page, section, chunk)
        for page, section, text in pages
        for chunk in split_text(text)
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
            [f"{section}\n{full_text}" for _, section, full_text in chunk_batch],
            "search_document: ",
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
                            "page": page,
                            "section": section,
                            "excerpt": full_text[:config.EXCERPT_LENGTH],
                            "full_text": full_text,
                        },
                    )
                    for (page, section, full_text), vector in zip(chunk_batch, vectors)
                ]
            ),
        )
    return {
        "document_id": document_id,
        "chunks_indexed": len(chunks),
        "pages": len(pages),
    }
