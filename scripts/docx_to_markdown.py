from __future__ import annotations

from pathlib import Path

from docx import Document
from docx.document import Document as DocumentType
from docx.oxml.table import CT_Tbl
from docx.oxml.text.paragraph import CT_P
from docx.table import Table
from docx.text.paragraph import Paragraph


ROOT = Path(__file__).resolve().parents[1]
DOCX_PATH = ROOT / "docs" / "半句项目概述与技术文档_详细版.docx"
OUT_PATH = ROOT / "docs" / "半句项目概述与技术文档_详细版.md"


def iter_block_items(parent: DocumentType):
    body = parent.element.body
    for child in body.iterchildren():
        if isinstance(child, CT_P):
            yield Paragraph(child, parent)
        elif isinstance(child, CT_Tbl):
            yield Table(child, parent)


def clean(text: str) -> str:
    return " ".join(text.replace("\u00a0", " ").split())


def escape_cell(text: str) -> str:
    return clean(text).replace("|", "\\|")


def table_to_markdown(table: Table) -> list[str]:
    rows: list[list[str]] = []
    for row in table.rows:
        rows.append([escape_cell(cell.text) for cell in row.cells])
    if not rows:
        return []
    if len(rows) == 1 and len(rows[0]) == 1:
        raw = table.cell(0, 0).text.strip()
        parts = [clean(line) for line in raw.splitlines() if clean(line)]
        if not parts:
            return []
        if len(parts) == 1:
            return [f"> {parts[0]}"]
        return [f"> **{parts[0]}**", ">", f"> {parts[1]}"]
    width = max(len(row) for row in rows)
    rows = [row + [""] * (width - len(row)) for row in rows]
    lines = []
    lines.append("| " + " | ".join(rows[0]) + " |")
    lines.append("| " + " | ".join(["---"] * width) + " |")
    for row in rows[1:]:
        lines.append("| " + " | ".join(row) + " |")
    return lines


def paragraph_to_markdown(paragraph: Paragraph) -> list[str]:
    text = clean(paragraph.text)
    if not text:
        return []
    style = paragraph.style.name if paragraph.style is not None else ""
    if style == "Heading 1":
        return [f"## {text}"]
    if style == "Heading 2":
        return [f"### {text}"]
    if style == "Heading 3":
        return [f"#### {text}"]
    if style.startswith("List Bullet"):
        return [f"- {text}"]
    if style.startswith("List Number"):
        return [f"1. {text}"]
    return [text]


def convert() -> None:
    doc = Document(str(DOCX_PATH))
    lines: list[str] = [
        "# 半句：基于短剧剧情理解的即时互动激发系统",
        "",
        "> 项目概述、技术文档与总结自评。本文由同名 Word 文件转换整理，适合 GitHub、比赛平台或在线文档阅读。",
        "",
    ]
    skip_cover_text = {
        "半句：基于短剧剧情理解的即时互动激发系统",
        "项目概述、技术文档与总结自评",
    }
    for block in iter_block_items(doc):
        if isinstance(block, Paragraph):
            if clean(block.text) in skip_cover_text:
                continue
            converted = paragraph_to_markdown(block)
        else:
            converted = table_to_markdown(block)
        if converted:
            lines.extend(converted)
            lines.append("")
    OUT_PATH.write_text("\n".join(lines).rstrip() + "\n", encoding="utf-8")
    print(OUT_PATH)


if __name__ == "__main__":
    convert()
