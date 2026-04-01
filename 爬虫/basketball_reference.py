"""
从 Basketball-Reference (www.basketball-reference.com) 抓取联盟球员表格。
解析页面表格的 data-stat 属性，输出与 stats.nba 流程兼容的 DataFrame（英文列名），
再配合 column_localization 转为中文。
"""
from __future__ import annotations

import re
import zlib
from typing import Any

import pandas as pd
import requests
from bs4 import BeautifulSoup

BASE_URL = "https://www.basketball-reference.com"

# 更像浏览器的请求头；若仍遇 Cloudflare，可安装 cloudscraper（见 fetch_html）
DEFAULT_HEADERS = {
    "User-Agent": (
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) "
        "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
    ),
    "Accept": "text/html,application/xhtml+xml;q=0.9,*/*;q=0.8",
    "Accept-Language": "en-US,en;q=0.9",
}


def season_to_br_year(season: str) -> int:
    """
    赛季标签 '2024-25' / '2025-26' -> BR 的 NBA_{year} 后缀（该赛季结束的日历年）。
    例：2024-25 对应 NBA_2025（与官网一致）。
    """
    parts = season.strip().split("-")
    if len(parts) < 2:
        raise ValueError(f"无效赛季格式: {season!r}，应为如 2024-25")
    y0 = int(parts[0])
    return y0 + 1


def br_player_id(slug: str) -> int:
    """稳定正整数 ID（与 NBA 官方 PERSON_ID 不同），供前端/JSON 使用。"""
    return zlib.crc32(slug.encode("utf-8")) & 0x7FFFFFFF


def _slug_from_player_cell(cell) -> str:
    if cell is None:
        return ""
    a = cell.find("a", href=True)
    if not a:
        return ""
    m = re.search(r"/players/[a-z]/([^/]+)\.html", a["href"])
    return m.group(1) if m else ""


def _cell_text(cell) -> str:
    if cell is None:
        return ""
    return cell.get_text(strip=True)


def _parse_float(s: str) -> float:
    s = (s or "").strip()
    if not s:
        return float("nan")
    if s in (".", "-"):
        return float("nan")
    if s.startswith("."):
        s = "0" + s
    try:
        return float(s)
    except ValueError:
        return float("nan")


# data-stat -> 与 column_localization.STATS_COLUMN_ZH 对齐的英文列名（NBA 风格）
# 2024 起站点「Upgraded」表格同时使用 *_per_g 与 name_display；旧版仍有 mp/fg/pts 等。
BR_STAT_TO_NBA: dict[str, str] = {
    "age": "AGE",
    "games": "GP",
    "gs": "GS",
    "games_started": "GS",
    "mp": "MIN",
    "mp_per_g": "MIN",
    "fg": "FGM",
    "fg_per_g": "FGM",
    "fga": "FGA",
    "fga_per_g": "FGA",
    "fg_pct": "FG_PCT",
    "fg3": "FG3M",
    "fg3_per_g": "FG3M",
    "fg3a": "FG3A",
    "fg3a_per_g": "FG3A",
    "fg3_pct": "FG3_PCT",
    "ft": "FTM",
    "ft_per_g": "FTM",
    "fta": "FTA",
    "fta_per_g": "FTA",
    "ft_pct": "FT_PCT",
    "orb": "OREB",
    "orb_per_g": "OREB",
    "drb": "DREB",
    "drb_per_g": "DREB",
    "trb": "REB",
    "trb_per_g": "REB",
    "ast": "AST",
    "ast_per_g": "AST",
    "stl": "STL",
    "stl_per_g": "STL",
    "blk": "BLK",
    "blk_per_g": "BLK",
    "tov": "TOV",
    "tov_per_g": "TOV",
    "pf": "PF",
    "pf_per_g": "PF",
    "pts": "PTS",
    "pts_per_g": "PTS",
}


