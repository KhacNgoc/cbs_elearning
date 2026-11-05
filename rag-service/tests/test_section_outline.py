import unittest
from io import BytesIO
from types import SimpleNamespace
from unittest.mock import AsyncMock, patch

from docx import Document
from docx.oxml import OxmlElement
from docx.oxml.ns import qn
from pypdf import PdfReader, PdfWriter

from app.section_outline import docx_sections, heading_page_ranges, pdf_page_headings
from app.services.documents import document_outline, extract_pages, ingest_document


def outline_paragraph(document, text, level, bold=True):
    paragraph = document.add_paragraph()
    paragraph.add_run(text).bold = bold
    node = OxmlElement("w:outlineLvl")
    node.set(qn("w:val"), str(level))
    paragraph._p.get_or_add_pPr().append(node)
    return paragraph


def docx_bytes(document):
    buffer = BytesIO()
    document.save(buffer)
    return buffer.getvalue()


class WordSectionTests(unittest.TestCase):
    def test_outline_levels_on_normal_paragraphs_build_chapter_paths(self):
        # Same shape as CIF_CB.docx: Normal style with direct outline levels.
        document = Document()
        outline_paragraph(document, "TÀI LIỆU ĐÀO TẠO CIF", 0)
        document.add_paragraph("Đối tượng: nhân sự nghiệp vụ.")
        outline_paragraph(document, "CHƯƠNG 1. TỔNG QUAN", 1)
        outline_paragraph(document, "1.1. CIF là gì?", 2)
        document.add_paragraph("CIF là hồ sơ thông tin khách hàng.")
        outline_paragraph(document, "1.2. Mục đích", 2)
        document.add_paragraph("Quản lý tập trung.")
        outline_paragraph(document, "CHƯƠNG 2. CẤU TRÚC", 1)
        table = document.add_table(rows=1, cols=2)
        table.rows[0].cells[0].text, table.rows[0].cells[1].text = "Trường", "Ý nghĩa"
        self.assertEqual([
            ("TÀI LIỆU ĐÀO TẠO CIF", "Đối tượng: nhân sự nghiệp vụ."),
            ("CHƯƠNG 1. TỔNG QUAN › 1.1. CIF là gì?", "CIF là hồ sơ thông tin khách hàng."),
            ("CHƯƠNG 1. TỔNG QUAN › 1.2. Mục đích", "Quản lý tập trung."),
            ("CHƯƠNG 2. CẤU TRÚC", "Trường | Ý nghĩa"),
        ], docx_sections(document))

    def test_heading_styles_and_repeated_names_stay_distinct(self):
        document = Document()
        document.add_paragraph("Lời nói đầu.")
        document.add_heading("Cho vay", level=1)
        document.add_heading("Tổng quan", level=2)
        document.add_paragraph("A")
        document.add_heading("Tiền gửi", level=1)
        document.add_heading("Tổng quan", level=2)
        document.add_paragraph("B")
        document.add_heading("Cho vay", level=1)
        document.add_heading("Tổng quan", level=2)
        document.add_paragraph("C")
        self.assertEqual([
            "Phần mở đầu", "Cho vay › Tổng quan", "Tiền gửi › Tổng quan", "Cho vay › Tổng quan (2)",
        ], [name for name, _ in docx_sections(document)])

    def test_numbering_fallback_ignores_amounts_list_items_and_sentences(self):
        document = Document()
        document.add_paragraph("CHƯƠNG 3. QUY TRÌNH")
        document.add_paragraph("3.1. Tiếp nhận")
        document.add_paragraph("2.000.000 VND được ghi nợ.")
        document.add_paragraph("1.2 tỷ là hạn mức tối đa cho khoản vay này, áp dụng cho khách hàng cá nhân.")
        document.add_paragraph("3.2. Phê duyệt")
        document.add_paragraph("Checker phê duyệt.")
        self.assertEqual([
            ("CHƯƠNG 3. QUY TRÌNH › 3.1. Tiếp nhận",
             "2.000.000 VND được ghi nợ.\n1.2 tỷ là hạn mức tối đa cho khoản vay này, áp dụng cho khách hàng cá nhân."),
            ("CHƯƠNG 3. QUY TRÌNH › 3.2. Phê duyệt", "Checker phê duyệt."),
        ], docx_sections(document))

    def test_document_without_headings_keeps_legacy_single_section(self):
        document = Document()
        document.add_paragraph("Nội dung một.")
        document.add_paragraph("Nội dung hai.")
        units = extract_pages(docx_bytes(document), "a.docx")
        self.assertEqual(["Nội dung tài liệu"], [unit["section"] for unit in units])


