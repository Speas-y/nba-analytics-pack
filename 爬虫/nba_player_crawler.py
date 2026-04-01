#!/usr/bin/env python3
"""
从 Basketball-Reference (www.basketball-reference.com) 抓取 NBA 联盟球员数据。
导出赛季统计表及由该表推导的球员目录；支持常规赛 / 季后赛等赛季类型与 per_game / totals 等口径。

请注意遵守站点 robots.txt 与使用条款；请求间隔由 --sleep 控制，建议勿过小。
"""
from __future__ import annotations

import argparse
import sys
import time
from pathlib import Path

import pandas as pd

from basketball_reference import fetch_season_stats_df, stats_df_to_directory_df
from column_localization import localize_directory_df, localize_stats_df

# 与旧版 nba_api 参数文案兼容
REGULAR_SEASON = "Regular Season"
PLAYOFFS = "Playoffs"
PLAY_IN = "PlayIn"
PRESEASON = "Pre Season"
PER_GAME = "PerGame"
TOTALS = "Totals"
PER_36 = "Per 36 Minutes"
PER_MINUTE = "Per Minute"
PER_POSSESSION = "Per Possession"
PER_100_POSS = "Per 100 Possessions"


def _season_year_label(year_start: int) -> str:
    """例如 2025 -> '2025-26'。"""
    y2 = (year_start + 1) % 100
    return f"{year_start}-{y2:02d}"


def default_season_string() -> str:
    """根据当前日期推断 NBA 赛季字符串（7–6 月为下赛季）。"""
    from datetime import datetime, timezone

    now = datetime.now(timezone.utc)
    y, m = now.year, now.month
    start = y if m >= 7 else y - 1
    return _season_year_label(start)


def main() -> int:
    parser = argparse.ArgumentParser(
        description="NBA 球员数据抓取（Basketball-Reference.com）",
    )
    parser.add_argument(
        "--season",
        default=default_season_string(),
        help="赛季，如 2025-26（默认按当前日期推断）；对应 BR 的 N+1 赛季年",
    )
    parser.add_argument(
        "--season-type",
        default=REGULAR_SEASON,
        choices=[REGULAR_SEASON, PLAYOFFS, PLAY_IN, PRESEASON],
        help="赛季阶段",
    )
    parser.add_argument(
        "--per-mode",
        default=PER_GAME,
        choices=sorted(
            {
                TOTALS,
                PER_GAME,
                PER_36,
                PER_MINUTE,
                PER_POSSESSION,
                PER_100_POSS,
            }
        ),
        help="累计 / 每场 / 每 36 分钟等（映射到 BR 对应页面）",
    )
    parser.add_argument(
        "--out-dir",
        type=Path,
        default=Path("output"),
        help="输出目录",
    )
    parser.add_argument(
        "--sleep",
        type=float,
        default=3.0,
        help="两次 HTTP 请求之间的间隔（秒）；对 Sports Reference 建议 ≥2",
    )
    parser.add_argument(
        "--timeout",
        type=int,
        default=90,
        help="单次 HTTP 超时（秒）",
    )
    parser.add_argument(
        "--skip-directory",
        action="store_true",
        help="只拉赛季统计，不导球员目录",
    )
    parser.add_argument(
        "--skip-stats",
        action="store_true",
        help="只根据常规赛每场统计生成球员目录",
    )
    parser.add_argument(
        "--no-localize",
        action="store_true",
        help="保留英文列名与原始字段（默认导出中文列名）",
    )
    parser.add_argument(
        "--skip-standings",
        action="store_true",
        help="不拉取 NBA Stats 分区战绩（跳过 league_standings_*.json）",
    )
    args = parser.parse_args()
    args.out_dir.mkdir(parents=True, exist_ok=True)

    if args.skip_directory and args.skip_stats:
        print("同时指定 --skip-directory 与 --skip-stats，无事可做。", file=sys.stderr)
        return 1

    stats_df: pd.DataFrame | None = None

    if not args.skip_stats:
        print(
            f"抓取 Basketball-Reference 赛季统计 season={args.season} "
            f"type={args.season_type} per_mode={args.per_mode} …"
        )
        stats_df = fetch_season_stats_df(
            args.season,
            args.season_type,
            args.per_mode,
            timeout=args.timeout,
        )
        if stats_df.empty:
            print("未解析到任何球员行（检查赛季是否已有数据、网络或 Cloudflare）", file=sys.stderr)
            return 2
        out_stats = stats_df.copy()
        if not args.no_localize:
            out_stats = localize_stats_df(out_stats)
        st = args.season_type.lower().replace(" ", "_")
        pm = args.per_mode.lower().replace(" ", "_")
        stem = f"player_stats_{args.season.replace('-', '_')}_{st}_{pm}"
        out_stats.to_csv(args.out_dir / f"{stem}.csv", index=False)
        out_stats.to_json(
            args.out_dir / f"{stem}.json",
            orient="records",
            force_ascii=False,
            indent=2,
        )
        print(f"  写入 {args.out_dir}/{stem}.csv / .json（{len(out_stats)} 行）")
        time.sleep(max(0.0, args.sleep))
        if (
            not args.skip_standings
            and args.season_type == REGULAR_SEASON
            and args.per_mode == PER_GAME
        ):
            try:
                from fetch_nba_standings import write_league_standings_json

                st_path = write_league_standings_json(args.season, args.out_dir)
                print(f"  写入 NBA 常规赛分区战绩 {st_path.name}（stats.nba.com）")
            except Exception as ex:
                print(
                    f"  警告：未写入分区战绩（{ex}）。后端将按队均得分排序球队。",
                    file=sys.stderr,
                )

    if not args.skip_directory:
        print(f"生成球员目录 season={args.season} …（始终基于常规赛每场统计表，便于检索）")
        if (
            stats_df is not None
            and args.season_type == REGULAR_SEASON
            and args.per_mode == PER_GAME
        ):
            dir_src = stats_df
        else:
            time.sleep(max(0.0, args.sleep))
            dir_src = fetch_season_stats_df(
                args.season,
                REGULAR_SEASON,
                PER_GAME,
                timeout=args.timeout,
            )
        directory = stats_df_to_directory_df(dir_src)
        if directory.empty:
            print("球员目录为空", file=sys.stderr)
            return 3
        if not args.no_localize:
            directory = localize_directory_df(directory)
        stem = f"player_directory_{args.season.replace('-', '_')}"
        directory.to_csv(args.out_dir / f"{stem}.csv", index=False)
        directory.to_json(
            args.out_dir / f"{stem}.json",
            orient="records",
            force_ascii=False,
            indent=2,
        )
        print(f"  写入 {args.out_dir}/{stem}.csv / .json（{len(directory)} 行）")

    print("完成。")
    return 0


if __name__ == "__main__":
    sys.exit(main())
