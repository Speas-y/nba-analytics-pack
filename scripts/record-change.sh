#!/usr/bin/env bash
# 追加一条代码变更日志（默认使用当前 HEAD 的提交说明），并裁剪为保留 30 天。
# 用法：./scripts/record-change.sh
#       ./scripts/record-change.sh "自定义说明"
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
if [[ $# -eq 0 ]]; then
  exec python3 "$ROOT/scripts/code_change_log.py" record
else
  exec python3 "$ROOT/scripts/code_change_log.py" record -m "$*"
fi
