#!/usr/bin/env python3
"""生成 team_recent_games_{season_tag}.json，供 Spring 球队概览「近 10 场」读取。

依赖：已在爬虫 venv 中安装 nba_api（与主爬虫相同环境）。
用法（在 爬虫 目录）：
  .venv/bin/python fetch_team_recent_games.py

输出：output/team_recent_games_2025_26.json（tag 与当季 league_standings 文件名一致）
"""

from __future__ import annotations

import json
import sys
import time
from datetime import datetime
from pathlib import Path

try:
    from nba_api.stats.endpoints.leaguegamefinder import LeagueGameFinder
except ImportError as e:  # pragma: no cover
    print("请先安装 nba_api：pip install nba_api", file=sys.stderr)
    raise SystemExit(1) from e


def season_api_from_tag(tag: str) -> str:
    # 2025_26 -> 2025-26
    parts = tag.split("_")
    if len(parts) != 2:
        raise ValueError(f"bad tag: {tag}")
    return f"{parts[0]}-{parts[1]}"


def parse_game_date(s: str) -> datetime:
    s = (s or "").strip()
    for fmt in ("%Y-%m-%d", "%b %d, %Y", "%B %d, %Y"):
        try:
            return datetime.strptime(s, fmt)
        except ValueError:
            continue
    return datetime.min


def main() -> None:
    here = Path(__file__).resolve().parent
    out_dir = here / "output"
    standings_files = sorted(out_dir.glob("league_standings_*.json"))
    if not standings_files:
        print("未找到 output/league_standings_*.json", file=sys.stderr)
        raise SystemExit(2)
    st_path = standings_files[-1]
    tag = st_path.stem.replace("league_standings_", "")
    season_api = season_api_from_tag(tag)
    teams = json.loads(st_path.read_text(encoding="utf-8"))
    if not isinstance(teams, list):
        print("战绩 JSON 应为数组", file=sys.stderr)
        raise SystemExit(2)

    result: dict[str, list[dict]] = {}
    for t in teams:
        abbr = (t.get("abbr") or t.get("nbaAbbr") or "").strip().upper()
        tid = t.get("teamId")
        if not abbr or not tid:
            continue
        try:
            finder = LeagueGameFinder(
                player_or_team_abbreviation="T",
                team_id_nullable=str(tid),
                season_nullable=season_api,
                season_type_nullable="Regular Season",
                timeout=60,
            )
            blob = finder.league_game_finder_results.get_dict()
            headers = blob.get("headers") or []
            data = blob.get("data") or []
            ix = {h: i for i, h in enumerate(headers)}
            rows_raw = []
            for row in data:
                if not isinstance(row, (list, tuple)):
                    continue
                def pick(name: str):
                    j = ix.get(name)
                    if j is None or j >= len(row):
                        return None
                    return row[j]

                wl = pick("WL")
                if not wl:
                    continue
                gd = pick("GAME_DATE")
                matchup = pick("MATCHUP")
                pts = pick("PTS")
                pm = pick("PLUS_MINUS")
                try:
                    pts_i = int(float(pts)) if pts is not None else 0
                except (TypeError, ValueError):
                    pts_i = 0
                opp = None
                if pm is not None:
                    try:
                        opp = int(round(float(pm)))
                        opp = pts_i - opp
                    except (TypeError, ValueError):
                        opp = None
                rows_raw.append(
                    {
                        "_sort": parse_game_date(str(gd) if gd else ""),
                        "gameDate": str(gd) if gd else "",
                        "matchup": str(matchup) if matchup else "",
                        "wl": str(wl).upper()[:1],
                        "pts": pts_i,
                        "oppPts": opp,
                    }
                )
            rows_raw.sort(key=lambda r: r["_sort"], reverse=True)
            for r in rows_raw:
                del r["_sort"]
            result[abbr] = rows_raw[:10]
        except Exception as ex:  # pragma: no cover
            print(abbr, ex, file=sys.stderr)
            result[abbr] = []
        time.sleep(0.65)

    outp = out_dir / f"team_recent_games_{tag}.json"
    outp.write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding="utf-8")
    print("Wrote", outp, "teams", len(result))


if __name__ == "__main__":
    main()
