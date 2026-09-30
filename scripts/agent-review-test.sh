#!/usr/bin/env bash
set -uo pipefail

here=$(cd "$(dirname "$0")" && pwd)
gate="${here}/agent-review.sh"
stub_dir=$(mktemp -d)
trap 'rm -rf "$stub_dir"' EXIT

cat > "${stub_dir}/gh" <<'STUB'
#!/usr/bin/env bash
files_json() {
    if [ -n "${STUB_FILES-}" ]; then
        printf '%s' "$STUB_FILES"
        return
    fi
    printf '%s' "${STUB_CHANGED-}" | jq -R -s -c 'split("\n") | map(select(length > 0)) | map({filename: .})'
}
if [ "${1:-}" = "pr" ] && [ "${2:-}" = "diff" ]; then
    if [ -n "${STUB_FAIL_DIFF-}" ]; then
        printf 'gh: could not determine the changed files\n' >&2
        exit 1
    fi
    printf '%s' "${STUB_CHANGED-}"
    [ -n "${STUB_CHANGED-}" ] && printf '\n'
    exit 0
fi
if [ "${1:-}" = "api" ] && printf '%s' "$*" | grep -qE '/pulls/[0-9]+/files'; then
    if [ -n "${STUB_FAIL_DIFF-}" ]; then
        printf 'gh: could not determine the changed files\n' >&2
        exit 1
    fi
    printf '[%s]' "$(files_json)"
    exit 0
fi
if [ "${1:-}" = "api" ] && printf '%s' "$*" | grep -qE '/pulls/[0-9]+$'; then
    if [ -n "${STUB_FAIL_DIFF-}" ]; then
        printf 'gh: could not determine the changed files\n' >&2
        exit 1
    fi
    printf '{"changed_files": %s}' "${STUB_COUNT:-$(files_json | jq 'length')}"
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
        STUB_CHANGED="$changed" STUB_FILES="${case_files-}" STUB_COUNT="${case_count-}" \
        STUB_REVIEWS="$reviews" STUB_THREADS="$threads" \
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

cases_this_suite_runs=136


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

check 'a changed path carrying a double quote is read by its real name, not a git-quoted spelling of it' 3 \
    'scripts/agent-review.sh' '[]' 'waiting for security'

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
    local name=$1 want=$2 want_text=$3
    names="${names}${name}"$'\n'
    shift 3
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
    if [ "$got" -ne "$want" ] || ! printf '%s' "$out" | grep -q "$want_text"; then
        printf 'FAIL %s: expected exit %s saying %s, got %s\n%s\n\n' "$name" "$want" "$want_text" "$got" "$out" >&2
        fail=$((fail + 1))
    else
        printf 'ok %s\n' "$name"
        pass=$((pass + 1))
    fi
}

live_money=src/main/kotlin/com/housedash/domain/money/EscrowHold.kt
live_workflow=.github/workflows/process-review.yml
live_gate=scripts/agent-review.sh
live_owners=CODEOWNERS
stray_mint=src/main/kotlin/com/housedash/adapters/inbound/http/MintController.kt

probe_repo 'a checkout where neither trigger pattern matches any file refuses to run at all' 2 \
    'can no longer see the path it guards' README.md

probe_repo 'a checkout with a money file but no gate-bearing file still refuses, on the workflows trigger' 2 \
    'trigger workflows' README.md "$live_money"

probe_repo 'a checkout whose domain layer moved refuses to run even though the rest is there' 2 \
    'trigger domain-money' README.md "$live_gate" "$live_workflow" "$live_owners"

probe_repo 'a checkout with every live trigger present and nothing else is not blind and passes' 0 \
    'requires no review dimensions' README.md "$live_money" "$live_workflow" "$live_gate" "$live_owners"

probe_repo 'domain/money renamed to domain/finance is refused even while a stray mint file still matches a sibling' 2 \
    'trigger domain-money' README.md src/main/kotlin/com/housedash/domain/finance/EscrowHold.kt \
    "$live_workflow" "$live_gate" "$live_owners" "$stray_mint"

probe_repo 'the gate script renamed is refused even while .github still matches a sibling' 2 \
    'trigger gate-script' README.md "$live_money" "$live_workflow" "$live_owners" scripts/review-gate.sh

probe_repo 'the workflows directory gone is refused even while the gate script still matches a sibling' 2 \
    'trigger workflows' README.md "$live_money" "$live_gate" "$live_owners"

probe_repo 'CODEOWNERS gone is refused even while every other gate-bearing file is present' 2 \
    'trigger codeowners' README.md "$live_money" "$live_workflow" "$live_gate"

