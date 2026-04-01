#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
根据最新 player_stats_*_regular_season_pergame.json 中的「球员姓名」
增量补全 nba-pc-analytics/player-zh.json（仅翻译缺失或非中文项）。

依赖： pip install deep-translator

也可单独运行： python scripts/rebuild_player_zh_deepl.py
"""
from __future__ import annotations

import json
import time
from pathlib import Path


def has_cjk(s: str) -> bool:
    return any("\u4e00" <= c <= "\u9fff" for c in (s or ""))


def needs_translate(name: str, cur: str | None) -> bool:
    if not cur or cur.strip() == name:
        return True
    return not has_cjk(cur)


def main() -> None:
    root = Path(__file__).resolve().parent.parent
    workspace = root.parent
    out_path = workspace / "nba-pc-analytics" / "player-zh.json"
    stats_files = sorted(root.glob("output/player_stats_*_regular_season_pergame.json"))
    if not stats_files:
        print("skip player-zh: no player_stats_*_pergame.json", flush=True)
        return

    names = sorted(
        {
            str(r.get("球员姓名") or "").strip()
            for r in json.loads(stats_files[-1].read_text(encoding="utf-8"))
            if r.get("球员姓名")
        }
    )
    old: dict[str, str] = {}
    if out_path.exists():
        try:
            raw = json.loads(out_path.read_text(encoding="utf-8"))
            if isinstance(raw, dict):
                old = {str(k): str(v) for k, v in raw.items()}
        except json.JSONDecodeError:
            pass

    try:
        from deep_translator import GoogleTranslator
    except ImportError:
        print(
            "skip player-zh: pip install deep-translator",
            flush=True,
        )
        return

    t = GoogleTranslator(source="en", target="zh-CN")
    todo = [n for n in names if needs_translate(n, old.get(n))]
    print(f"player-zh: {len(todo)} / {len(names)} names to translate", flush=True)

    for i, name in enumerate(todo):
        if i and i % 40 == 0:
            time.sleep(1.5)
        try:
            old[name] = t.translate(name)
        except Exception as ex:
            print(f"translate fail {name!r}: {ex}", flush=True)
            old[name] = name
        time.sleep(0.055)

    for n in names:
        if n not in old:
            old[n] = n

    ordered = {k: old[k] for k in sorted(old) if k in names}
    out_path.parent.mkdir(parents=True, exist_ok=True)
    out_path.write_text(
        json.dumps(ordered, ensure_ascii=False, separators=(",", ":")),
        encoding="utf-8",
    )
    same = sum(1 for k in names if ordered.get(k) == k)
    print(f"player-zh: wrote {out_path} identity_en={same}", flush=True)


if __name__ == "__main__":
    main()
