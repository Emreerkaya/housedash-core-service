#!/usr/bin/env bash
set -uo pipefail

here=$(cd "$(dirname "$0")" && pwd)
gate="${here}/agent-review.sh"
stub_dir=$(mktemp -d)
trap 'rm -rf "$stub_dir"' EXIT

cat > "${stub_dir}/gh" <<'STUB'
#!/usr/bin/env bash
if [ "${1:-}" = "pr" ] && [ "${2:-}" = "diff" ]; then
    if [ -n "${STUB_FAIL_DIFF-}" ]; then
        printf 'gh: could not determine the changed files\n' >&2
        exit 1
    fi
    printf '%s' "${STUB_CHANGED-}"
    [ -n "${STUB_CHANGED-}" ] && printf '\n'
    exit 0
fi
if [ "${1:-}" = "api" ] && [ "${2:-}" = "graphql" ]; then
    if printf '%s' "$*" | grep -q reviewThreads; then
        printf '[{"data":{"repository":{"pullRequest":{"reviewThreads":{"nodes":%s}}}}}]' "${STUB_THREADS:-[]}"
    else
        printf '[{"data":{"repository":{"pullRequest":{"reviews":{"nodes":%s}}}}}]' "${STUB_REVIEWS:-[]}"
    fi
    exit 0
fi
printf 'stub gh received an unexpected call: %s\n' "$*" >&2
exit 90
STUB
chmod +x "${stub_dir}/gh"

sha=1111111111111111111111111111111111111111
other=2222222222222222222222222222222222222222
touched_domain='src/main/kotlin/com/housedash/domain/shared/ContactDetail.kt'
untouched='README.md'

review() {
    local dimension=$1 verdict=$2 at=${3:-$sha} push=${4:-true} assoc=${5:-OWNER} tail=${6:-} state=${7:-COMMENTED} typename=${8:-User}
    local body="Some prose about the change.\n\n<!-- review-sha: ${at} dimension: ${dimension} verdict: ${verdict} -->"
    [ -n "$tail" ] && body="${body}\n\n${tail}"
    printf '{"state":"%s","authorAssociation":"%s","authorCanPushToRepository":%s,"author":{"login":"someone","__typename":"%s"},"body":"%s"}' \
        "$state" "$assoc" "$push" "$typename" "$body"
}

review_as() {
    local state=$1 typename=$2 dimension=$3 verdict=$4
    review "$dimension" "$verdict" "$sha" true OWNER "" "$state" "$typename"
}

set_of() { printf '[%s]' "$(printf '%s,' "$@" | sed 's/,$//')"; }

four_dimensions=$(set_of \
    "$(review architecture clean)" \
    "$(review security clean)" \
    "$(review testing clean)" \
    "$(review performance clean)")

all_five=$(set_of \
    "$(review architecture clean)" \
    "$(review security clean)" \
    "$(review testing clean)" \
    "$(review performance clean)" \
    "$(review invariants clean)")

pass=0
fail=0

check() {
    local name=$1 want=$2 changed=$3 reviews=$4 wanted_text=${5:-} threads=${6:-[]} unwanted_text=${7:-}
    local out got
    out=$(PATH="${stub_dir}:${PATH}" \
        STUB_CHANGED="$changed" STUB_REVIEWS="$reviews" STUB_THREADS="$threads" \
        PR_NUMBER=1 HEAD_SHA="$sha" GITHUB_REPOSITORY=Emreerkaya/housedash-core-service \
        bash "$gate" 2>&1)
    got=$?
    if [ "$got" -ne "$want" ]; then
        printf 'FAIL %s: expected exit %s, got %s\n%s\n\n' "$name" "$want" "$got" "$out" >&2
        fail=$((fail + 1))
        return
    fi
    if [ -n "$wanted_text" ] && ! printf '%s' "$out" | grep -q "$wanted_text"; then
        printf 'FAIL %s: exit %s was right but the message never said %s\n%s\n\n' "$name" "$got" "$wanted_text" "$out" >&2
        fail=$((fail + 1))
        return
    fi
    if [ -n "$unwanted_text" ] && printf '%s' "$out" | grep -q "$unwanted_text"; then
        printf 'FAIL %s: exit %s was right but the message said %s and must not\n%s\n\n' "$name" "$got" "$unwanted_text" "$out" >&2
        fail=$((fail + 1))
        return
    fi
    printf 'ok %s\n' "$name"
    pass=$((pass + 1))
}