probe_repo 'a domain/quote directory that now exists does not make the gate refuse, it just stops being unbuilt' 0 \
    'requires no review dimensions' README.md "$live_money" "$live_workflow" "$live_gate" "$live_owners" \
    src/main/kotlin/com/housedash/domain/quote/Quote.kt

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

files_of() { jq -cn '$ARGS.positional | map({filename: .})' --args "$@"; }

check_files() {
    local name=$1 want=$2 files=$3 legacy=$4 wanted_text=${5:-} unwanted_text=${6:-} count=${7:-}
    case_files=$files
    case_count=$count
    check "$name" "$want" "$legacy" '[]' "$wanted_text" '[]' "$unwanted_text"
    case_files=''
    case_count=''
}

check_paths() {
    local name=$1 want=$2 wanted_text=$3
    shift 3
    check_files "$name" "$want" "$(files_of "$@")" "$(printf '%s\n' "$@")" "$wanted_text"
}

alive_pinned=''

check_alive() {
    local trigger=$1 dimension=$2 sample=$3
    local name="trigger ${trigger} is alive on its own pinned sample ${sample}, and no sibling trigger claims that sample"
    names="${names}${name}"$'\n'
    alive_pinned="${alive_pinned}${trigger}"$'\n'
    local out got matched
    out=$(PATH="${stub_dir}:${PATH}" \
        STUB_CHANGED="$sample" STUB_REVIEWS='[]' STUB_THREADS='[]' \
        PR_NUMBER=1 HEAD_SHA="$sha" GITHUB_REPOSITORY=Emreerkaya/housedash-core-service \
        bash "$gate" 2>&1)
    got=$?
    matched=$(printf '%s\n' "$out" | grep -c '^matched: ')
    if [ "$got" -ne 3 ] || [ "$matched" -ne 1 ] \
        || ! printf '%s' "$out" | grep -q "^matched: ${trigger} (${dimension})$" \
        || ! printf '%s' "$out" | grep -q "waiting for ${dimension} at "; then
        printf 'FAIL %s: expected exit 3, exactly one matched line and it naming %s (%s); got exit %s and %s matched line(s)\n%s\n\n' \
            "$name" "$trigger" "$dimension" "$got" "$matched" "$out" >&2
        fail=$((fail + 1))
        return
    fi
    printf 'ok %s\n' "$name"
    pass=$((pass + 1))
}

check_alive domain-money invariants src/main/kotlin/com/housedash/domain/money/EscrowHold.kt
check_alive domain-quote invariants src/main/kotlin/com/housedash/domain/quote/Quote.kt
check_alive domain-booking invariants src/main/kotlin/com/housedash/domain/booking/Booking.kt
check_alive domain-review invariants src/main/kotlin/com/housedash/domain/review/Review.kt
check_alive mint-route invariants src/main/kotlin/com/housedash/adapters/inbound/http/MintController.kt
check_alive workflows security .github/workflows/process-review.yml
check_alive gate-script security scripts/agent-review.sh
check_alive codeowners security CODEOWNERS

check_paths 'trigger control-character is alive: one tab in an otherwise unguarded path requires security' 3 \
    'matched: control-character (security)' $'docs/ops/a\tb.md'

check_paths 'bypass 01: domain/Money/ in capitals requires invariants' 3 'waiting for invariants' \
    src/main/kotlin/com/housedash/domain/Money/EscrowHold.kt
check_paths 'bypass 02: Domain/money/ with a capital D requires invariants' 3 'waiting for invariants' \
    src/main/kotlin/com/housedash/Domain/money/EscrowHold.kt
check_paths 'bypass 03: domain/Quote/ in capitals requires invariants' 3 'waiting for invariants' \
    src/main/kotlin/com/housedash/domain/Quote/Quote.kt
check_paths 'bypass 04: domain/Booking/ in capitals requires invariants' 3 'waiting for invariants' \
    src/main/kotlin/com/housedash/domain/Booking/Booking.kt
check_paths 'bypass 05: domain/Review/ in capitals requires invariants' 3 'waiting for invariants' \
    src/main/kotlin/com/housedash/domain/Review/Review.kt
check_paths 'bypass 06: a directory inserted between domain/ and money/ requires invariants' 3 'waiting for invariants' \
    src/main/kotlin/com/housedash/domain/core/money/EscrowHold.kt
check_paths 'bypass 07: a multi-module prefix ahead of src/ requires invariants' 3 'waiting for invariants' \
    payment-module/src/main/kotlin/com/housedash/domain/money/EscrowHold.kt
check_paths 'bypass 08: a mint adapter whose name does not begin with Mint requires invariants' 3 'waiting for invariants' \
    src/main/kotlin/com/housedash/adapters/outbound/StripeMintAdapter.kt
