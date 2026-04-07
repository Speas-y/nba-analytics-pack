#!/usr/bin/env python3
"""维护 docs/代码变更记录.txt：结构化中文说明，可选仅保留最近 30 天（北京时间）。"""
from __future__ import annotations

import argparse
import re
import sys
from datetime import date, datetime, timedelta
from pathlib import Path
from zoneinfo import ZoneInfo

ROOT = Path(__file__).resolve().parent.parent
LOG_PATH = ROOT / "docs" / "代码变更记录.txt"
TZ = ZoneInfo("Asia/Shanghai")
RETAIN_DAYS = 30

DATE_LINE = re.compile(r"^(\d{4})年(\d{1,2})月(\d{1,2})日")
SEP_DASH = "-" * 72


def cn_date_to_date(line: str) -> date | None:
    line = line.strip().replace("（北京时间）", "").strip()
    m = DATE_LINE.match(line)
    if not m:
        return None
    y, mo, d = int(m.group(1)), int(m.group(2)), int(m.group(3))
    try:
        return date(y, mo, d)
    except ValueError:
        return None


def parse_iso_date(s: str) -> date:
    y, m, d = s.strip().split("-", 2)
    return date(int(y), int(m), int(d))


def split_preamble_and_blocks(text: str) -> tuple[str, list[tuple[date | None, str]]]:
    """首段为文件头；之后每条记录以 72 横线 + 日期行 + 72 横线 开头。"""
    lines = text.splitlines(keepends=True)
    start = None
    for i in range(len(lines) - 2):
        if (
            lines[i].rstrip("\n") == SEP_DASH
            and DATE_LINE.match(lines[i + 1].strip())
            and lines[i + 2].rstrip("\n") == SEP_DASH
        ):
            start = i
            break
    if start is None:
        return text, []

    preamble = "".join(lines[:start])
    rest = lines[start:]
    blocks: list[tuple[date | None, str]] = []
    i = 0
    while i < len(rest) - 2:
        if (
            rest[i].rstrip("\n") == SEP_DASH
            and DATE_LINE.match(rest[i + 1].strip())
            and rest[i + 2].rstrip("\n") == SEP_DASH
        ):
            d = cn_date_to_date(rest[i + 1].strip())
            chunk = rest[i] + rest[i + 1] + rest[i + 2]
            i += 3
            while i < len(rest):
                if (
                    i + 2 < len(rest)
                    and rest[i].rstrip("\n") == SEP_DASH
                    and DATE_LINE.match(rest[i + 1].strip())
                    and rest[i + 2].rstrip("\n") == SEP_DASH
                ):
                    break
                chunk += rest[i]
                i += 1
            blocks.append((d, chunk))
        else:
            i += 1
    return preamble, blocks


def prune() -> int:
    if not LOG_PATH.is_file():
        return 0
    text = LOG_PATH.read_text(encoding="utf-8")
    preamble, blocks = split_preamble_and_blocks(text)
    cutoff = datetime.now(TZ).date() - timedelta(days=RETAIN_DAYS)
    kept: list[str] = []
    for d, b in blocks:
        if d is None:
            kept.append(b)
        elif d >= cutoff:
            kept.append(b)

    body = preamble
    if kept:
        if body and not body.endswith("\n\n"):
            body = body.rstrip("\n") + "\n\n"
        body += "".join(kept)
    LOG_PATH.write_text(body.rstrip() + ("\n" if body.strip() else ""), encoding="utf-8")
    return len(kept)


def add_entry(title: str, problem: str, solution: str, on_date: date | None) -> None:
    if on_date is None:
        on_date = datetime.now(TZ).date()
    date_line = f"{on_date.year}年{on_date.month}月{on_date.day}日（北京时间）"
    block = f"""{SEP_DASH}
{date_line}
{SEP_DASH}

【摘要】{title.strip()}

【问题说明】
{problem.strip()}

【处理办法】
{solution.strip()}

"""
    LOG_PATH.parent.mkdir(parents=True, exist_ok=True)
    if not LOG_PATH.is_file() or LOG_PATH.stat().st_size == 0:
        header = (
            "代码变更记录（可读版）\n"
            "说明：每条含摘要、问题说明与处理办法；日期为北京时间。\n"
            "机器可读短日志见 docs/code-change-log.jsonl。\n\n"
        )
        LOG_PATH.write_text(header + block, encoding="utf-8")
    else:
        text = LOG_PATH.read_text(encoding="utf-8")
        if not text.endswith("\n"):
            text += "\n"
        LOG_PATH.write_text(text + "\n" + block, encoding="utf-8")
    prune()


def read_text_arg(s: str | None, path: Path | None) -> str:
    if path is not None:
        return path.read_text(encoding="utf-8")
    return (s or "").strip()


def main() -> None:
    p = argparse.ArgumentParser(description="可读版代码变更记录（中文 + 30 天滚动）")
    sub = p.add_subparsers(dest="cmd", required=True)

    a = sub.add_parser("add", help="追加一条记录")
    a.add_argument("--title", "-t", required=True)
    a.add_argument("--problem", "-p", default="")
    a.add_argument("--solution", "-s", default="")
    a.add_argument("--problem-file", type=Path)
    a.add_argument("--solution-file", type=Path)
    a.add_argument("--date", help="YYYY-MM-DD，默认今天（北京时间）")

    sub.add_parser("prune", help="仅按 30 天裁剪")

    args = p.parse_args()
    try:
        if args.cmd == "prune":
            n = prune()
            print(f"prune done, {n} block(s) kept", file=sys.stderr)
        elif args.cmd == "add":
            prob = read_text_arg(args.problem, args.problem_file)
            sol = read_text_arg(args.solution, args.solution_file)
            if not prob or not sol:
                print("需要 --problem / --solution 或对应 -file", file=sys.stderr)
                sys.exit(2)
            on = parse_iso_date(args.date) if args.date else None
            add_entry(args.title, prob, sol, on)
            print(f"appended -> {LOG_PATH}", file=sys.stderr)
    except OSError as e:
        print(e, file=sys.stderr)
        sys.exit(1)


if __name__ == "__main__":
    main()