check 'five clean reviews on a domain diff pass' 0 "$touched_domain" "$all_five" 'none blocked'

check 'four clean reviews pass when the diff avoids the domain layer' 0 "$untouched" "$four_dimensions" \
    'architecture security testing performance'

check 'a domain diff without an invariants review is blocked' 1 "$touched_domain" "$four_dimensions" \
    'missing: no invariants review'

check 'a missing architecture review blocks' 1 "$untouched" \
    "$(set_of "$(review security clean)" "$(review testing clean)" "$(review performance clean)")" \
    'missing: no architecture review'

check 'a missing performance review blocks' 1 "$untouched" \
    "$(set_of "$(review architecture clean)" "$(review security clean)" "$(review testing clean)")" \
    'missing: no performance review'

check 'a blocked verdict blocks' 1 "$untouched" \
    "$(set_of "$(review architecture blocked)" "$(review security clean)" "$(review testing clean)" "$(review performance clean)")" \
    'reports verdict blocked'

check 'a verdict misspelled by one character fails closed rather than reading as clean' 1 "$untouched" \
    "$(set_of "$(review architecture cleann)" "$(review security clean)" "$(review testing clean)" "$(review performance clean)")" \
    'is not one of the verdicts'

check 'a review at a stale sha does not count' 1 "$untouched" \
    "$(set_of "$(review architecture clean "$other")" "$(review security clean)" "$(review testing clean)" "$(review performance clean)")" \
    'missing: no architecture review'

check 'a review by an author who cannot push is ignored' 1 "$untouched" \
    "$(set_of "$(review architecture clean "$sha" false)" "$(review security clean)" "$(review testing clean)" "$(review performance clean)")" \
    'is not entitled to gate a merge'

check 'a review by a drive-by contributor is ignored' 1 "$untouched" \
    "$(set_of "$(review architecture clean "$sha" true CONTRIBUTOR)" "$(review security clean)" "$(review testing clean)" "$(review performance clean)")" \
    'is not entitled to gate a merge'

check 'an APPROVED review carrying a blocked verdict still blocks' 1 "$untouched" \
    "$(set_of "$(review_as APPROVED User architecture blocked)" "$(review security clean)" "$(review testing clean)" "$(review performance clean)")" \
    'reports verdict blocked'

check 'an APPROVED review is counted, because approving is how a review is filed here' 0 "$untouched" \
    "$(set_of "$(review_as APPROVED User architecture clean)" "$(review security clean)" "$(review testing clean)" "$(review performance clean)")" \
    'none blocked' '[]' 'is not entitled'

check 'a review by an organisation member gates exactly as the owner does' 0 "$untouched" \
    "$(set_of "$(review architecture clean "$sha" true MEMBER)" "$(review security clean)" "$(review testing clean)" "$(review performance clean)")" \
    'none blocked' '[]' 'is not entitled'

check 'a blocked verdict from an organisation member blocks' 1 "$untouched" \
    "$(set_of "$(review architecture blocked "$sha" true MEMBER)" "$(review security clean)" "$(review testing clean)" "$(review performance clean)")" \
    'reports verdict blocked'

check 'a review by a collaborator gates exactly as the owner does' 0 "$untouched" \
    "$(set_of "$(review architecture clean "$sha" true COLLABORATOR)" "$(review security clean)" "$(review testing clean)" "$(review performance clean)")" \
    'none blocked' '[]' 'is not entitled'

