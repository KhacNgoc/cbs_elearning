"""Recover the heading structure of uploaded documents.

Word: a heading is recognised from, in order, a Heading/Title style, an outline
level set on the paragraph, or an outline level inherited from its style.
Documents without any structural heading fall back to numbering patterns such
as "CHƯƠNG 2" or "2.1." on short lines. Each section is named by its heading
path ("CHƯƠNG 1. TỔNG QUAN › 1.1. CIF là gì?") so equal headings in different
chapters stay distinct.

PDF: content stays addressed by page (lesson sources select page ranges), so
headings come from the PDF bookmarks, or the same numbering patterns on text
lines, and are recorded on the page where each one starts.
"""

import re
from dataclasses import dataclass

from docx.document import Document as DocxDocumentType
from docx.oxml.ns import qn
from docx.oxml.table import CT_Tbl
from docx.oxml.text.paragraph import CT_P
from docx.table import Table
from docx.text.paragraph import Paragraph

SECTION_SEPARATOR = " › "
UNTITLED_SECTION = "Nội dung tài liệu"
INTRO_SECTION = "Phần mở đầu"
# Word stores outline levels 0-8; 9 means body text.
BODY_OUTLINE_LEVEL = 9
MAX_HEADING_LENGTH = 150
# Table-of-contents lines such as "1.1. CIF là gì? ........ 3".
TOC_ENTRY = re.compile(r"(\.{3,}|…)\s*\d+$")

HEADING_STYLE = re.compile(r"^heading\s*(\d)$", re.IGNORECASE)
CHAPTER_PATTERN = re.compile(
    r"^(chương|phần|chapter|part)\s+([0-9]+|[ivxlc]+)\b", re.IGNORECASE
)
# "2.1. Title" but not amounts such as "2.000.000 VND".
NUMBERED_PATTERN = re.compile(r"^(\d{1,2}(?:\.\d{1,2})+)\.?\s+[^\d\s]")


@dataclass
class _Block:
    text: str
    level: int | None = None


def _outline_level(element) -> int | None:
    p_pr = getattr(element, "pPr", None)
    if p_pr is None:
        return None
    node = p_pr.find(qn("w:outlineLvl"))
    if node is None:
        return None
    try:
        level = int(node.get(qn("w:val")))
    except (TypeError, ValueError):
        return None
    return level if 0 <= level < BODY_OUTLINE_LEVEL else None


def _style_level(paragraph: Paragraph) -> int | None:
    style = paragraph.style
    while style is not None:
        name = (style.name or "").strip()
        match = HEADING_STYLE.match(name)
        if match:
            return max(0, int(match.group(1)) - 1)
        if name.lower() == "title":
            return 0
        level = _outline_level(style.element)
        if level is not None:
            return level
        style = style.base_style
    return None


def structural_heading_level(paragraph: Paragraph) -> int | None:
    level = _outline_level(paragraph._p)
    if level is not None:
        return level
    return _style_level(paragraph)


def _is_list_item(paragraph: Paragraph) -> bool:
    p_pr = paragraph._p.pPr
    return p_pr is not None and p_pr.numPr is not None


def _is_bold(paragraph: Paragraph) -> bool:
    runs = [run for run in paragraph.runs if run.text.strip()]
    return bool(runs) and all(run.bold for run in runs)


def pattern_heading_level(paragraph: Paragraph, text: str) -> int | None:
    if len(text) > MAX_HEADING_LENGTH or text[-1] in ".;,":
        return None
    if CHAPTER_PATTERN.match(text):
        return 0
    numbered = NUMBERED_PATTERN.match(text)
    if numbered and not _is_list_item(paragraph) and (_is_bold(paragraph) or len(text) <= 100):
        return numbered.group(1).count(".")
    return None


def _blocks(document: DocxDocumentType) -> list[tuple[Paragraph | None, str]]:
    blocks = []
    for child in document.element.body.iterchildren():
        if isinstance(child, CT_P):
            paragraph = Paragraph(child, document)
            text = paragraph.text.strip()
            if text:
                blocks.append((paragraph, text))
        elif isinstance(child, CT_Tbl):
            for row in Table(child, document).rows:
                cells = []
                for cell in row.cells:
                    value = cell.text.strip()
                    # Merged cells repeat; keep each value once per row.
                    if value and (not cells or cells[-1] != value):
                        cells.append(value)
                if cells:
                    blocks.append((None, " | ".join(cells)))
    return blocks


def _classify(document: DocxDocumentType) -> list[_Block]:
    raw = _blocks(document)
    blocks = [
        _Block(text, structural_heading_level(paragraph) if paragraph is not None else None)
        for paragraph, text in raw
    ]
    if not any(block.level is not None for block in blocks):
        for block, (paragraph, text) in zip(blocks, raw):
            if paragraph is not None:
                block.level = pattern_heading_level(paragraph, text)
    return blocks


