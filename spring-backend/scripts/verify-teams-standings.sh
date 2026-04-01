#!/usr/bin/env bash
# 验证 /public/nba/teams 是否合并了 league_standings（需已重启 Spring）。
# 用法：./scripts/verify-teams-standings.sh [API_BASE]
# 例： ./scripts/verify-teams-standings.sh http://127.0.0.1:3000/api
set -euo pipefail
BASE="${1:-http://127.0.0.1:3000/api}"
URL="${BASE%/}/public/nba/teams?season=2025&scope=regular"
HDR=$(mktemp)
BODY=$(mktemp)
trap 'rm -f "$HDR" "$BODY"' EXIT
curl -sS -D "$HDR" -o "$BODY" "$URL" || true
echo "=== Response headers (standings) ==="
grep -i '^X-NBA-Standings-Merged' "$HDR" || echo "(missing — server may be old build or wrong URL)"
echo "=== First team (expect confRank, conference, wins if merged) ==="
if command -v jq >/dev/null 2>&1; then
  jq '.[0] | {abbr, conference, confRank, wins, losses}' "$BODY" 2>/dev/null || head -c 400 "$BODY"
else
  head -c 600 "$BODY"
  echo
fi