check 'a blocked verdict from a collaborator blocks' 1 "$untouched" \
    "$(set_of "$(review architecture blocked "$sha" true COLLABORATOR)" "$(review security clean)" "$(review testing clean)" "$(review performance clean)")" \
    'reports verdict blocked'

check 'a trailer that is not the last line does not count' 1 "$untouched" \
    "$(set_of "$(review architecture clean "$sha" true OWNER 'and one more thought afterwards')" "$(review security clean)" "$(review testing clean)" "$(review performance clean)")" \
    'missing: no architecture review'

check 'a trailer naming only a sha prefix does not count' 1 "$untouched" \
    "$(set_of "$(review architecture clean 1111111)" "$(review security clean)" "$(review testing clean)" "$(review performance clean)")" \
    'missing: no architecture review'

check 'a path git quotes still requires the invariants review' 1 \
    '"src/main/kotlin/com/housedash/domain/shared/Gr\303\266\303\237e.kt"' \
    "$four_dimensions" 'missing: no invariants review'

check 'a diff of the gate itself still requires the security review' 1 \
    'scripts/agent-review.sh' \
    "$(set_of "$(review architecture clean)" "$(review testing clean)" "$(review performance clean)")" \
    'missing: no security review'

check 'a diff of the workflows still requires the security review' 1 \
    '.github/workflows/process-review.yml' \
    "$(set_of "$(review architecture clean)" "$(review testing clean)" "$(review performance clean)")" \
    'missing: no security review'

check 'the security review is required whatever the diff touches' 1 "$untouched" \
    "$(set_of "$(review architecture clean)" "$(review testing clean)" "$(review performance clean)")" \
    'missing: no security review'

check 'the required set is exactly the four dimensions when the domain is untouched' 0 \
    'scripts/agent-review.sh' "$four_dimensions" \
    'every required dimension (architecture security testing performance)'

check 'a clean review naming a dimension this diff does not require is not counted' 0 "$untouched" \
    "$all_five" 'is not counted'

check 'a blocked review blocks even when this diff does not require its dimension' 1 "$untouched" \
    "$(set_of "$(review architecture clean)" "$(review security clean)" "$(review testing clean)" "$(review performance clean)" "$(review invariants blocked)")" \
    'reports verdict blocked'

check 'a malformed verdict fails closed even on a dimension this diff does not require' 1 "$untouched" \
    "$(set_of "$(review architecture clean)" "$(review security clean)" "$(review testing clean)" "$(review performance clean)" "$(review invariants blockedd)")" \
    'is not one of the verdicts'

check 'a CHANGES_REQUESTED review carrying blocked still blocks' 1 "$untouched" \
    "$(set_of "$(review_as CHANGES_REQUESTED User architecture blocked)" "$(review security clean)" "$(review testing clean)" "$(review performance clean)")" \
    'reports verdict blocked'

check 'a DISMISSED review does not count' 1 "$untouched" \
    "$(set_of "$(review_as DISMISSED User architecture clean)" "$(review security clean)" "$(review testing clean)" "$(review performance clean)")" \
    'is not entitled to gate a merge'

check 'a review by a bot does not count' 1 "$untouched" \
    "$(set_of "$(review_as COMMENTED Bot architecture clean)" "$(review security clean)" "$(review testing clean)" "$(review performance clean)")" \
    'is not entitled to gate a merge'

check 'a later clean review does not clear an earlier blocked one at the same sha' 1 "$untouched" \
    "$(set_of "$(review architecture blocked)" "$(review architecture clean)" "$(review security clean)" "$(review testing clean)" "$(review performance clean)")" \
    'reports verdict blocked'

check 'a capitalised verdict is malformed rather than invisible' 1 "$untouched" \
    "$(set_of "$(review architecture clean)" "$(review security clean)" "$(review testing clean)" "$(review performance clean)" "$(review invariants Blocked)")" \
    'is not one of the verdicts'

