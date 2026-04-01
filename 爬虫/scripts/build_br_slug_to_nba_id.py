#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
读取爬虫 output 里的 player_directory_*.json（球员网址别名 + 姓名），
与 nba_api 静态球员表 get_players() 做姓名匹配，生成：

  { "doncilu01": 1629029, ... }

供前端拼 NBA 官方头像：cdn.nba.com/headshots/nba/latest/260x190/{id}.png

依赖： pip install nba-api
"""
from __future__ import annotations

import argparse
import json
import unicodedata
from pathlib import Path

try:
    from nba_api.stats.static import players as nba_players
except ImportError as e:
    raise SystemExit(
        "缺少 nba_api，请在当前环境执行：pip install nba-api\n"
        f"原始错误: {e}",
    ) from e


def norm_name(s: str) -> str:
    s = (s or "").replace("ё", "e").replace("Ё", "e")
    s = unicodedata.normalize("NFD", s.strip())
    s = "".join(c for c in s if unicodedata.category(c) != "Mn")
    return " ".join(s.lower().split())


def build_nba_index():
    pl = nba_players.get_players()
    return [(norm_name(p.get("full_name") or ""), p) for p in pl if p.get("full_name")]


def find_nba_player(dir_display_name: str, nba_list: list) -> dict | None:
    d = norm_name(dir_display_name)
    if not d:
        return None

    exact = [p for n, p in nba_list if n == d]
    if exact:
        act = [x for x in exact if x.get("is_active")]
        return (act or exact)[0]

    prefix = [p for n, p in nba_list if n.startswith(d + " ")]
    if prefix:
        act = [x for x in prefix if x.get("is_active")]
        return sorted((act or prefix), key=lambda x: len(x["full_name"]))[0]

    parts = d.split()
    if len(parts) >= 2:
        first, last = parts[0], parts[-1]
        loose = [
            p
            for n, p in nba_list
            if len(n.split()) >= 2 and n.split()[0] == first and n.split()[-1] == last
        ]
        if loose:
            act = [x for x in loose if x.get("is_active")]
            return sorted((act or loose), key=lambda x: len(x["full_name"]))[0]

    return None


def default_directory_file(output_dir: Path) -> Path:
    files = sorted(output_dir.glob("player_directory_*.json"))
    if not files:
        raise SystemExit(f"在 {output_dir} 未找到 player_directory_*.json，请先跑爬虫。")
    return files[-1]


def main() -> None:
    script_dir = Path(__file__).resolve().parent
    nba_crawler_root = script_dir.parent
    workspace_root = nba_crawler_root.parent

    parser = argparse.ArgumentParser(description="生成 BR slug -> NBA PERSON_ID JSON")
    parser.add_argument(
        "--output-dir",
        type=Path,
        default=nba_crawler_root / "output",
        help="爬虫 output 目录（含 player_directory_*.json）",
    )
    parser.add_argument(
        "--directory",
        type=Path,
        default=None,
        help="指定单个 player_directory JSON；默认用 output 里文件名排序最后一个",
    )
    parser.add_argument(
        "--out",
        type=Path,
        default=workspace_root / "nba-pc-analytics" / "br-slug-to-nba-person-id.json",
        help="写入的映射文件路径",
    )
    args = parser.parse_args()

    dir_path = args.directory or default_directory_file(args.output_dir)
    if not dir_path.is_file():
        raise SystemExit(f"找不到文件: {dir_path}")

    rows = json.loads(dir_path.read_text(encoding="utf-8"))
    if not isinstance(rows, list):
        raise SystemExit("player_directory JSON 应为数组")

    nba_list = build_nba_index()
    slug_to_id: dict[str, int] = {}
    missing: list[tuple[str, str]] = []

    for row in rows:
        slug = str(row.get("球员网址别名") or "").strip()
        name = str(row.get("姓名") or "").strip()
        if not slug or not name:
            continue
        p = find_nba_player(name, nba_list)
        if not p:
            missing.append((slug, name))
            continue
        slug_to_id[slug] = int(p["id"])

    args.out.parent.mkdir(parents=True, exist_ok=True)
    args.out.write_text(
        json.dumps(slug_to_id, ensure_ascii=False, separators=(",", ":")),
        encoding="utf-8",
    )

    print(f"目录文件: {dir_path}")
    print(f"已写入: {args.out}（{len(slug_to_id)} 条）")
    if missing:
        print(f"未匹配（{len(missing)} 条），头像将走前端占位。示例:")
        for slug, name in missing[:12]:
            print(f"  {slug}  {name}")
        if len(missing) > 12:
            print(f"  … 共 {len(missing)} 条")


if __name__ == "__main__":
    main()