def fetch_html(url: str, timeout: int = 90) -> str:
    """
    抓取页面 HTML。Basketball-Reference 走 Cloudflare，plain requests 常会 403。
    顺序：curl_cffi（模拟 Chrome TLS/指纹）→ cloudscraper → requests。
    """
    headers = {
        **DEFAULT_HEADERS,
        "Referer": f"{BASE_URL}/",
    }
    last_err: Exception | None = None

    try:
        from curl_cffi import requests as curl_requests  # type: ignore[import-not-found]

        for imp in ("chrome131", "chrome124", "chrome120", "chrome119"):
            try:
                r = curl_requests.get(
                    url,
                    impersonate=imp,
                    timeout=timeout,
                    headers=headers,
                )
                r.raise_for_status()
                return str(r.text)
            except Exception as e:  # noqa: BLE001 —— 逐个 impersonate 回退
                last_err = e
                continue
    except ImportError:
        last_err = ImportError("curl_cffi 未安装（建议: pip install curl-cffi）")

    try:
        import cloudscraper  # type: ignore

        scraper = cloudscraper.create_scraper(
            browser={"browser": "chrome", "platform": "darwin", "mobile": False},
        )
        r = scraper.get(url, timeout=timeout, headers=headers)
        r.raise_for_status()
        return r.text
    except ImportError:
        pass
    except Exception as e:  # noqa: BLE001
        last_err = e

    try:
        r = requests.get(
            url,
            timeout=timeout,
            headers=headers,
            allow_redirects=True,
        )
        r.raise_for_status()
        return r.text
    except Exception as e:  # noqa: BLE001
        last_err = e

    hint = (
        "Basketball-Reference 返回 403 多因 Cloudflare 反爬。"
        "请先: pip install curl-cffi（已在 requirements.txt）；"
        "仍失败时请用本机住宅网络/关系统代理后再点「更新数据」，"
        "或在浏览器能打开该页面后在同机执行 nba_player_crawler.py。"
    )
    detail = f"{type(last_err).__name__}: {last_err}" if last_err else "unknown"
    raise RuntimeError(f"{hint} 最后错误: {detail}") from last_err


def _per_mode_segment(per_mode: str) -> str:
    m = per_mode.lower().strip().replace(" ", "_")
    if m in ("pergame", "per_game"):
        return "per_game"
    if m in ("totals",):
        return "totals"
    if m in ("per_36", "per_36_minutes", "per_minute", "36"):
        return "per_minute"
    if m in ("per_possession", "per_100_possessions"):
        return "per_poss"
    return "per_game"


def _table_id_for_segment(segment: str) -> str:
    return f"{segment}_stats"


def build_stats_url(season: str, season_type: str, per_mode: str) -> str:
    y = season_to_br_year(season)
    seg = _per_mode_segment(per_mode)
    stem = seg
    st = season_type.lower().strip().replace(" ", "_")
    if st in ("regular_season", "regular"):
        return f"{BASE_URL}/leagues/NBA_{y}_{stem}.html"
    if st in ("playoffs", "playoff"):
        return f"{BASE_URL}/playoffs/NBA_{y}_{stem}.html"
    if st in ("preseason", "pre_season"):
        return f"{BASE_URL}/leagues/NBA_{y}_preseason_{stem}.html"
    if st == "playin":
        return f"{BASE_URL}/leagues/NBA_{y}_{stem}.html"
    return f"{BASE_URL}/leagues/NBA_{y}_{stem}.html"


def parse_league_stats_table(html: str, table_id: str) -> list[dict[str, Any]]:
    soup = BeautifulSoup(html, "lxml")
    table = soup.find("table", id=table_id)
    if table is None:
        alt = soup.find("table", id=table_id.replace("_stats", ""))
        if alt is not None:
            table = alt
    if table is None:
        raise ValueError(f"页面中未找到表格 id={table_id!r}（可能被拦截或 URL 错误）")

    tbody = table.find("tbody")
    if not tbody:
        return []

    rows_out: list[dict[str, Any]] = []
    for tr in tbody.find_all("tr"):
        if "class" in tr.attrs and "thead" in tr.attrs.get("class", []):
            continue
        cells: dict[str, str] = {}
        for cell in tr.find_all(["th", "td"]):
            ds = cell.get("data-stat")
            if ds:
                cells[ds] = _cell_text(cell)

        player_cell = tr.find(["th", "td"], attrs={"data-stat": "player"}) or tr.find(
            ["th", "td"],
            attrs={"data-stat": "name_display"},
        )
        if not player_cell:
            continue
        if "player" not in cells and "name_display" not in cells:
            continue
        slug = _slug_from_player_cell(player_cell)
        if not slug:
            continue
        name = (cells.get("player") or cells.get("name_display") or "").strip()
        if not name or name.lower() in ("player", "name_display"):
            continue

        row: dict[str, Any] = {
            "PLAYER_SLUG": slug,
            "PLAYER_NAME": name,
        }
        for br_key, nba_key in BR_STAT_TO_NBA.items():
            if br_key not in cells:
                continue
            raw = cells[br_key]
            row[nba_key] = _parse_float(raw)

        team = cells.get("team_name_abbr", "").strip()
        row["TEAM_ABBREVIATION"] = team

        rows_out.append(row)

    return rows_out


