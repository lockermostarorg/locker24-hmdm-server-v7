#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
plugins=$(ls -d ../../../../plugins/*/src/main/web/projects/* 2>/dev/null | grep -v '/xtra$' || true)
hits=$(grep -rniE 'headwind|h-mdm' projects/core/src projects/core/public/i18n projects/hmdm-ui-kit/src $plugins \
  --include='*.html' --include='*.ts' --include='*.scss' --include='*.json' \
  --exclude='*.spec.ts' --exclude-dir=node_modules | grep -v 'l24-upstream-credit' || true)
if [ -n "$hits" ]; then
  echo "Headwind branding found outside the credit line:"
  echo "$hits"
  exit 1
fi
grep -q 'l24-upstream-credit' projects/core/src/app/shared/components/about-dialog/about-dialog.html || { echo "About credit line missing"; exit 1; }
echo "branding check passed"
