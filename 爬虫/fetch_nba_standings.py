#!/usr/bin/env python3
"""
拉取 NBA Stats 常规赛分区战绩（LeagueStandingsV3），导出为与球员表同赛季的 JSON，
供 Spring 合并球队「真实分区排名」。失败时不抛异常（由调用方记录警告）。

球队缩写使用 Basketball-Reference / 本仓库一致的写法（CHO、BRK、PHO）。
"""
from __future__ import annotations

import json
import sys
from pathlib import Path

# nba_api 与 BR 常见不一致的缩写
NBA_ABBR_TO_BR: dict[str, str] = {
    "CHA": "CHO",
    "BKN": "BRK",
    "PHX": "PHO",
}


def nba_abbr_to_br(abbr: str) -> str:
    u = (abbr or "").strip().upper()
    return NBA_ABBR_TO_BR.get(u, u)


def fetch_standings_records(season_hyphen: str) -> list[dict]:
    from nba_api.stats.endpoints.leaguestandingsv3 import LeagueStandingsV3

    # season 如 2025-26
    raw = LeagueStandingsV3(
        season=season_hyphen.strip(),
        season_type="Regular Season",
        timeout=90,
    )
    df = raw.standings.get_data_frame()
    out: list[dict] = []
    for _, row in df.iterrows():
        team_id = int(row["TeamID"])
        # TeamName 仅队名；缩写用 static 映射
        nba_abbr = _abbr_from_team_id(team_id)
        br_abbr = nba_abbr_to_br(nba_abbr)
        conf = str(row["Conference"]).strip()
        wins = int(row["WINS"])
        losses = int(row["LOSSES"])
        pct = float(row["WinPCT"])
        # 常规赛分区内名次（1–15）
        rank = int(row["PlayoffRank"])
        out.append(
            {
                "teamId": team_id,
                "abbr": br_abbr,
                "nbaAbbr": nba_abbr,
                "conference": conf,
                "confRank": rank,
                "wins": wins,
                "losses": losses,
                "winPct": round(pct, 3),
            }
        )
    return out


def _abbr_from_team_id(team_id: int) -> str:
    from nba_api.stats.static import teams as nba_teams

    for t in nba_teams.get_teams():
        if int(t["id"]) == team_id:
            return str(t["abbreviation"]).upper()
    return ""


def write_league_standings_json(season_hyphen: str, out_dir: Path) -> Path:
    """season_hyphen: 2025-26；写出 league_standings_2025_26.json"""
    stem = season_hyphen.strip().replace("-", "_")
    out_dir.mkdir(parents=True, exist_ok=True)
    path = out_dir / f"league_standings_{stem}.json"
    rows = fetch_standings_records(season_hyphen)
    if len(rows) < 30:
        raise RuntimeError(f"战绩行数异常（{len(rows)}），预期 30 支球队")
    path.write_text(
        json.dumps(rows, ensure_ascii=False, indent=2),
        encoding="utf-8",
    )
    return path


def main() -> int:
    import argparse
    from nba_player_crawler import default_season_string

    p = argparse.ArgumentParser(description="导出 NBA 常规赛分区战绩 JSON")
    p.add_argument(
        "--season",
        default=default_season_string(),
        help="赛季 如 2025-26",
    )
    p.add_argument("--out-dir", type=Path, default=Path("output"))
    args = p.parse_args()
    try:
        path = write_league_standings_json(args.season, args.out_dir)
    except Exception as e:
        print(f"失败：{e}", file=sys.stderr)
        return 1
    print(f"写入 {path}（{30} 队）")
    return 0


if __name__ == "__main__":
    sys.exit(main())
