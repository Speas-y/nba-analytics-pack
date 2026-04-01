"""本地单元测试：赛季 URL 年、球员 ID、多队行优选（不发起网络请求）。"""
from __future__ import annotations

import pytest

from basketball_reference import (
    br_player_id,
    build_stats_url,
    parse_league_stats_table,
    rows_to_stats_dataframe,
    season_to_br_year,
)


def test_season_to_br_year() -> None:
    assert season_to_br_year("2024-25") == 2025
    assert season_to_br_year("2025-26") == 2026


def test_br_player_id_stable() -> None:
    assert br_player_id("curryst01") == br_player_id("curryst01")
    assert br_player_id("curryst01") != br_player_id("jamesle01")


def test_build_stats_url_regular_per_game() -> None:
    u = build_stats_url("2024-25", "Regular Season", "PerGame")
    assert u.endswith("/leagues/NBA_2025_per_game.html")


def test_build_stats_url_playoffs() -> None:
    u = build_stats_url("2024-25", "Playoffs", "PerGame")
    assert u.endswith("/playoffs/NBA_2025_per_game.html")


def test_parse_upgraded_table_name_display_and_per_g_suffix() -> None:
    """BR 升级表：name_display + *_per_g，无 data-stat=player。"""
    html = """
    <html><body>
    <table id="per_game_stats">
    <tbody>
    <tr>
      <th data-stat="ranker">1</th>
      <td data-stat="name_display"><a href="/players/d/doncilu01.html">Luka Dončić</a></td>
      <td data-stat="age">26</td>
      <td data-stat="team_name_abbr">LAL</td>
      <td data-stat="pos">PG</td>
      <td data-stat="games">62</td>
      <td data-stat="games_started">62</td>
      <td data-stat="mp_per_g">36.0</td>
      <td data-stat="trb_per_g">8.4</td>
      <td data-stat="ast_per_g">8.9</td>
      <td data-stat="pts_per_g">33.7</td>
    </tr>
    </tbody>
    </table>
    </body></html>
    """
    rows = parse_league_stats_table(html, "per_game_stats")
    assert len(rows) == 1
    assert rows[0]["PLAYER_SLUG"] == "doncilu01"
    assert rows[0]["PLAYER_NAME"] == "Luka Dončić"
    assert rows[0]["TEAM_ABBREVIATION"] == "LAL"
    assert rows[0]["GP"] == 62.0
    assert rows[0]["PTS"] == 33.7


def test_pick_2tm_over_team_rows() -> None:
    raw = [
        {
            "PLAYER_SLUG": "doncilu01",
            "PLAYER_NAME": "Luka Dončić",
            "TEAM_ABBREVIATION": "2TM",
            "GP": 50.0,
            "PTS": 28.0,
        },
        {
            "PLAYER_SLUG": "doncilu01",
            "PLAYER_NAME": "Luka Dončić",
            "TEAM_ABBREVIATION": "DAL",
            "GP": 22.0,
            "PTS": 28.1,
        },
    ]
    df = rows_to_stats_dataframe(raw)  # type: ignore[arg-type]
    assert len(df) == 1
    assert df.iloc[0]["TEAM_ABBREVIATION"] == "2TM"
