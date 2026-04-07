#!/usr/bin/env bash
# 追加 docs/代码变更记录.txt（摘要 + 问题说明 + 处理办法），并裁剪 30 天外条目。
# 示例见 docs/代码变更日志说明.txt
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
exec python3 "$ROOT/scripts/human_change_log.py" add "$@"