def _document_title_index(blocks: list[_Block]) -> int | None:
    """A lone top-level first heading is the document title, not a parent of every chapter."""
    headings = [(index, block.level) for index, block in enumerate(blocks) if block.level is not None]
    if len(headings) < 2:
        return None
    top = min(level for _, level in headings)
    first_index, first_level = headings[0]
    first_text = blocks[first_index].text
    if CHAPTER_PATTERN.match(first_text) or NUMBERED_PATTERN.match(first_text):
        return None
    if first_level == top and sum(1 for _, level in headings if level == top) == 1:
        return first_index
    return None


def docx_sections(document: DocxDocumentType) -> list[tuple[str, str]]:
    """Return ``(section_path, text)`` in document order; empty sections are skipped."""
    blocks = _classify(document)
    has_headings = any(block.level is not None for block in blocks)
    title_index = _document_title_index(blocks)

    sections: list[tuple[str, str]] = []
    used_names: dict[str, int] = {}
    stack: list[tuple[int, str]] = []
    current = INTRO_SECTION if has_headings else UNTITLED_SECTION
    paragraphs: list[str] = []

    last_path: str | None = None

    def flush() -> None:
        nonlocal last_path
        if not paragraphs:
            return
        text = "\n".join(paragraphs)
        paragraphs.clear()
        if current == last_path:
            # Same heading path repeated back to back is one section.
            name, previous = sections[-1]
            sections[-1] = (name, previous + "\n" + text)
            return
        used_names[current] = used_names.get(current, 0) + 1
        name = current if used_names[current] == 1 else f"{current} ({used_names[current]})"
        sections.append((name, text))
        last_path = current

    for index, block in enumerate(blocks):
        if block.level is None:
            paragraphs.append(block.text)
            continue
        flush()
        title = " ".join(block.text.replace("›", ">").split())
        if index == title_index:
            current = title
        else:
            while stack and stack[-1][0] >= block.level:
                stack.pop()
            stack.append((block.level, title))
            current = SECTION_SEPARATOR.join(part for _, part in stack)
        if current != last_path:
            last_path = None
    flush()
    return sections


def _text_heading_level(line: str) -> int | None:
    if not line or len(line) > MAX_HEADING_LENGTH or line[-1] in ".;," or TOC_ENTRY.search(line):
        return None
    if CHAPTER_PATTERN.match(line):
        return 0
    numbered = NUMBERED_PATTERN.match(line)
    return numbered.group(1).count(".") if numbered and len(line) <= 100 else None


def _bookmark_headings(reader) -> list[tuple[int, int, str]]:
    """Return ``(page_number, level, title)`` from PDF bookmarks, in reading order."""
    found: list[tuple[int, int, str]] = []

    def walk(items, level: int) -> None:
        for item in items:
            if isinstance(item, list):
                walk(item, level + 1)
                continue
            try:
                page_index = reader.get_destination_page_number(item)
            except Exception:  # noqa: BLE001 - broken bookmarks must not block ingestion.
                continue
            title = " ".join(str(getattr(item, "title", "") or "").split())
            if title and page_index is not None and page_index >= 0:
                found.append((page_index + 1, level, title))

    try:
        walk(reader.outline, 0)
    except Exception:  # noqa: BLE001
        return []
    return sorted(found, key=lambda item: item[0])


def _pattern_headings(page_texts: list[str]) -> list[tuple[int, int, str]]:
    found = []
    for page_number, text in enumerate(page_texts, start=1):
        for line in text.splitlines():
            line = " ".join(line.split())
            level = _text_heading_level(line)
            if level is not None:
                found.append((page_number, level, line))
    return found


def pdf_page_headings(reader, page_texts: list[str]) -> list[dict]:
    """Per page: ``{"heading": active path at the page, "starts": [{"path", "level"}]}``."""
    raw = _bookmark_headings(reader) or _pattern_headings(page_texts)
    pages = [{"heading": "", "starts": []} for _ in page_texts]
    stack: list[tuple[int, str]] = []
    cursor = 0
    for page_number in range(1, len(page_texts) + 1):
        active_before = SECTION_SEPARATOR.join(title for _, title in stack)
        first_started = ""
        while cursor < len(raw) and raw[cursor][0] <= page_number:
            _, level, title = raw[cursor]
            cursor += 1
            title = title.replace("›", ">")
            while stack and stack[-1][0] >= level:
                stack.pop()
            stack.append((level, title))
            path = SECTION_SEPARATOR.join(part for _, part in stack)
            pages[page_number - 1]["starts"].append({"path": path, "level": len(stack) - 1})
            first_started = first_started or path
        pages[page_number - 1]["heading"] = first_started or active_before
    return pages


def heading_page_ranges(starts_by_page: dict[int, list[dict]], last_page: int) -> list[dict]:
    """Turn heading start pages into suggested ``pageFrom``/``pageTo`` ranges."""
    entries = [
        (page, start["level"], start["path"])
        for page in sorted(starts_by_page)
        for start in starts_by_page[page]
    ]
    ranges = []
    for index, (page, level, path) in enumerate(entries):
        end = last_page
        for next_page, next_level, _ in entries[index + 1:]:
            if next_level <= level:
                end = max(page, next_page - 1)
                break
        ranges.append({"section": path, "level": level, "pageFrom": page, "pageTo": end})
    return ranges
