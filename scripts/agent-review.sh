#!/usr/bin/env bash
set -euo pipefail

owner_repo="${GITHUB_REPOSITORY:-Emreerkaya/housedash-core-service}"
owner="${owner_repo%%/*}"
repo="${owner_repo##*/}"
pr="${PR_NUMBER:?PR_NUMBER is required}"
head_sha="${HEAD_SHA:?HEAD_SHA is required}"

if [ "${#head_sha}" -ne 40 ]; then
    printf 'HEAD_SHA %s is not a full commit sha, and a trailer is only accepted when it names one exactly\n' "$head_sha" >&2
    exit 2
fi

changed=$(gh pr diff "$pr" --repo "$owner_repo" --name-only)
if [ -z "$changed" ]; then
    printf 'pull request %s reports no changed files; refusing to pass vacuously\n' "$pr" >&2
    exit 2
fi

invariant_bearing=('^"?src/[^/]+/kotlin/com/housedash/domain/')
for pattern in "${invariant_bearing[@]}"; do
    if ! git ls-files | grep -Eq "$pattern"; then
        printf 'no file in this checkout matches %s, so the invariants trigger can no longer see the layer it guards; update this pattern\n' "$pattern" >&2
        exit 2
    fi
done

required=(architecture security testing performance)
defined_verdicts=(clean blocked)
for pattern in "${invariant_bearing[@]}"; do
    if printf '%s\n' "$changed" | grep -Eq "$pattern"; then
        required+=(invariants)
        break
    fi
done

reviews=$(gh api graphql --paginate --slurp -f query='
  query($owner:String!,$repo:String!,$pr:Int!,$endCursor:String){
    repository(owner:$owner,name:$repo){ pullRequest(number:$pr){
      reviews(first:100, after:$endCursor){
        pageInfo{ hasNextPage endCursor }
        nodes{
          state
          authorAssociation
          authorCanPushToRepository
          author{ login __typename }
          body
        }
      }
    }}}' -F owner="$owner" -F repo="$repo" -F pr="$pr" \
    | jq '[.[].data.repository.pullRequest.reviews.nodes[]]')

threads=$(gh api graphql --paginate --slurp -f query='
  query($owner:String!,$repo:String!,$pr:Int!,$endCursor:String){
    repository(owner:$owner,name:$repo){ pullRequest(number:$pr){
      reviewThreads(first:100, after:$endCursor){
        pageInfo{ hasNextPage endCursor }
        nodes{ isResolved }
      }
    }}}' -F owner="$owner" -F repo="$repo" -F pr="$pr" \
    | jq '[.[].data.repository.pullRequest.reviewThreads.nodes[]]')

entitled='.author != null
    and .author.__typename == "User"
    and .authorCanPushToRepository == true
    and (.authorAssociation | IN("OWNER", "MEMBER", "COLLABORATOR"))
    and (.state | IN("COMMENTED", "APPROVED", "CHANGES_REQUESTED"))'

while read -r login association push state; do
    [ -z "${login:-}" ] && continue
    printf 'ignored: review by %s (association %s, can push %s, state %s) is not entitled to gate a merge here\n' \
        "$login" "$association" "$push" "$state" >&2
done < <(printf '%s' "$reviews" | jq -r \
    ".[] | select((${entitled}) | not) | [(.author.login // \"(deleted)\"), .authorAssociation, (.authorCanPushToRepository | tostring), .state] | @tsv")

own_trailer='.body
    | split("\n")
    | map(sub("^\\s+"; "") | sub("\\s+$"; ""))
    | map(select(length > 0))
    | last // empty'

trailers=$(printf '%s' "$reviews" \
    | jq -r ".[] | select(${entitled}) | ${own_trailer}" \
    | grep -xE '<!--[[:space:]]*review-sha:[[:space:]]*[0-9a-f]{40}[[:space:]]+dimension:[[:space:]]*[^[:space:]]+[[:space:]]+verdict:[[:space:]]*[^[:space:]]+[[:space:]]*-->' \
    || true)

at_head=""
while read -r sha dimension verdict; do
    [ -z "${sha:-}" ] && continue
    if [ "$sha" = "$head_sha" ]; then
        at_head+="${dimension} ${verdict}"$'\n'
    fi
done < <(printf '%s\n' "$trailers" | sed -E 's/^<!--[[:space:]]*review-sha:[[:space:]]*([0-9a-f]+)[[:space:]]+dimension:[[:space:]]*([^[:space:]]+)[[:space:]]+verdict:[[:space:]]*([^[:space:]]+)[[:space:]]*-->$/\1 \2 \3/')

fail=0

for dimension in "${required[@]}"; do
    if ! printf '%s' "$at_head" | grep -q "^${dimension} "; then
        printf 'missing: no %s review at %s from an author entitled to gate a merge\n' "$dimension" "${head_sha:0:8}" >&2
        fail=1
    fi
done

while read -r dimension verdict; do
    [ -z "${dimension:-}" ] && continue
    case "$verdict" in
        clean)
            if ! printf '%s\n' "${required[@]}" | grep -qxF "$dimension"; then
                printf 'note: a clean %s review at %s names a dimension this diff does not require (%s) and is not counted\n' \
                    "$dimension" "${head_sha:0:8}" "${required[*]}" >&2
            fi
            ;;
        blocked)
            printf 'blocked: %s review at %s reports verdict blocked\n' "$dimension" "${head_sha:0:8}" >&2
            fail=1
            ;;
        *)
            printf 'malformed: %s review at %s reports verdict %s, which is not one of the verdicts the review format defines (%s); an undefined verdict fails closed rather than reading as clean, because a blocking verdict misspelled by one character used to pass\n' \
                "$dimension" "${head_sha:0:8}" "$verdict" "${defined_verdicts[*]}" >&2
            fail=1
            ;;
    esac
done < <(printf '%s' "$at_head")

unresolved=$(printf '%s' "$threads" | jq '[.[] | select(.isResolved == false)] | length' 2>/dev/null || true)
if [ "${unresolved:-0}" -gt 0 ]; then
    printf 'note: %s unresolved thread(s); the ruleset blocks the merge on these, not this check\n' "$unresolved" >&2
fi

if [ "$fail" -eq 0 ]; then
    printf 'every required dimension (%s) reviewed at %s by an entitled author, every verdict one of %s and none blocked\n' \
        "${required[*]}" "${head_sha:0:8}" "${defined_verdicts[*]}"
fi
exit "$fail"
