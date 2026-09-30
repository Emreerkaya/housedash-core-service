#!/usr/bin/env bash
set -euo pipefail
export LC_ALL=C

owner_repo="${GITHUB_REPOSITORY:-Emreerkaya/housedash-core-service}"
owner="${owner_repo%%/*}"
repo="${owner_repo##*/}"
pr="${PR_NUMBER:?PR_NUMBER is required}"
head_sha="${HEAD_SHA:?HEAD_SHA is required}"

if [ "${#head_sha}" -ne 40 ] || ! printf '%s' "$head_sha" | grep -qxE '[0-9a-f]{40}'; then
    printf 'HEAD_SHA %s is not a full commit sha, and a trailer is only accepted when it names one exactly\n' "$head_sha" >&2
    exit 2
fi

if ! files=$(gh api --paginate --slurp "repos/${owner_repo}/pulls/${pr}/files?per_page=100" | jq -c '[.[][]]') \
    || ! expected=$(gh api "repos/${owner_repo}/pulls/${pr}" | jq '.changed_files'); then
    printf 'could not list the files pull request %s changed, so nothing was measured\n' "$pr" >&2
    exit 2
fi

if ! printf '%s' "$files" | jq -e 'all(.[]; (.filename | type) == "string")' >/dev/null 2>&1 \
    || ! printf '%s' "$expected" | grep -qxE '[0-9]+'; then
    printf 'the pull request file listing came back in a shape this check cannot read, so it measured nothing\n' >&2
    exit 2
fi

listed=$(printf '%s' "$files" | jq 'length')
if [ "$listed" -eq 0 ]; then
    printf 'pull request %s reports no changed files; refusing to pass vacuously\n' "$pr" >&2
    exit 2
fi
if [ "$listed" -ne "$expected" ]; then
    printf 'pull request %s changed %s files but the listing returned %s, so a guarded path may be missing from it; refusing to pass on a partial diff\n' \
        "$pr" "$expected" "$listed" >&2
    exit 2
fi

control_char='explode | any(. < 32 or (. >= 127 and . <= 159))'
without_control='explode | map(select(. >= 32 and (. < 127 or . > 159))) | implode'
paths=$(printf '%s' "$files" | jq -r ".[] | (.filename, (.previous_filename // empty)) | ${without_control}")
hostile=$(printf '%s' "$files" | jq "[.[] | (.filename, (.previous_filename // empty)) | select(${control_char})] | length")

invariant_bearing=(
    'domain-money::(^|/)domain/([^/]+/)*money/::src/main/kotlin/com/housedash/domain/money/Money.kt'
    'domain-quote::(^|/)domain/([^/]+/)*quote/::src/main/kotlin/com/housedash/domain/quote/Quote.kt'
    'domain-booking::(^|/)domain/([^/]+/)*booking/::src/main/kotlin/com/housedash/domain/booking/Booking.kt'
    'domain-review::(^|/)domain/([^/]+/)*review/::src/main/kotlin/com/housedash/domain/review/Review.kt'
    'mint-route::mint::src/main/kotlin/com/housedash/adapters/inbound/http/MintController.kt'
)
gate_bearing=(
    'workflows::(^|/)\.github/::.github/workflows/process-review.yml'
    'gate-script::(^|/)scripts/agent[-_]?review::scripts/agent-review.sh'
    'codeowners::(^|/)codeowners$::CODEOWNERS'
)
unbuilt_triggers=(domain-quote domain-booking domain-review mint-route)

tracked=$(git ls-files -z | tr '\0' '\n')

