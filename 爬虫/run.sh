#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
VENV_PY="${ROOT}/.venv/bin/python"

if [[ -x "${VENV_PY}" ]]; then
  exec "${VENV_PY}" "${ROOT}/nba_player_crawler.py" "$@"
fi

if command -v python3 >/dev/null 2>&1; then
  echo "提示: 未检测到 ${ROOT}/.venv，请先执行:" >&2
  echo "  cd \"${ROOT}\" && python3 -m venv .venv && .venv/bin/pip install -r requirements.txt" >&2
  echo "随后用: ./run.sh  或  .venv/bin/python nba_player_crawler.py" >&2
  exec python3 "${ROOT}/nba_player_crawler.py" "$@"
fi

echo "错误: 未找到 python3，请从 https://www.python.org 安装 Python 3。" >&2
exit 1