class PdfHeadingTests(unittest.TestCase):
    def pdf_with_bookmarks(self):
        writer = PdfWriter()
        for _ in range(6):
            writer.add_blank_page(200, 200)
        chapter = writer.add_outline_item("CHƯƠNG 1. TỔNG QUAN", 0)
        writer.add_outline_item("1.1. CIF là gì?", 1, parent=chapter)
        writer.add_outline_item("1.2. Mục đích", 2, parent=chapter)
        writer.add_outline_item("CHƯƠNG 2. CẤU TRÚC", 4)
        buffer = BytesIO()
        writer.write(buffer)
        return PdfReader(BytesIO(buffer.getvalue()))

    def test_bookmarks_give_page_headings_and_suggested_ranges(self):
        pages = pdf_page_headings(self.pdf_with_bookmarks(), [""] * 6)
        self.assertEqual("CHƯƠNG 1. TỔNG QUAN", pages[0]["heading"])
        self.assertEqual("CHƯƠNG 1. TỔNG QUAN › 1.2. Mục đích", pages[3]["heading"])
        self.assertEqual("CHƯƠNG 2. CẤU TRÚC", pages[5]["heading"])
        ranges = heading_page_ranges({index + 1: page["starts"] for index, page in enumerate(pages) if page["starts"]}, 6)
        self.assertEqual([
            ("CHƯƠNG 1. TỔNG QUAN", 0, 1, 4),
            ("CHƯƠNG 1. TỔNG QUAN › 1.1. CIF là gì?", 1, 2, 2),
            ("CHƯƠNG 1. TỔNG QUAN › 1.2. Mục đích", 1, 3, 4),
            ("CHƯƠNG 2. CẤU TRÚC", 0, 5, 6),
        ], [(r["section"], r["level"], r["pageFrom"], r["pageTo"]) for r in ranges])

    def test_text_patterns_are_used_without_bookmarks_and_skip_table_of_contents(self):
        reader = SimpleNamespace(outline=[])
        texts = [
            "MỤC LỤC\n1.1. CIF là gì? ........ 2\nCHƯƠNG 1. TỔNG QUAN ..... 2",
            "CHƯƠNG 1. TỔNG QUAN\n1.1. CIF là gì?\nCIF là hồ sơ khách hàng.",
            "Số dư 2.000.000 VND\n1.2. Mục đích\nQuản lý tập trung.",
        ]
        pages = pdf_page_headings(reader, texts)
        self.assertEqual(["", "CHƯƠNG 1. TỔNG QUAN", "CHƯƠNG 1. TỔNG QUAN › 1.2. Mục đích"],
                         [page["heading"] for page in pages])
        self.assertEqual([], pages[0]["starts"])

    def test_pdf_units_keep_page_sections_for_page_range_filters(self):
        writer = PdfWriter()
        writer.add_blank_page(200, 200)
        writer.add_outline_item("CHƯƠNG 1", 0)
        buffer = BytesIO()
        writer.write(buffer)
        units = extract_pages(buffer.getvalue(), "a.pdf")
        self.assertEqual("Trang 1", units[0]["section"])
        self.assertEqual("CHƯƠNG 1", units[0]["heading"])


class OutlineOrderTests(unittest.IsolatedAsyncioTestCase):
    async def test_ingest_stores_order_and_heading_metadata(self):
        document = Document()
        outline_paragraph(document, "CHƯƠNG 1", 0)
        document.add_paragraph("x" * 2500)
        outline_paragraph(document, "CHƯƠNG 2", 0)
        document.add_paragraph("Ngắn.")
        upload = SimpleNamespace(filename="a.docx", read=AsyncMock(return_value=docx_bytes(document)))
        upsert = AsyncMock()
        with patch("app.infrastructure.ensure_collection", AsyncMock()), \
             patch("app.infrastructure.qdrant.delete", AsyncMock()), \
             patch("app.infrastructure.qdrant.upsert", upsert), \
             patch("app.infrastructure.embeddings", AsyncMock(side_effect=lambda texts, *_: [[1., 0.]] * len(texts))):
            await ingest_document(upload, "D1", "C1")
        payloads = [point.payload for call in upsert.await_args_list for point in call.args[1].points]
        self.assertEqual([("CHƯƠNG 1", 0, 0), ("CHƯƠNG 1", 0, 1), ("CHƯƠNG 1", 0, 2), ("CHƯƠNG 2", 1, 0)],
                         [(p["section"], p["section_order"], p["chunk_index"]) for p in payloads])

    async def test_outline_lists_sections_in_document_order_with_first_chunk_excerpt(self):
        def point(section, order, chunk, excerpt, page=1):
            return SimpleNamespace(payload={"page": page, "section": section, "excerpt": excerpt,
                                            "section_order": order, "chunk_index": chunk, "heading": section})
        scroll = AsyncMock(return_value=([
            point("B › 2.1", 1, 1, "giữa mục"), point("A", 0, 0, "đầu A"),
            point("B › 2.1", 1, 0, "đầu mục"), point("Cũ", None, None, "chỉ mục cũ"),
        ], None))
        with patch("app.infrastructure.qdrant.collection_exists", new=AsyncMock(return_value=True)), \
             patch("app.infrastructure.qdrant.scroll", new=scroll):
            outline = await document_outline("D1", "C1")
        self.assertEqual(["A", "B › 2.1", "Cũ"], outline["sections"])
        self.assertEqual(["đầu A", "đầu mục", "chỉ mục cũ"], [e["excerpt"] for e in outline["excerpts"]])
        self.assertEqual([], outline["headings"])

    async def test_outline_returns_pdf_heading_ranges(self):
        starts = [{"path": "CHƯƠNG 1", "level": 0}]
        scroll = AsyncMock(return_value=([
            SimpleNamespace(payload={"page": 1, "section": "Trang 1", "excerpt": "a", "heading": "CHƯƠNG 1",
                                     "heading_starts": starts, "section_order": 0, "chunk_index": 0}),
            SimpleNamespace(payload={"page": 3, "section": "Trang 3", "excerpt": "b", "heading": "CHƯƠNG 1",
                                     "heading_starts": [], "section_order": 2, "chunk_index": 0}),
        ], None))
        with patch("app.infrastructure.qdrant.collection_exists", new=AsyncMock(return_value=True)), \
             patch("app.infrastructure.qdrant.scroll", new=scroll):
            outline = await document_outline("D1", "C1")
        self.assertEqual([{"section": "CHƯƠNG 1", "level": 0, "pageFrom": 1, "pageTo": 3}], outline["headings"])
        self.assertEqual("CHƯƠNG 1", outline["excerpts"][0]["heading"])


if __name__ == "__main__":
    unittest.main()
