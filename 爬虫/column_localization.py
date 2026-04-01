"""将 DataFrame 英文列名与部分字段值转为中文（stats.nba 列名或与 basketball_reference 对齐的列名）。"""
from __future__ import annotations

import pandas as pd

# 球员目录 CommonAllPlayers
DIRECTORY_COLUMN_ZH: dict[str, str] = {
    "PERSON_ID": "球员ID",
    "DISPLAY_LAST_COMMA_FIRST": "姓名_姓在前",
    "DISPLAY_FIRST_LAST": "姓名",
    "ROSTERSTATUS": "是否在队名单",
    "FROM_YEAR": "进入联盟年份",
    "TO_YEAR": "最后效力年份",
    "PLAYERCODE": "球员代码",
    "PLAYER_SLUG": "球员网址别名",
    "TEAM_ID": "球队ID",
    "TEAM_CITY": "球队城市",
    "TEAM_NAME": "球队名",
    "TEAM_ABBREVIATION": "球队缩写",
    "TEAM_CODE": "球队代码",
    "TEAM_SLUG": "球队网址别名",
    "GAMES_PLAYED_FLAG": "是否出战过",
    "OTHERLEAGUE_EXPERIENCE_CH": "其他联赛经历标识",
}

# 联盟球员赛季统计 LeagueDashPlayerStats
STATS_COLUMN_ZH: dict[str, str] = {
    "PLAYER_ID": "球员ID",
    "PLAYER_SLUG": "球员网址别名",
    "PLAYER_NAME": "球员姓名",
    "NICKNAME": "昵称",
    "TEAM_ID": "球队ID",
    "TEAM_ABBREVIATION": "球队缩写",
    "AGE": "年龄",
    "GP": "出场次数",
    "W": "胜场",
    "L": "负场",
    "W_PCT": "胜率",
    "MIN": "上场时间",
    "FGM": "投篮命中",
    "FGA": "投篮出手",
    "FG_PCT": "投篮命中率",
    "FG3M": "三分命中",
    "FG3A": "三分出手",
    "FG3_PCT": "三分命中率",
    "FTM": "罚球命中",
    "FTA": "罚球出手",
    "FT_PCT": "罚球命中率",
    "OREB": "前场篮板",
    "DREB": "后场篮板",
    "REB": "篮板",
    "AST": "助攻",
    "TOV": "失误",
    "STL": "抢断",
    "BLK": "盖帽",
    "BLKA": "被盖",
    "PF": "犯规",
    "PFD": "造犯规",
    "PTS": "得分",
    "PLUS_MINUS": "正负值",
    "NBA_FANTASY_PTS": "NBA范特西得分",
    "WNBA_FANTASY_PTS": "WNBA范特西得分",
    "DD2": "两双场次",
    "TD3": "三双场次",
    "GP_RANK": "出场次数排名",
    "W_RANK": "胜场排名",
    "L_RANK": "负场排名",
    "W_PCT_RANK": "胜率排名",
    "MIN_RANK": "上场时间排名",
    "FGM_RANK": "投篮命中排名",
    "FGA_RANK": "投篮出手排名",
    "FG_PCT_RANK": "投篮命中率排名",
    "FG3M_RANK": "三分命中排名",
    "FG3A_RANK": "三分出手排名",
    "FG3_PCT_RANK": "三分命中率排名",
    "FTM_RANK": "罚球命中排名",
    "FTA_RANK": "罚球出手排名",
    "FT_PCT_RANK": "罚球命中率排名",
    "OREB_RANK": "前场篮板排名",
    "DREB_RANK": "后场篮板排名",
    "REB_RANK": "篮板排名",
    "AST_RANK": "助攻排名",
    "TOV_RANK": "失误排名",
    "STL_RANK": "抢断排名",
    "BLK_RANK": "盖帽排名",
    "BLKA_RANK": "被盖排名",
    "PF_RANK": "犯规排名",
    "PFD_RANK": "造犯规排名",
    "PTS_RANK": "得分排名",
    "PLUS_MINUS_RANK": "正负值排名",
    "NBA_FANTASY_PTS_RANK": "范特西得分排名",
    "WNBA_FANTASY_PTS_RANK": "WNBA范特西得分排名",
    "DD2_RANK": "两双场次排名",
    "TD3_RANK": "三双场次排名",
    "CFID": "筛选配置ID",
    "CFPARAMS": "筛选参数",
    "TEAM_COUNT": "效力球队数",
}


def _map_roster_status(v) -> str | float | int:
    if pd.isna(v):
        return v
    try:
        iv = int(v)
    except (TypeError, ValueError):
        return v
    if iv == 1:
        return "在队"
    if iv == 0:
        return "不在队"
    return v


def _map_yn_flag(v) -> str | float:
    if pd.isna(v):
        return v
    s = str(v).strip().upper()
    if s == "Y":
        return "是"
    if s == "N":
        return "否"
    return v


def localize_directory_df(df: pd.DataFrame) -> pd.DataFrame:
    out = df.copy()
    if "ROSTERSTATUS" in out.columns:
        out["ROSTERSTATUS"] = out["ROSTERSTATUS"].map(_map_roster_status)
    if "GAMES_PLAYED_FLAG" in out.columns:
        out["GAMES_PLAYED_FLAG"] = out["GAMES_PLAYED_FLAG"].map(_map_yn_flag)
    rename = {c: DIRECTORY_COLUMN_ZH[c] for c in out.columns if c in DIRECTORY_COLUMN_ZH}
    return out.rename(columns=rename)


def localize_stats_df(df: pd.DataFrame) -> pd.DataFrame:
    out = df.copy()
    rename = {c: STATS_COLUMN_ZH[c] for c in out.columns if c in STATS_COLUMN_ZH}
    return out.rename(columns=rename)
