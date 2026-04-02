#!/usr/bin/env bash
# 运营侧本机：跑爬虫 → 可选生成 i18n 映射 → git commit + push（应用内已无「在线更新数据」入口）
# 用法：在仓库根目录执行  ./更新   或  bash scripts/crawler-update-and-push.sh
# 追加参数会传给 nba_player_crawler.py，例如：./更新 --season 2025-26
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

CRAWLER="$ROOT/爬虫"
VENV="$CRAWLER/.venv"
PY="$VENV/bin/python"
PIP="$VENV/bin/pip"

if [[ ! -x "$PY" ]]; then
  echo "创建爬虫虚拟环境并安装依赖…"
  python3 -m venv "$VENV"
  "$PIP" install -r "$CRAWLER/requirements.txt"
fi

if ! git rev-parse --is-inside-work-tree &>/dev/null; then
  echo "错误：当前不是 git 仓库。" >&2
  exit 1
fi

echo "运行爬虫（Basketball-Reference）…"
# 必须在 爬虫/output 写入（与 Docker COPY、git add 路径一致；勿依赖进程 cwd 默认的 ./output）
"$PY" "$CRAWLER/nba_player_crawler.py" --out-dir "$CRAWLER/output" "$@"

echo "尝试生成 nba-pc-analytics 映射（失败不阻断提交 output）…"
"$PY" "$CRAWLER/scripts/rebuild_front_assets.py" || true

shopt -s nullglob
paths=(爬虫/output/*.json)
[[ -f nba-pc-analytics/player-zh.json ]] && paths+=("nba-pc-analytics/player-zh.json")
[[ -f nba-pc-analytics/br-slug-to-nba-person-id.json ]] && paths+=("nba-pc-analytics/br-slug-to-nba-person-id.json")

if [[ ${#paths[@]} -eq 0 ]]; then
  echo "错误：未找到可提交的 JSON。" >&2
  exit 1
fi

git add "${paths[@]}"

if git diff --cached --quiet; then
  echo "数据文件相对仓库无变化，跳过 commit / push。"
  exit 0
fi

msg="chore(data): crawler update $(date -u +%Y-%m-%dT%H:%MZ)"
git commit -m "$msg"
git push
echo "已完成推送。"