def _pick_preferred_rows(rows: list[dict[str, Any]]) -> list[dict[str, Any]]:
    """同一球员多行时优选取 2TM（赛季汇总），否则取出场数最多的一行。"""
    by_slug: dict[str, list[dict[str, Any]]] = {}
    for r in rows:
        slug = r["PLAYER_SLUG"]
        by_slug.setdefault(slug, []).append(r)

    picked: list[dict[str, Any]] = []
    for slug, group in by_slug.items():
        twos = [x for x in group if str(x.get("TEAM_ABBREVIATION", "")).upper() == "2TM"]
        if twos:
            picked.append(twos[0])
            continue
        best = max(
            group,
            key=lambda x: (x.get("GP", 0) or 0) if not pd.isna(x.get("GP")) else 0,
        )
        picked.append(best)
    return picked


def rows_to_stats_dataframe(rows: list[dict[str, Any]]) -> pd.DataFrame:
    if not rows:
        return pd.DataFrame()
    picked = _pick_preferred_rows(rows)
    records = []
    for r in picked:
        slug = r["PLAYER_SLUG"]
        rec = {k: v for k, v in r.items()}
        rec["PLAYER_ID"] = br_player_id(slug)
        records.append(rec)
    df = pd.DataFrame(records)
    front = ["PLAYER_ID", "PLAYER_SLUG", "PLAYER_NAME", "TEAM_ABBREVIATION"]
    rest = [c for c in df.columns if c not in front]
    df = df[[c for c in front if c in df.columns] + rest]
    return df


def fetch_season_stats_df(
    season: str,
    season_type: str,
    per_mode: str,
    *,
    timeout: int = 90,
) -> pd.DataFrame:
    url = build_stats_url(season, season_type, per_mode)
    seg = _per_mode_segment(per_mode)
    table_id = _table_id_for_segment(seg)
    html = fetch_html(url, timeout=timeout)
    rows = parse_league_stats_table(html, table_id)
    return rows_to_stats_dataframe(rows)


def stats_df_to_directory_df(stats_df: pd.DataFrame) -> pd.DataFrame:
    """从统计表推导球员目录（无 BR 单独 roster 全量时的折中）。"""
    if stats_df.empty:
        return pd.DataFrame()
    rows = []
    for _, r in stats_df.iterrows():
        pid = int(r["PLAYER_ID"])
        name = str(r.get("PLAYER_NAME", ""))
        team = r.get("TEAM_ABBREVIATION", "")
        slug = str(r.get("PLAYER_SLUG", "") or "")
        rows.append(
            {
                "PERSON_ID": pid,
                "DISPLAY_FIRST_LAST": name,
                "DISPLAY_LAST_COMMA_FIRST": "",
                "PLAYER_SLUG": slug,
                "TEAM_ABBREVIATION": team,
                "TEAM_ID": "",
            }
        )
    return pd.DataFrame(rows)


def fetch_player_directory_from_stats(
    season: str,
    season_type: str,
    per_mode: str,
    *,
    timeout: int = 90,
) -> pd.DataFrame:
    """抓取与当前赛季类型/统计口径一致的联盟表，再生成目录（每球员一行）。"""
    df = fetch_season_stats_df(season, season_type, per_mode, timeout=timeout)
    out = stats_df_to_directory_df(df)
    if not out.empty:
        out = out.drop_duplicates(subset=["PERSON_ID"], keep="first")
    return out