for entry in "${invariant_bearing[@]}" "${gate_bearing[@]}"; do
    name=${entry%%::*}
    rest=${entry#*::}
    pattern=${rest%%::*}
    sample=${rest#*::}
    if ! grep -Eiq -e "$pattern" <<<"$sample"; then
        printf 'trigger %s (%s) does not match its own pinned sample %s, so the pattern is broken; update it\n' \
            "$name" "$pattern" "$sample" >&2
        exit 2
    fi
    if grep -Eiq -e "$pattern" <<<"$tracked"; then
        continue
    fi
    case " ${unbuilt_triggers[*]} " in
        *" ${name} "*)
            printf 'note: trigger %s has no file in this checkout yet and is listed as unbuilt, so only its pinned sample proves it alive\n' "$name" >&2
            continue
            ;;
    esac
    printf 'no file in this checkout matches trigger %s (%s), so a required-dimension trigger can no longer see the path it guards; update this pattern\n' \
        "$name" "$pattern" >&2
    exit 2
done

need_invariants=0
need_security=0
for entry in "${invariant_bearing[@]}"; do
    name=${entry%%::*}
    rest=${entry#*::}
    pattern=${rest%%::*}
    if grep -Eiq -e "$pattern" <<<"$paths"; then
        printf 'matched: %s (invariants)\n' "$name"
        need_invariants=1
    fi
done
for entry in "${gate_bearing[@]}"; do
    name=${entry%%::*}
    rest=${entry#*::}
    pattern=${rest%%::*}
    if grep -Eiq -e "$pattern" <<<"$paths"; then
        printf 'matched: %s (security)\n' "$name"
        need_security=1
    fi
done
if [ "$hostile" -gt 0 ]; then
    printf 'matched: control-character (security): %s changed path(s) carry a control character, which no honest path needs, so the diff fails closed to security review\n' "$hostile"
    need_security=1
fi

required=()
defined_verdicts=(clean blocked)
if [ "$need_invariants" -eq 1 ]; then
    required+=(invariants)
fi
if [ "$need_security" -eq 1 ]; then
    required+=(security)
fi

if [ "${#required[@]}" -eq 0 ]; then
    printf 'required: this diff requires no review dimensions; it touches none of the money, invariant or gate paths\n'
else
    printf 'required: %s\n' "${required[*]}"
fi

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
    | sed -nE 's/^<!--[[:space:]]*review-sha:[[:space:]]*([0-9a-f]{40})[[:space:]]+dimension:[[:space:]]*([^[:space:]]+)[[:space:]]+verdict:[[:space:]]*([^[:space:]]+)[[:space:]]*-->$/\1 \2 \3/p' \
    || true)

at_head=""
while read -r sha dimension verdict; do
    [ -z "${sha:-}" ] && continue
    if [ "$sha" = "$head_sha" ]; then
        at_head+="${dimension} ${verdict}"$'\n'
    fi
done < <(printf '%s\n' "$trailers")

blocking=0
pending=''

for dimension in "${required[@]:-}"; do
    [ -z "${dimension:-}" ] && continue
    if ! printf '%s' "$at_head" | grep -q "^${dimension} "; then
        printf 'missing: no %s review at %s from an author entitled to gate a merge\n' "$dimension" "${head_sha:0:8}" >&2
        pending="${pending}${pending:+ }${dimension}"
    fi
done

while read -r dimension verdict; do
    [ -z "${dimension:-}" ] && continue
    case "$verdict" in
        clean)
            if ! printf '%s\n' "${required[@]:-}" | grep -qxF "$dimension"; then
                printf 'note: a clean %s review at %s names a dimension this diff does not require (%s) and is not counted\n' \
                    "$dimension" "${head_sha:0:8}" "${required[*]:-none required}" >&2
            fi
            ;;
        blocked)
            printf 'blocked: %s review at %s reports verdict blocked\n' "$dimension" "${head_sha:0:8}" >&2
            blocking=1
            ;;
        *)
            printf 'malformed: %s review at %s reports verdict %s, which is not one of the verdicts the review format defines (%s); an undefined verdict fails closed rather than reading as clean, because a blocking verdict misspelled by one character used to pass\n' \
                "$dimension" "${head_sha:0:8}" "$verdict" "${defined_verdicts[*]}" >&2
            blocking=1
            ;;
    esac
done < <(printf '%s' "$at_head")

if ! unresolved=$(printf '%s' "$threads" | jq '
    if all(type == "object" and (.isResolved | type) == "boolean")
    then [.[] | select(.isResolved == false)] | length
    else error("a reviewThreads node carries no boolean isResolved") end'); then
    printf 'the reviewThreads query returned a shape this check cannot read, so it measured nothing rather than reporting no unresolved threads: %s\n' "$threads" >&2
    exit 2
fi
if [ "$unresolved" -gt 0 ]; then
    printf 'note: %s unresolved thread(s); the ruleset blocks the merge on these, not this check\n' "$unresolved" >&2
fi

if [ "$blocking" -ne 0 ]; then
    exit 1
fi

if [ -n "$pending" ]; then
    printf 'pending: this round has not finished; waiting for %s at %s. Nothing here is blocked or malformed, so this is neither a pass nor a refusal\n' \
        "$pending" "${head_sha:0:8}"
    exit 3
fi

if [ "${#required[@]}" -eq 0 ]; then
    printf 'this diff requires no review dimensions; it touches none of the money, invariant or gate paths, and no posted verdict blocked or was malformed\n'
else
    printf 'every required dimension (%s) reviewed at %s by an entitled author, every verdict one of %s and none blocked\n' \
        "${required[*]}" "${head_sha:0:8}" "${defined_verdicts[*]}"
fi
