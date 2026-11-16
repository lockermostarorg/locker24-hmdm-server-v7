#!/usr/bin/env bash
# Usage: docs-only.sh <deployed sha> <new sha>   (needs gh and GH_TOKEN; GITHUB_REPOSITORY)
# Exit 0 only when the new commit is strictly ahead of the deployed one and every changed file is under
# docs/ or ends in .md. Anything else, including any error, exits 1 (deploy).
set -euo pipefail
base=${1-}
head=${2-}
[[ $base =~ ^[0-9a-f]{40}$ && $head =~ ^[0-9a-f]{40}$ ]] || exit 1

out=$(gh api "repos/$GITHUB_REPOSITORY/compare/$base...$head?per_page=100" \
    --jq '.status, (.files | length), (.files[] | .filename, (.previous_filename // empty))') || exit 1

{
    read -r status
    read -r count
} <<< "$out"
[ "$status" = ahead ] || exit 1
# the compare API returns at most one page of files here; a full page may be truncated
[ "$count" -ge 1 ] && [ "$count" -lt 100 ] || exit 1

tail -n +3 <<< "$out" | while IFS= read -r f; do
    [[ $f == docs/* || $f == *.md ]] || exit 1
done