check 'a capitalised blocked beside a clean one on the same required dimension still fails' 1 "$untouched" \
    "$(set_of "$(review architecture BLOCKED)" "$(review architecture clean)" "$(review security clean)" "$(review testing clean)" "$(review performance clean)")" \
    'is not one of the verdicts'

check 'a verdict with trailing punctuation is malformed rather than invisible' 1 "$untouched" \
    "$(set_of "$(review architecture clean)" "$(review security clean)" "$(review testing clean)" "$(review performance clean)" "$(review invariants 'blocked.')")" \
    'is not one of the verdicts'

check 'a misspelled dimension is noted and not counted rather than blocking' 1 "$untouched" \
    "$(set_of "$(review architeture clean)" "$(review security clean)" "$(review testing clean)" "$(review performance clean)")" \
    'missing: no architecture review'

check 'a dimension holding a regex metacharacter reads as missing, it does not satisfy the real one' 1 "$untouched" \
    "$(set_of "$(review 'a.chitecture' clean)" "$(review security clean)" "$(review testing clean)" "$(review performance clean)")" \
    'missing: no architecture review'

check 'an empty diff refuses to pass vacuously' 2 "" "$all_five" 'refusing to pass vacuously'

check 'a body with CRLF line endings still counts, as the web UI sends them' 0 "$untouched" \
    "$(printf '[%s,%s,%s,%s]' \
        "$(printf '{"state":"COMMENTED","authorAssociation":"OWNER","authorCanPushToRepository":true,"author":{"login":"someone","__typename":"User"},"body":"Prose.\\r\\n\\r\\n<!-- review-sha: %s dimension: architecture verdict: clean -->\\r\\n"}' "$sha")" \
        "$(review security clean)" "$(review testing clean)" "$(review performance clean)")" \
    'none blocked'

check 'no reviews at all blocks' 1 "$untouched" '[]' 'missing: no architecture review'

for short in 1111111 111111111111111111111111111111111111111 11111111111111111111111111111111111111111 \
    ZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZ 111111111111111111111111111111111111111g; do
    out=$(PATH="${stub_dir}:${PATH}" \
        STUB_CHANGED="$untouched" STUB_REVIEWS="$four_dimensions" STUB_THREADS='[]' \
        PR_NUMBER=1 HEAD_SHA="$short" GITHUB_REPOSITORY=Emreerkaya/housedash-core-service \
        bash "$gate" 2>&1)
    got=$?
    if [ "$got" -ne 2 ] || ! printf '%s' "$out" | grep -q 'is not a full commit sha'; then
        printf 'FAIL a HEAD_SHA of %s must be refused: exit %s\n%s\n\n' "$short" "$got" "$out" >&2
        fail=$((fail + 1))
    else
        printf 'ok a HEAD_SHA of %s is refused\n' "$short"
        pass=$((pass + 1))
    fi
done

out=$(PATH="${stub_dir}:${PATH}" \
    STUB_CHANGED="$untouched" STUB_REVIEWS="$four_dimensions" STUB_THREADS='[]' \
    PR_NUMBER=1 HEAD_SHA="${sha}"$'\n'"${other}" GITHUB_REPOSITORY=Emreerkaya/housedash-core-service \
    bash "$gate" 2>&1)
got=$?
if [ "$got" -ne 2 ] || ! printf '%s' "$out" | grep -q 'is not a full commit sha'; then
    printf 'FAIL a HEAD_SHA holding two full shas on two lines must be refused: exit %s\n%s\n\n' "$got" "$out" >&2
    fail=$((fail + 1))
else
    printf 'ok a HEAD_SHA holding two full shas on two lines is refused\n'
    pass=$((pass + 1))
fi

out=$(PATH="${stub_dir}:${PATH}" \
    STUB_CHANGED="$untouched" STUB_REVIEWS="$four_dimensions" STUB_THREADS='[]' STUB_FAIL_DIFF=1 \
    PR_NUMBER=1 HEAD_SHA="$sha" GITHUB_REPOSITORY=Emreerkaya/housedash-core-service \
    bash "$gate" 2>&1)
