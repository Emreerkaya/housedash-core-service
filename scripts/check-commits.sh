#!/usr/bin/env bash
set -euo pipefail

base="${1:-origin/main}"

if ! git rev-parse --verify --quiet "$base" >/dev/null; then
    printf 'base ref %s does not resolve; refusing to pass vacuously\n' "$base" >&2
    exit 1
fi
pattern='^(feat|fix|refactor|chore|test|docs|style|perf|ci|revert)(\([a-z0-9-]+\))?: [a-z].+ \(#[0-9]+\)$'
fail=0

while read -r sha; do
    [ -z "$sha" ] && continue
    subject=$(git log -1 --format=%s "$sha")
    case "$subject" in
        Merge*|Revert*) continue ;;
    esac
    if ! printf '%s' "$subject" | grep -Eq "$pattern"; then
        printf 'rejected: %s\n  %s\n' "${sha:0:8}" "$subject" >&2
        fail=1
    fi
done < <(git rev-list "$base"..HEAD)

if [ "$fail" -eq 0 ]; then
    echo "every subject matches conventional commits with an issue reference"
fi
exit "$fail"
