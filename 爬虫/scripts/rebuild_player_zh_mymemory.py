#!/usr/bin/env python3
"""从 per_game 统计 JSON 收集全部「球员姓名」，经 MyMemory 免费接口批量译为 zh-CN，写入 nba-pc-analytics/player-zh.json。"""
from __future__ import annotations

import json
import time
import urllib.parse
import urllib.request
from pathlib import Path


def translate_mymemory(q: str) -> str:
    u = (
        "https://api.mymemory.translated.net/get?q="
        + urllib.parse.quote(q)
        + "&langpair=en|zh-CN"
    )
    with urllib.request.urlopen(u, timeout=45) as r:
        j = json.loads(r.read().decode())
    return j["responseData"]["translatedText"]


def main() -> None:
    root = Path(__file__).resolve().parent.parent
    workspace = root.parent
    stats = sorted(root.glob("output/player_stats_*_regular_season_pergame.json"))
    if not stats:
        raise SystemExit("未找到 output/player_stats_*_regular_season_pergame.json")
    names = sorted(
        {
            str(r.get("球员姓名") or "").strip()
            for r in json.loads(stats[-1].read_text(encoding="utf-8"))
            if r.get("球员姓名")
        }
    )
    out: dict[str, str] = {}
    for i, name in enumerate(names):
        if i and i % 20 == 0:
            print("progress", i, "/", len(names), flush=True)
            time.sleep(1.0)
        try:
            out[name] = translate_mymemory(name)
        except Exception as e:
            print("fail", name, e, flush=True)
            out[name] = name
        time.sleep(0.12)

    target = workspace / "nba-pc-analytics" / "player-zh.json"
    target.write_text(
        json.dumps(out, ensure_ascii=False, separators=(",", ":")),
        encoding="utf-8",
    )
    print("wrote", target, len(out), "entries", flush=True)


if __name__ == "__main__":
    main()
