#!/usr/bin/env python3
"""Convierte docs/reporte-tecnico.md a HTML y lo imprime a PDF con Edge."""

import html
import re
import shutil
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent
MD = ROOT / "reporte-tecnico.md"
HTML_PATH = ROOT / "reporte-tecnico.html"
PDF_PATH = ROOT / "reporte-tecnico.pdf"
SHOT = ROOT / "evidencias" / "grafana-dashboard.png"

EDGE_CANDIDATES = [
    Path(r"C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe"),
    Path(r"C:\Program Files\Microsoft\Edge\Application\msedge.exe"),
    Path(r"C:\Program Files\Google\Chrome\Application\chrome.exe"),
]


def inline(text: str) -> str:
    escaped = html.escape(text)
    escaped = re.sub(r"`([^`]+)`", r"<code>\1</code>", escaped)
    escaped = re.sub(r"\*\*([^*]+)\*\*", r"<strong>\1</strong>", escaped)
    return escaped


def markdown_to_html(source: str) -> str:
    lines = source.splitlines()
    parts: list[str] = []
    i = 0
    while i < len(lines):
        line = lines[i]
        if line.startswith("```"):
            buffer = []
            i += 1
            while i < len(lines) and not lines[i].startswith("```"):
                buffer.append(html.escape(lines[i]))
                i += 1
            parts.append("<pre>" + "\n".join(buffer) + "</pre>")
            i += 1
            continue
        if line.startswith("|"):
            rows = []
            while i < len(lines) and lines[i].startswith("|"):
                rows.append(lines[i])
                i += 1
            data = [row for row in rows if not re.match(r"^\|\s*-+", row)]
            if data:
                parts.append("<table>")
                for index, row in enumerate(data):
                    cells = [cell.strip() for cell in row.strip().strip("|").split("|")]
                    tag = "th" if index == 0 else "td"
                    parts.append("<tr>" + "".join(f"<{tag}>{inline(cell)}</{tag}>" for cell in cells) + "</tr>")
                parts.append("</table>")
            continue
        if line.startswith("#"):
            level = len(line) - len(line.lstrip("#"))
            parts.append(f"<h{level}>{inline(line[level:].strip())}</h{level}>")
            i += 1
            continue
        if line.startswith("- "):
            parts.append("<ul>")
            while i < len(lines) and lines[i].startswith("- "):
                parts.append(f"<li>{inline(lines[i][2:].strip())}</li>")
                i += 1
            parts.append("</ul>")
            continue
        if not line.strip():
            i += 1
            continue
        paragraph = [line.strip()]
        i += 1
        while i < len(lines) and lines[i].strip() and not lines[i].startswith(("#", "-", "|", "```")):
            paragraph.append(lines[i].strip())
            i += 1
        parts.append("<p>" + inline(" ".join(paragraph)) + "</p>")
    figure = ""
    if SHOT.exists():
        figure = (
            "<figure><img src=\"evidencias/grafana-dashboard.png\" "
            "alt=\"Dashboard de Grafana con la carga del 9 de octubre de 2026\">"
            "<figcaption>Figura 1. Dashboard local después del benchmark. "
            "El SLI marca 100 % y la tasa de errores 5xx está en cero.</figcaption></figure>"
        )
    style = """
    body { font-family: Calibri, Arial, sans-serif; font-size: 11.5pt; line-height: 1.35; margin: 18mm 16mm; color: #1a1a1a; }
    h1 { font-size: 18pt; }
    h2 { font-size: 14pt; margin-top: 1.2em; page-break-after: avoid; }
    pre, code { font-family: Consolas, monospace; font-size: 9pt; }
    pre { background: #f4f4f4; padding: 8px; white-space: pre-wrap; }
    table { border-collapse: collapse; width: 100%; margin: 0.6em 0 1em; page-break-inside: avoid; }
    th, td { border: 1px solid #bbb; padding: 4px 6px; text-align: left; vertical-align: top; }
    th { background: #eee; }
    img { width: 100%; }
    figure { margin: 1em 0; page-break-inside: avoid; }
    figcaption { font-size: 10pt; color: #333; }
    """
    body = "\n".join(parts)
    body = body.replace("</h2>\n", "</h2>\n", 1)
    # Insert the figure after section 7 heading content is hard; append before section 8.
    body = body.replace("<h2>8. Benchmark</h2>", figure + "<h2>8. Benchmark</h2>")
    return f"<!DOCTYPE html><html lang=\"es\"><head><meta charset=\"utf-8\"><title>Laboratorio 2.2</title><style>{style}</style></head><body>{body}</body></html>"


def main() -> None:
    HTML_PATH.write_text(markdown_to_html(MD.read_text(encoding="utf-8")), encoding="utf-8")
    browser = next((path for path in EDGE_CANDIDATES if path.exists()), None)
    if browser is None:
        print("No se encontró Edge ni Chrome. Quedó el HTML.")
        sys.exit(1)
    if PDF_PATH.exists():
        PDF_PATH.unlink()
    url = HTML_PATH.resolve().as_uri()
    command = [
        str(browser),
        "--headless",
        "--disable-gpu",
        "--no-pdf-header-footer",
        f"--print-to-pdf={PDF_PATH}",
        url,
    ]
    completed = subprocess.run(command, capture_output=True, text=True, timeout=90)
    if not PDF_PATH.exists() or PDF_PATH.stat().st_size < 1000:
        print(completed.stdout)
        print(completed.stderr)
        sys.exit(1)
    print(f"{PDF_PATH} ({PDF_PATH.stat().st_size} bytes) via {browser.name}")


if __name__ == "__main__":
    main()