got=$?
if [ "$got" -eq 0 ] || printf '%s' "$out" | grep -qE 'every required dimension|refusing to pass vacuously'; then
    printf 'FAIL a gh that cannot list the changed files must fail loudly, not vacuously: exit %s\n%s\n\n' "$got" "$out" >&2
    fail=$((fail + 1))
else
    printf 'ok a gh that cannot list the changed files must fail loudly, not vacuously\n'
    pass=$((pass + 1))
fi

probe_repo() {
    local name=$1 want_text=$2
    shift 2
    local root
    root=$(mktemp -d)
    git -C "$root" init --quiet
    for path in "$@"; do
        mkdir -p "${root}/$(dirname "$path")"
        printf 'x\n' > "${root}/${path}"
    done
    git -C "$root" add -A >/dev/null 2>&1
    local out got
    out=$(cd "$root" && PATH="${stub_dir}:${PATH}" \
        STUB_CHANGED="$untouched" STUB_REVIEWS="$four_dimensions" STUB_THREADS='[]' \
        PR_NUMBER=1 HEAD_SHA="$sha" GITHUB_REPOSITORY=Emreerkaya/housedash-core-service \
        bash "$gate" 2>&1)
    got=$?
    rm -rf "$root"
    if [ "$got" -ne 2 ] || ! printf '%s' "$out" | grep -q "$want_text"; then
        printf 'FAIL %s: expected exit 2 saying %s, got %s\n%s\n\n' "$name" "$want_text" "$got" "$out" >&2
        fail=$((fail + 1))
    else
        printf 'ok %s\n' "$name"
        pass=$((pass + 1))
    fi
}

probe_repo 'a checkout no trigger pattern matches refuses to run at all' \
    'can no longer see the layer it guards' README.md

probe_repo 'a checkout whose domain layer moved refuses to run even though the rest is there' \
    'can no longer see the layer it guards' README.md scripts/agent-review.sh .github/workflows/process-review.yml

check 'a trailer sha one character too long reads as missing, though the trailer length clause alone cannot be isolated because HEAD_SHA is already forty lowercase hex' 1 "$untouched" \
    "$(set_of "$(review architecture clean "${sha}1")" "$(review security clean)" "$(review testing clean)" "$(review performance clean)")" \
    'missing: no architecture review'

check 'a trailer sha that is not hexadecimal reads as missing' 1 "$untouched" \
    "$(set_of "$(review architecture clean zzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzz)" "$(review security clean)" "$(review testing clean)" "$(review performance clean)")" \
    'missing: no architecture review'

check 'two unresolved threads are noted and do not block, because the ruleset blocks on them' 0 "$untouched" \
    "$four_dimensions" '2 unresolved thread(s)' '[{"isResolved":false},{"isResolved":false}]'

check 'a resolved thread is not counted' 0 "$untouched" "$four_dimensions" \
    'none blocked' '[{"isResolved":true},{"isResolved":true}]' 'unresolved thread'

check 'no threads at all is not an unresolved thread' 0 "$untouched" "$four_dimensions" \
    'none blocked' '[]' 'unresolved thread'

check 'a thread node that is not an object refuses to report a count it did not measure' 2 "$untouched" \
    "$four_dimensions" 'measured nothing' '["x"]'

check 'a renamed isResolved field is refused rather than silently counted as zero' 2 "$untouched" \
    "$four_dimensions" 'measured nothing' '[{"resolved":false}]'

check 'an isResolved that is not a boolean is refused rather than silently counted as zero' 2 "$untouched" \
    "$four_dimensions" 'measured nothing' '[{"isResolved":"maybe"}]'

printf '\n%s passed, %s failed\n' "$pass" "$fail"
[ "$fail" -eq 0 ]
