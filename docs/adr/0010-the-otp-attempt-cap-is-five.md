# 10. The OTP attempt cap is five

## Status

Accepted, 2026-09-29.

## Context

A six-digit numeric code is a million possibilities, which is minutes of
scripting against an endpoint with no cap at all. `platform-integration.md`
settles the shape of the mitigation: single-use, a ten-minute server-checked
TTL, and an attempt cap after which the code dies and a new one must be
requested. The TTL and single-use are structural (a `Duration` and a `Boolean`,
neither an upper-case constant the binding below can read); the attempt cap is
the one number in that mitigation, and it is a Nester-visible one: five wrong
tries and the code they were sent stops working, no matter how much of the
ten minutes remains.

Five is small enough that scripting the six digits before the cap trips is not
meaningfully easier than before, and large enough that a Nester who mistypes a
digit twice still gets in without asking for a new code.

## Decision

`ATTEMPT_CAP` stays at 5.

It is bound to its declaration in
`src/main/kotlin/com/housedash/domain/identity/IssuedOtp.kt` by the same test
that binds the limits ADR-0006 records: it reads every `const val` the domain
declares whose name is upper case and requires every one it publishes to be
bound to a record here, to defer to another constant that is, or to be named
in the test as deciding nothing a Nester sees.

## Consequences

Changing the cap means changing this record in the same commit, or the binding
test fails and names `ATTEMPT_CAP`.

A sixth wrong attempt, even one within the TTL and even the correct code
submitted a moment too late, is refused identically to the first: `IssuedOtp`
does not distinguish the reason a code is rejected, and the cap is what makes
"rejected" a state that outlives the code rather than a single failed
comparison.