check_paths 'bypass 09: MINTController in capitals requires invariants' 3 'waiting for invariants' \
    src/main/kotlin/com/housedash/adapters/inbound/http/MINTController.kt
check_paths 'bypass 10: .GITHUB/ in capitals requires security' 3 'waiting for security' \
    .GITHUB/workflows/x.yml
check_paths 'bypass 11: a raw newline splitting domain/money/ across two lines requires invariants and security' 3 \
    'waiting for invariants security' \
    $'src/main/kotlin/com/housedash/domain/mo\nney/EscrowHold.kt'
check_paths 'bypass 12: a raw newline splitting scripts/agent-review.sh across two lines requires security' 3 \
    'waiting for security' \
    $'scripts/agent-review\n.sh'
check_paths 'bypass 13: a nested directory and capitals together require invariants' 3 'waiting for invariants' \
    src/main/kotlin/com/housedash/domain/Core/Money/Foo.kt

check_files 'control 1: a file moved into domain/money/ from elsewhere requires invariants' 3 \
    '[{"filename":"src/main/kotlin/com/housedash/domain/money/Fee.kt","status":"renamed","previous_filename":"src/main/kotlin/com/housedash/app/Fee.kt"}]' \
    'src/main/kotlin/com/housedash/domain/money/Fee.kt' 'waiting for invariants'
check_paths 'control 2: a move shown as a deletion plus an addition requires invariants' 3 'waiting for invariants' \
    src/main/kotlin/com/housedash/app/Fee.kt src/main/kotlin/com/housedash/domain/money/Fee.kt
check_paths 'control 3: a path containing a space requires invariants' 3 'waiting for invariants' \
    'src/main/kotlin/com/housedash/domain/money/Escrow Hold.kt'
check_paths 'control 4: a path containing a double quote and a semicolon requires invariants' 3 'waiting for invariants' \
    'src/main/kotlin/com/housedash/domain/money/"; rm -rf /; echo ".kt'
check_paths 'control 5: a path containing a command substitution requires invariants and executes nothing' 3 \
    'waiting for invariants' \
    'src/main/kotlin/com/housedash/domain/money/$(id).kt'
check_paths 'control 6: the gate script under an underscore spelling requires security' 3 'waiting for security' \
    scripts/agent_review.sh
check_paths 'control 7: a lower-case codeowners file requires security' 3 'waiting for security' \
    codeowners

check_files 'a file renamed out of domain/money/ requires invariants through its previous name' 3 \
    '[{"filename":"docs/fee.kt","status":"renamed","previous_filename":"src/main/kotlin/com/housedash/domain/money/Fee.kt"}]' \
    'docs/fee.kt' 'waiting for invariants'

check_files 'a file renamed out of scripts/agent-review.sh requires security through its previous name' 3 \
    '[{"filename":"scripts/other.sh","status":"renamed","previous_filename":"scripts/agent-review.sh"}]' \
    'scripts/other.sh' 'waiting for security'

check_paths 'a domain/money path with a non-ASCII character is read by its real name and requires invariants' 3 \
    'waiting for invariants' \
    'src/main/kotlin/com/housedash/domain/money/Caf'$'\303\251''.kt'

check_paths 'a non-ASCII path outside every guarded prefix requires no dimension' 0 \
    'requires no review dimensions' 'docs/ops/caf'$'\303\251''.md'

check_paths 'a path carrying a DEL byte fails closed to security review' 3 'waiting for security' \
    $'docs/ops/a\177b.md'

check_paths 'a path carrying a C1 control character fails closed to security review' 3 'waiting for security' \
    $'docs/ops/a\302\205b.md'

check_paths 'a path carrying a carriage return fails closed to security review' 3 'waiting for security' \
    $'docs/ops/a\rb.md'

check_files 'a path carrying an escaped NUL fails closed to security review' 3 \
    '[{"filename":"docs/ops/a\u0000b.md"}]' 'docs/ops/ab.md' 'waiting for security'

check_files 'a listing shorter than the pull request changed refuses rather than pass on a partial diff' 2 \
    "$(files_of README.md)" 'README.md' 'refusing to pass on a partial diff' '' 3001

check_files 'a file listing that is an error object rather than a list refuses' 2 \
    '{"message":"Not Found"}' 'README.md' 'measured nothing' '' 1

check_files 'a file listing entry with no filename refuses' 2 \
    '[{"status":"added"}]' 'README.md' 'measured nothing' '' 1

wiring_paths=(
    Dockerfile
    .dockerignore
    .env.example
    scripts/deploy-cloud-run.sh
    scripts/supabase-verify.sh
    src/main/resources/application-supabase.yml
    src/main/kotlin/com/housedash/HouseDashService.kt
    config/detekt/detekt.yml
    src/test/kotlin/com/housedash/invariants/AbsenceTest.kt
    justfile
    docs/ops/supabase.md
)

