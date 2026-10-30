#!/usr/bin/env python3
"""Đưa nội dung quan trọng của file log vào phần chú thích (annotation) của lần chạy GitHub Actions.

Cách dùng:
  annotate.py <notice|error|warning> <tiêu đề> <file> [<file> ...]

- notice: ghi nguyên văn nội dung file.
- error/warning: chỉ lọc ra các dòng lỗi của Kotlin/AAPT/Gradle (kèm vài dòng ngữ cảnh); nếu không lọc được
  gì thì ghi phần cuối của log.
Chú thích hiển thị ngay trên trang lần chạy và đọc được qua API (check-runs/<id>/annotations).
"""
import os
import re
import sys

LEVEL, TITLE, FILES = sys.argv[1], sys.argv[2], sys.argv[3:]
CHUNK = 3200
MAX_ANNOTATIONS = 9

PATTERNS = [
    re.compile(r"^e: "),
    re.compile(r"^w: .*(deprecat)", re.I),
    re.compile(r"error:", re.I),
    re.compile(r"^FAILURE:"),
    re.compile(r"^\* What went wrong:"),
    re.compile(r"^> Task .* FAILED"),
    re.compile(r"Execution failed"),
    re.compile(r"Could not (resolve|find|get)"),
    re.compile(r"SDK location not found|Failed to install|licen[cs]e", re.I),
    re.compile(r"AAPT|aapt2", re.I),
    re.compile(r"^\s+.*FAILED$"),
    re.compile(r"AssertionError|Test .* FAILED|tests completed"),
]


def escape(s: str) -> str:
    return s.replace("%", "%25").replace("\r", "%0D").replace("\n", "%0A")


def escape_title(s: str) -> str:
    return escape(s).replace(":", "%3A").replace(",", "%2C")


def interesting(lines):
    keep = set()
    for i, line in enumerate(lines):
        if any(p.search(line) for p in PATTERNS):
            lo = i
            hi = min(len(lines), i + (4 if line.startswith("* What went wrong") else 1))
            for j in range(lo, hi):
                keep.add(j)
    out = []
    last = -2
    for i in sorted(keep):
        if i != last + 1 and out:
            out.append("...")
        out.append(lines[i].rstrip())
        last = i
    return out


def emit(title: str, text: str):
    chunks = [text[i:i + CHUNK] for i in range(0, len(text), CHUNK)] or [""]
    for n, chunk in enumerate(chunks[:MAX_ANNOTATIONS]):
        t = title if len(chunks) == 1 else f"{title} ({n + 1}/{min(len(chunks), MAX_ANNOTATIONS)})"
        print(f"::{LEVEL} title={escape_title(t)}::{escape(chunk)}")


for path in FILES:
    if not os.path.exists(path):
        continue
    with open(path, encoding="utf-8", errors="replace") as f:
        raw = f.read().splitlines()
    if LEVEL == "notice":
        emit(f"{TITLE}", "\n".join(raw))
        continue
    picked = interesting(raw)
    body = "\n".join(picked) if picked else "\n".join(raw[-60:])
    emit(f"{TITLE} - {os.path.basename(path)}", body)
