#!/usr/bin/env python3
"""维护 docs/code-change-log.jsonl：记录提交说明，仅保留最近 30 天（UTC）。"""
from __future__ import annotations

import argparse
import json
import subprocess
import sys
from datetime import datetime, timedelta, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
LOG_PATH = ROOT / "docs" / "code-change-log.jsonl"
RETAIN_DAYS = 30


def utc_now() -> datetime:
    return datetime.now(timezone.utc)


def parse_iso_ts(s: str) -> datetime | None:
    if not s or not isinstance(s, str):
        return None
    s = s.strip()
    if s.endswith("Z"):
        s = s[:-1] + "+00:00"
    try:
        dt = datetime.fromisoformat(s)
    except ValueError:
        return None
    if dt.tzinfo is None:
        dt = dt.replace(tzinfo=timezone.utc)
    return dt


def prune() -> int:
    """删除早于 RETAIN_DAYS 的行，返回剩余条数。"""
    if not LOG_PATH.is_file():
        return 0
    cutoff = utc_now() - timedelta(days=RETAIN_DAYS)
    kept: list[str] = []
    for line in LOG_PATH.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if not line:
            continue
        try:
            obj = json.loads(line)
        except json.JSONDecodeError:
            continue
        ts = parse_iso_ts(obj.get("ts", ""))
        if ts is None:
            continue
        if ts >= cutoff:
            kept.append(line)
    LOG_PATH.write_text("\n".join(kept) + ("\n" if kept else ""), encoding="utf-8")
    return len(kept)


def record(message: str | None, commit_hash: str | None) -> None:
    """追加一条记录（默认取当前 HEAD 的说明与短 hash），并 prune。"""
    if not message or not message.strip():
        message = subprocess.check_output(
            ["git", "log", "-1", "--format=%s"], cwd=ROOT, text=True
        ).strip()
    if not commit_hash or not commit_hash.strip():
        full = subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=ROOT, text=True).strip()
        commit_hash = full[:12]
    entry = {
        "ts": utc_now().strftime("%Y-%m-%dT%H:%M:%SZ"),
        "subject": message.strip(),
        "hash": commit_hash.strip()[:12],
    }
    LOG_PATH.parent.mkdir(parents=True, exist_ok=True)
    with LOG_PATH.open("a", encoding="utf-8") as f:
        f.write(json.dumps(entry, ensure_ascii=False) + "\n")
    prune()


def seed_from_git() -> int:
    """用最近 30 天内的 git 提交重写日志文件（用于初始化或对齐）。"""
    raw = subprocess.check_output(
        [
            "git",
            "log",
            f"--since={RETAIN_DAYS} days ago",
            "--format=%H%x09%ct%x09%s",
        ],
        cwd=ROOT,
        text=True,
    )
    entries: list[dict] = []
    for line in raw.splitlines():
        line = line.strip()
        if not line:
            continue
        parts = line.split("\t", 2)
        if len(parts) < 3:
            continue
        h, ct_s, subj = parts[0], parts[1], parts[2]
        try:
            ct = int(ct_s)
        except ValueError:
            continue
        ts = datetime.fromtimestamp(ct, tz=timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
        entries.append({"ts": ts, "subject": subj.strip(), "hash": h[:12]})
    entries.sort(key=lambda e: e["ts"])
    LOG_PATH.parent.mkdir(parents=True, exist_ok=True)
    lines = [json.dumps(e, ensure_ascii=False) for e in entries]
    LOG_PATH.write_text("\n".join(lines) + ("\n" if lines else ""), encoding="utf-8")
    return len(entries)


def main() -> None:
    p = argparse.ArgumentParser(description="代码变更日志（30 天滚动）")
    sub = p.add_subparsers(dest="cmd", required=True)

    sub.add_parser("prune", help="仅按 30 天窗口裁剪文件")

    r = sub.add_parser("record", help="追加一条（默认取当前 HEAD）")
    r.add_argument("-m", "--message", help="说明文字，默认 git log -1")
    r.add_argument("--hash", help="提交 hash，默认 HEAD")

    sub.add_parser("seed", help="用 git log（30 天）重写日志文件")

    args = p.parse_args()
    try:
        if args.cmd == "prune":
            n = prune()
            print(f"pruned, {n} line(s) kept", file=sys.stderr)
        elif args.cmd == "record":
            record(args.message, args.hash)
            print(f"appended -> {LOG_PATH}", file=sys.stderr)
        elif args.cmd == "seed":
            n = seed_from_git()
            print(f"seeded {n} commit(s) -> {LOG_PATH}", file=sys.stderr)
    except subprocess.CalledProcessError as e:
        print(e, file=sys.stderr)
        sys.exit(1)


if __name__ == "__main__":
    main()
