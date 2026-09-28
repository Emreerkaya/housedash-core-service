#!/usr/bin/env bash
set -euo pipefail

owner_repo="${GITHUB_REPOSITORY:-Emreerkaya/housedash-core-service}"
owner="${owner_repo%%/*}"
repo="${owner_repo##*/}"
pr="${PR_NUMBER:?PR_NUMBER is required}"
head_sha="${HEAD_SHA:?HEAD_SHA is required}"

if [ "${#head_sha}" -lt 7 ]; then
    printf 'HEAD_SHA %s is too short to match a review trailer\n' "$head_sha" >&2
    exit 2
fi

changed=$(gh pr diff "$pr" --repo "$owner_repo" --name-only)
if [ -z "$changed" ]; then
    printf 'pull request %s reports no changed files; refusing to pass vacuously\n' "$pr" >&2
    exit 2
fi

required=(architecture security testing performance)
if printf '%s\n' "$changed" | grep -Eq '^src/main/kotlin/com/housedash/domain/(money|quote|booking|review)/'; then
    required+=(invariants)
fi

reviews=$(gh api graphql -f query='
  query($owner:String!,$repo:String!,$pr:Int!){
    repository(owner:$owner,name:$repo){ pullRequest(number:$pr){
      reviews(last:50){ nodes{ body } }
      reviewThreads(last:100){ nodes{ isResolved comments(first:1){ nodes{ body } } } }
    }}}' -F owner="$owner" -F repo="$repo" -F pr="$pr")

trailers=$(printf '%s' "$reviews" \
    | jq -r '.data.repository.pullRequest.reviews.nodes[].body' \
    | grep -oE '<!--[[:space:]]*review-sha:[[:space:]]*[0-9a-f]{7,40}[[:space:]]+dimension:[[:space:]]*[a-z]+[[:space:]]+verdict:[[:space:]]*[a-z]+[[:space:]]*-->' \
    || true)

at_head=""
while read -r sha dimension verdict; do
    [ -z "${sha:-}" ] && continue
    case "$head_sha" in
        "$sha"*) at_head+="${dimension} ${verdict}"$'\n' ;;
    esac
done < <(printf '%s\n' "$trailers" | sed -E 's/^<!--[[:space:]]*review-sha:[[:space:]]*([0-9a-f]+)[[:space:]]+dimension:[[:space:]]*([a-z]+)[[:space:]]+verdict:[[:space:]]*([a-z]+)[[:space:]]*-->$/\1 \2 \3/')

fail=0

for dimension in "${required[@]}"; do
    if ! printf '%s' "$at_head" | grep -q "^${dimension} "; then
        printf 'missing: no %s review at %s\n' "$dimension" "${head_sha:0:8}" >&2
        fail=1
    fi
done

while read -r dimension verdict; do
    [ -z "${dimension:-}" ] && continue
    if [ "$verdict" = "blocked" ]; then
        printf 'blocked: %s review at %s reports verdict blocked\n' "$dimension" "${head_sha:0:8}" >&2
        fail=1
    fi
done < <(printf '%s' "$at_head")

unresolved=$(printf '%s' "$reviews" | jq '[.data.repository.pullRequest.reviewThreads.nodes[] | select(.isResolved == false)] | length')
if [ "$unresolved" -gt 0 ]; then
    printf 'note: %s unresolved thread(s); the ruleset blocks the merge on these, not this check\n' "$unresolved" >&2
fi

if [ "$fail" -eq 0 ]; then
    printf 'every required dimension (%s) reviewed at %s, none blocked\n' "${required[*]}" "${head_sha:0:8}"
fi
exit "$fail"
