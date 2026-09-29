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

# Fixtures for the diffs goal.md's "Function first" table distinguishes.
untouched='README.md'
touched_migration='src/main/resources/db/migration/V2__quote.sql'
touched_case='src/main/kotlin/com/housedash/domain/case/Case.kt'
touched_shared='src/main/kotlin/com/housedash/domain/shared/ContactDetail.kt'
touched_money='src/main/kotlin/com/housedash/domain/money/EscrowHold.kt'
touched_quote='src/main/kotlin/com/housedash/domain/quote/Quote.kt'
touched_booking='src/main/kotlin/com/housedash/domain/booking/Booking.kt'
touched_review='src/main/kotlin/com/housedash/domain/review/Review.kt'
touched_mint='src/main/kotlin/com/housedash/adapters/inbound/http/MintController.kt'
touched_gate_script='scripts/agent-review.sh'
touched_workflow='.github/workflows/process-review.yml'
touched_codeowners='CODEOWNERS'
money_and_gate=$'src/main/kotlin/com/housedash/domain/money/EscrowHold.kt\nscripts/agent-review.sh'

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

pass=0
fail=0
names=''

check() {
    local name=$1 want=$2 changed=$3 reviews=$4 wanted_text=${5:-} threads=${6:-[]} unwanted_text=${7:-}
    names="${names}${name}"$'\n'
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

cases_this_suite_runs=73

# --- No dimension required: a feature, a screen, a fixture, a migration, a wiring change ---

check 'an ordinary diff requires no dimensions and passes with no reviews at all' 0 "$untouched" '[]' \
    'this diff requires no review dimensions'

check 'a migration diff requires no dimensions and passes with no reviews' 0 "$touched_migration" '[]' \
    'this diff requires no review dimensions'

check 'a domain/case diff requires no dimensions, unlike the old whole-domain trigger' 0 "$touched_case" '[]' \
    'this diff requires no review dimensions'

check 'a domain/shared diff requires no dimensions, unlike the old whole-domain trigger' 0 "$touched_shared" '[]' \
    'this diff requires no review dimensions'

check 'a clean review naming a dimension an ordinary diff does not require is noted and still passes' 0 "$untouched" \
    "$(set_of "$(review invariants clean)")" \
    'is not counted'

# --- domain/money, quote/, booking/, review/ or a mint route requires invariants ---

check 'a domain/money diff with no invariants review is pending, naming invariants' 3 "$touched_money" '[]' \
    'waiting for invariants'

check 'a domain/money diff with a clean invariants review passes' 0 "$touched_money" \
    "$(set_of "$(review invariants clean)")" \
    'required: invariants'

check 'a domain/quote diff requires invariants' 3 "$touched_quote" '[]' 'waiting for invariants'

check 'a domain/booking diff requires invariants' 3 "$touched_booking" '[]' 'waiting for invariants'

check 'a domain/review diff requires invariants' 3 "$touched_review" '[]' 'waiting for invariants'

check 'a mint route file requires invariants through its own branch of the trigger' 3 "$touched_mint" '[]' \
    'waiting for invariants'

check 'a blocked invariants verdict blocks a money diff' 1 "$touched_money" \
    "$(set_of "$(review invariants blocked)")" \
    'reports verdict blocked'

check 'a malformed verdict on a money diff fails closed rather than reading as clean' 1 "$touched_money" \
    "$(set_of "$(review invariants cleann)")" \
    'is not one of the verdicts'

# --- a merge gate, a ruleset, a permission, or CI that decides whether code lands requires security ---

check 'a diff of the gate script itself requires security, and only security' 3 "$touched_gate_script" '[]' \
    'waiting for security'

check 'a diff of the gate script with a clean security review passes' 0 "$touched_gate_script" \
    "$(set_of "$(review security clean)")" \
    'required: security'

check 'a diff of a workflow file requires security' 3 "$touched_workflow" '[]' 'waiting for security'

check 'a diff of CODEOWNERS requires security' 3 "$touched_codeowners" '[]' 'waiting for security'

check 'a blocked security verdict blocks a gate diff' 1 "$touched_gate_script" \
    "$(set_of "$(review security blocked)")" \
    'reports verdict blocked'

# --- both triggers on one diff ---

check 'a diff touching both money and the gate script requires invariants and security together' 3 \
    "$money_and_gate" '[]' 'waiting for invariants security'

check 'a mixed diff with only invariants posted still waits on security alone' 3 "$money_and_gate" \
    "$(set_of "$(review invariants clean)")" \
    'waiting for security' '[]' 'waiting for invariants security'

check 'a mixed diff with both dimensions posted clean passes' 0 "$money_and_gate" \
    "$(set_of "$(review invariants clean)" "$(review security clean)")" \
    'required: invariants security'

check 'a mixed diff blocks on either dimension even while the other is still pending' 1 "$money_and_gate" \
    "$(set_of "$(review invariants blocked)")" \
    'reports verdict blocked'

# --- entitlement, trailer parsing and verdict handling, exercised against a single required dimension ---

check 'a review at a stale sha does not count, leaving the dimension pending' 3 "$touched_gate_script" \
    "$(set_of "$(review security clean "$other")")" \
    'missing: no security review'

check 'a review by an author who cannot push is ignored, leaving the dimension pending' 3 "$touched_gate_script" \
    "$(set_of "$(review security clean "$sha" false)")" \
    'is not entitled to gate a merge'

check 'a review by a drive-by contributor is ignored, leaving the dimension pending' 3 "$touched_gate_script" \
    "$(set_of "$(review security clean "$sha" true CONTRIBUTOR)")" \
    'is not entitled to gate a merge'

check 'an APPROVED review carrying a blocked verdict still blocks' 1 "$touched_gate_script" \
    "$(set_of "$(review_as APPROVED User security blocked)")" \
    'reports verdict blocked'

check 'an APPROVED review is counted, because approving is how a review is filed here' 0 "$touched_gate_script" \
    "$(set_of "$(review_as APPROVED User security clean)")" \
    'required: security' '[]' 'is not entitled'

check 'a review by an organisation member gates exactly as the owner does' 0 "$touched_gate_script" \
    "$(set_of "$(review security clean "$sha" true MEMBER)")" \
    'required: security' '[]' 'is not entitled'

check 'a blocked verdict from an organisation member blocks' 1 "$touched_gate_script" \
    "$(set_of "$(review security blocked "$sha" true MEMBER)")" \
    'reports verdict blocked'

check 'a review by a collaborator gates exactly as the owner does' 0 "$touched_gate_script" \
    "$(set_of "$(review security clean "$sha" true COLLABORATOR)")" \
    'required: security' '[]' 'is not entitled'

check 'a blocked verdict from a collaborator blocks' 1 "$touched_gate_script" \
    "$(set_of "$(review security blocked "$sha" true COLLABORATOR)")" \
    'reports verdict blocked'

check 'a trailer that is not the last line does not count, leaving the dimension pending' 3 "$touched_gate_script" \
    "$(set_of "$(review security clean "$sha" true OWNER 'and one more thought afterwards')")" \
    'missing: no security review'

check 'a trailer naming only a sha prefix does not count, leaving the dimension pending' 3 "$touched_gate_script" \
    "$(set_of "$(review security clean 1111111)")" \
    'missing: no security review'

check 'a diff of the gate itself still requires security even when the changed path is quoted' 3 \
    '"scripts/agent-review.sh"' '[]' 'waiting for security'

check 'the required set is named exactly when both dimensions are satisfied' 0 \
    "$money_and_gate" "$(set_of "$(review invariants clean)" "$(review security clean)")" \
    'required: invariants security'

check 'a clean review naming a dimension this diff does not require is not counted' 0 "$touched_gate_script" \
    "$(set_of "$(review security clean)" "$(review invariants clean)")" \
    'is not counted'

check 'a blocked review blocks even when this diff does not require its dimension' 1 "$untouched" \
    "$(set_of "$(review invariants blocked)")" \
    'reports verdict blocked'

check 'a malformed verdict fails closed even on a dimension this diff does not require' 1 "$untouched" \
    "$(set_of "$(review invariants blockedd)")" \
    'is not one of the verdicts'

check 'a CHANGES_REQUESTED review carrying blocked still blocks' 1 "$touched_gate_script" \
    "$(set_of "$(review_as CHANGES_REQUESTED User security blocked)")" \
    'reports verdict blocked'

check 'a DISMISSED review does not count' 3 "$touched_gate_script" \
    "$(set_of "$(review_as DISMISSED User security clean)")" \
    'is not entitled to gate a merge'

check 'a review by a bot does not count' 3 "$touched_gate_script" \
    "$(set_of "$(review_as COMMENTED Bot security clean)")" \
    'is not entitled to gate a merge'

check 'a later clean review does not clear an earlier blocked one at the same sha' 1 "$touched_gate_script" \
    "$(set_of "$(review security blocked)" "$(review security clean)")" \
    'reports verdict blocked'

check 'a capitalised verdict is malformed rather than invisible' 1 "$touched_gate_script" \
    "$(set_of "$(review security Blocked)")" \
    'is not one of the verdicts'

check 'a capitalised blocked beside a clean one on the same required dimension still fails' 1 "$touched_gate_script" \
    "$(set_of "$(review security BLOCKED)" "$(review security clean)")" \
    'is not one of the verdicts'

check 'a verdict with trailing punctuation is malformed rather than invisible' 1 "$touched_gate_script" \
    "$(set_of "$(review security 'blocked.')")" \
    'is not one of the verdicts'

check 'a misspelled dimension is not counted, leaving the real one pending' 3 "$touched_gate_script" \
    "$(set_of "$(review securty clean)")" \
    'missing: no security review'

check 'a dimension holding a regex metacharacter reads as missing, it does not satisfy the real one' 3 \
    "$touched_gate_script" "$(set_of "$(review 'securit.' clean)")" \
    'missing: no security review'

check 'a dimension name with the required one as its tail does not satisfy the requirement' 3 \
    "$touched_gate_script" "$(set_of "$(review xsecurity clean)")" \
    'missing: no security review'

check 'an empty diff refuses to pass vacuously' 2 "" '[]' 'refusing to pass vacuously'

check 'a body with CRLF line endings still counts, as the web UI sends them' 0 "$touched_gate_script" \
    "$(printf '[%s]' \
        "$(printf '{"state":"COMMENTED","authorAssociation":"OWNER","authorCanPushToRepository":true,"author":{"login":"someone","__typename":"User"},"body":"Prose.\\r\\n\\r\\n<!-- review-sha: %s dimension: security verdict: clean -->\\r\\n"}' "$sha")")" \
    'required: security'

check 'no reviews at all does not pass' 3 "$touched_gate_script" '[]' 'missing: no security review'

for short in 1111111 111111111111111111111111111111111111111 11111111111111111111111111111111111111111 \
    ZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZ 111111111111111111111111111111111111111g; do
    out=$(PATH="${stub_dir}:${PATH}" \
        STUB_CHANGED="$untouched" STUB_REVIEWS='[]' STUB_THREADS='[]' \
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
    STUB_CHANGED="$untouched" STUB_REVIEWS='[]' STUB_THREADS='[]' \
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
    STUB_CHANGED="$untouched" STUB_REVIEWS='[]' STUB_THREADS='[]' STUB_FAIL_DIFF=1 \
    PR_NUMBER=1 HEAD_SHA="$sha" GITHUB_REPOSITORY=Emreerkaya/housedash-core-service \
    bash "$gate" 2>&1)
got=$?
if [ "$got" -eq 0 ] || printf '%s' "$out" | grep -qE 'requires no review dimensions|refusing to pass vacuously'; then
    printf 'FAIL a gh that cannot list the changed files must fail loudly, not vacuously: exit %s\n%s\n\n' "$got" "$out" >&2
    fail=$((fail + 1))
else
    printf 'ok a gh that cannot list the changed files must fail loudly, not vacuously\n'
    pass=$((pass + 1))
fi

probe_repo() {
    local name=$1 want_text=$2
    names="${names}${name}"$'\n'
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
        STUB_CHANGED="$untouched" STUB_REVIEWS='[]' STUB_THREADS='[]' \
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

probe_repo 'a checkout where neither trigger pattern matches any file refuses to run at all' \
    'can no longer see the path it guards' README.md

probe_repo 'a checkout with a money file but no gate-bearing file still refuses, on the gate trigger' \
    'can no longer see the path it guards' README.md src/main/kotlin/com/housedash/domain/money/EscrowHold.kt

probe_repo 'a checkout whose domain layer moved refuses to run even though the rest is there' \
    'can no longer see the path it guards' README.md scripts/agent-review.sh .github/workflows/process-review.yml

check 'a trailer sha one character too long reads as missing, though the trailer length clause alone cannot be isolated because HEAD_SHA is already forty lowercase hex' 3 \
    "$touched_gate_script" "$(set_of "$(review security clean "${sha}1")")" \
    'missing: no security review'

check 'a trailer sha that is not hexadecimal reads as missing' 3 "$touched_gate_script" \
    "$(set_of "$(review security clean zzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzz)")" \
    'missing: no security review'

check 'two unresolved threads are noted and do not block, because the ruleset blocks on them' 0 "$touched_gate_script" \
    "$(set_of "$(review security clean)")" '2 unresolved thread(s)' '[{"isResolved":false},{"isResolved":false}]'

check 'a resolved thread is not counted' 0 "$touched_gate_script" \
    "$(set_of "$(review security clean)")" \
    'required: security' '[{"isResolved":true},{"isResolved":true}]' 'unresolved thread'

check 'no threads at all is not an unresolved thread' 0 "$touched_gate_script" \
    "$(set_of "$(review security clean)")" \
    'required: security' '[]' 'unresolved thread'

check 'a thread node that is not an object refuses to report a count it did not measure' 2 "$touched_gate_script" \
    "$(set_of "$(review security clean)")" 'measured nothing' '["x"]'

check 'a renamed isResolved field is refused rather than silently counted as zero' 2 "$touched_gate_script" \
    "$(set_of "$(review security clean)")" 'measured nothing' '[{"resolved":false}]'

check 'an isResolved that is not a boolean is refused rather than silently counted as zero' 2 "$touched_gate_script" \
    "$(set_of "$(review security clean)")" 'measured nothing' '[{"isResolved":"maybe"}]'

check 'no dimension has posted on a mixed diff, so both required dimensions are named' 3 "$money_and_gate" '[]' \
    'waiting for invariants security'

check 'every required dimension posted and clean is a pass, not a pending round' 0 "$money_and_gate" \
    "$(set_of "$(review invariants clean)" "$(review security clean)")" \
    'required: invariants security' '[]' 'pending:'

check 'a blocked verdict is a refusal even while another required dimension has not posted' 1 "$money_and_gate" \
    "$(set_of "$(review invariants blocked)")" \
    'reports verdict blocked' '[]' 'pending:'

check 'a malformed verdict is a refusal, not a pending round' 1 "$touched_gate_script" \
    "$(set_of "$(review security cleann)")" \
    'is not one of the verdicts' '[]' 'pending:'

printf '\n%s passed, %s failed\n' "$pass" "$fail"

ran=$((pass + fail))
if [ "$ran" -ne "$cases_this_suite_runs" ]; then
    printf 'census: %s cases ran and this file is written to run %s. Deleting a whole case block left no trace\n' \
        "$ran" "$cases_this_suite_runs" >&2
    printf 'before this line existed: the printed count, the exit status and the Gradle suite were all unchanged,\n' >&2
    printf 'on the suite every merge rests on. Move this number in the commit that adds or removes a case.\n' >&2
    fail=$((fail + 1))
fi
duplicate_names=$(printf '%s' "$names" | sort | uniq -d)
if [ -n "$duplicate_names" ]; then
    printf 'census: two cases share a name, so the count cannot tell them apart and one can be lost behind the\n' >&2
    printf 'other:\n%s\n' "$duplicate_names" >&2
    fail=$((fail + 1))
fi

[ "$fail" -eq 0 ]
