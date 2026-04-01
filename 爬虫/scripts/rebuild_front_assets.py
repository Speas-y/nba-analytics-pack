#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
爬虫成功后由 Nest 后端在后台调用：依次执行
  1. build_br_slug_to_nba_id.py  → br-slug-to-nba-person-id.json
  2. rebuild_player_zh_deepl.py → player-zh.json

日志：nba-pc-analytics/.i18n-rebuild.log
"""
from __future__ import annotations

import subprocess
import sys
from datetime import datetime, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent


def log(msg: str) -> None:
    ws = ROOT.parent
    p = ws / "nba-pc-analytics" / ".i18n-rebuild.log"
    p.parent.mkdir(parents=True, exist_ok=True)
    ts = datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
    with open(p, "a", encoding="utf-8") as f:
        f.write(f"[{ts}] {msg}\n")


def run_step(label: str, script: Path, timeout: int) -> bool:
    if not script.is_file():
        log(f"SKIP {label}: missing {script}")
        return False
    try:
        cp = subprocess.run(
            [sys.executable, str(script)],
            cwd=str(ROOT),
            timeout=timeout,
            capture_output=True,
            text=True,
        )
        ok = cp.returncode == 0
        tail = ((cp.stdout or "") + (cp.stderr or "")).strip()
        if tail:
            tail = tail[-4000:]
        log(f"{label} exit={cp.returncode} ok={ok}\n{tail}")
        return ok
    except subprocess.TimeoutExpired:
        log(f"{label} TIMEOUT after {timeout}s")
        return False
    except Exception as e:
        log(f"{label} ERROR: {e}")
        return False


def main() -> None:
    log("=== rebuild_front_assets start ===")
    scripts_dir = ROOT / "scripts"
    slug_ok = run_step(
        "build_br_slug",
        scripts_dir / "build_br_slug_to_nba_id.py",
        timeout=180,
    )
    if not slug_ok:
        log("build_br_slug failed; still trying player-zh")
    zh_ok = run_step(
        "player_zh",
        scripts_dir / "rebuild_player_zh_deepl.py",
        timeout=900,
    )
    log(f"=== done slug_ok={slug_ok} zh_ok={zh_ok} ===")


if __name__ == "__main__":
    main()