for wiring in "${wiring_paths[@]}"; do
    check_paths "wiring file ${wiring} alone requires no dimension" 0 'requires no review dimensions' "$wiring"
done

check_paths 'a wiring diff shaped like pull request 63 requires no dimension and passes with no reviews' 0 \
    'this diff requires no review dimensions' "${wiring_paths[@]}"

check_paths 'the fifteen files pull request 63 actually changes require no dimension' 0 \
    'this diff requires no review dimensions' \
    .dockerignore .env.example .gitignore Dockerfile build.gradle.kts config/detekt/detekt.yml docs/ops/supabase.md \
    justfile scripts/deploy-cloud-run.sh scripts/supabase-verify.sh \
    src/integrationTest/kotlin/com/housedash/SupabaseProfileTest.kt \
    src/main/kotlin/com/housedash/HouseDashService.kt src/main/resources/application-supabase.yml \
    src/main/resources/application.yml src/test/kotlin/com/housedash/invariants/AbsenceTest.kt

check_paths 'a wiring diff with one guarded file added still requires exactly that file dimension' 3 \
    'waiting for invariants' "${wiring_paths[@]}" src/main/kotlin/com/housedash/domain/booking/Booking.kt

unbuilt_here=$(cd "${here}/.." && PATH="${stub_dir}:${PATH}" \
    STUB_CHANGED="$untouched" STUB_REVIEWS='[]' STUB_THREADS='[]' \
    PR_NUMBER=1 HEAD_SHA="$sha" GITHUB_REPOSITORY=Emreerkaya/housedash-core-service \
    bash "$gate" 2>&1 | sed -n 's/^note: trigger \([a-z-]*\) has no file in this checkout yet.*/\1/p' | sort | tr '\n' ' ')
names="${names}the triggers this checkout has no file for are exactly the pinned unbuilt set"$'\n'
if [ "$unbuilt_here" != "domain-booking domain-quote domain-review mint-route " ]; then
    printf 'FAIL the triggers this checkout has no file for are exactly the pinned unbuilt set: got %s\n' "${unbuilt_here:-none}" >&2
    fail=$((fail + 1))
else
    printf 'ok the triggers this checkout has no file for are exactly the pinned unbuilt set\n'
    pass=$((pass + 1))
fi

scratch=$(mktemp -d)
sed 's#(^|/)domain/(\[^/\]+/)\*money/#(^|/)domain/([^/]+/)*finance/#' "$gate" > "${scratch}/agent-review.sh"
names="${names}a trigger edited so it no longer matches its own pinned sample refuses to run"$'\n'
mutant_out=$(cd "${here}/.." && PATH="${stub_dir}:${PATH}" \
    STUB_CHANGED="$untouched" STUB_REVIEWS='[]' STUB_THREADS='[]' \
    PR_NUMBER=1 HEAD_SHA="$sha" GITHUB_REPOSITORY=Emreerkaya/housedash-core-service \
    bash "${scratch}/agent-review.sh" 2>&1)
mutant_got=$?
rm -rf "$scratch"
if ! cmp -s <(sed 's#(^|/)domain/(\[^/\]+/)\*money/#(^|/)domain/([^/]+/)*finance/#' "$gate") "$gate" \
    && [ "$mutant_got" -eq 2 ] && printf '%s' "$mutant_out" | grep -q 'trigger domain-money .* does not match its own pinned sample'; then
    printf 'ok a trigger edited so it no longer matches its own pinned sample refuses to run\n'
    pass=$((pass + 1))
else
    printf 'FAIL a trigger edited so it no longer matches its own pinned sample refuses to run: exit %s\n%s\n\n' "$mutant_got" "$mutant_out" >&2
    fail=$((fail + 1))
fi

script_triggers=$(sed -n "s/^    '\([a-z-]*\)::.*/\1/p" "$gate" | sort | tr '\n' ' ')
pinned_triggers=$(printf '%s' "$alive_pinned" | sort | tr '\n' ' ')
names="${names}every trigger the gate script defines has a pinned positive case and no pinned case names a trigger it lacks"$'\n'
if [ -z "$script_triggers" ] || [ "$script_triggers" != "$pinned_triggers" ]; then
    printf 'FAIL every trigger the gate script defines has a pinned positive case: the script defines [%s] and the suite pins [%s]\n' \
        "$script_triggers" "$pinned_triggers" >&2
    fail=$((fail + 1))
else
    printf 'ok every trigger the gate script defines has a pinned positive case and no pinned case names a trigger it lacks (%s)\n' "$script_triggers"
    pass=$((pass + 1))
fi

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
