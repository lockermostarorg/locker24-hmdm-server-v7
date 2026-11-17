#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
token_files='projects/core/src/theme-tokens.scss|projects/core/src/index.html'
pattern='(^|[^&0-9A-Za-z_])#([0-9A-Fa-f]{3}|[0-9A-Fa-f]{4}|[0-9A-Fa-f]{6}|[0-9A-Fa-f]{8})([^0-9A-Za-z_-]|$)|(^|[^A-Za-z_-])(rgba?|hsla?)\('
hits=$(grep -rnE "$pattern" projects/core/src projects/hmdm-ui-kit/src \
  --include='*.scss' --include='*.css' --include='*.html' --include='*.ts' \
  --exclude='*.spec.ts' --exclude-dir=node_modules \
  | grep -vE "^($token_files):" | grep -v 'l24-allow-color' || true)
if [ -n "$hits" ]; then
  echo "Colour literals outside the theme token files ($(printf '%s\n' "$hits" | wc -l | tr -d ' ') lines):"
  echo "$hits"
  exit 1
fi
echo "colour check passed"
