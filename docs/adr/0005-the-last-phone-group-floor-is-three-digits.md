# 5. The last group of a phone-shaped run must hold at least three digits

## Status

Accepted, 2026-09-29.

## Context

The I7 contact filter reads a run of digit groups and decides whether it is dialable. One of its
conditions is a floor on the width of the final group: a run whose last group holds fewer digits
than the floor is not a phone number. `FEWEST_DIGITS_IN_THE_LAST_PHONE_GROUP` in
`domain/shared/NumberShape.kt` is that floor and it is 3.

The floor decides exactly one family of inputs: a cued run grouped three-three-two-two. At 3,
`my number is 917 555 01 99 if you need it` is accepted although it is explicitly cued and plainly
dialable. At 2, that sentence is caught — and `call me about the 60 40 30 20 10 split` is refused,
along with ten other ordinary repair sentences. The uncued spelling of `917 555 01 99` is accepted
at either value, because the uncued arm separately requires a four-digit line group.

Measured across thirty inputs on two builds of the filter: moving the floor from 3 to 2 flips
sixteen inputs to rejection. Five are dialable numbers gained. Eleven are ordinary sentences lost.
That is roughly two false positives per true positive.

The deeper fact, and the reason this is a decision rather than a tuning exercise: no threshold can
make this distinction. `917 555 01 99` and `120 150 180 90` are the same shape — four groups, each
six digits or fewer, ten or eleven digits in total, ending in a group of two. Nothing about the
widths, the separators or the total separates a phone number from a quantity list here. The cued
three-three-two-two family is a residual of the mechanism, not a number waiting to be tuned.

## Decision

`FEWEST_DIGITS_IN_THE_LAST_PHONE_GROUP` stays at 3.

Rejecting a legitimate description is a worse product failure than leaking a phone number is a
business one (D158), so a change that buys one catch at the price of two refusals is a net loss.
Both directions are pinned by tests whose names say the behaviour is a decision and not a
specification, and both pins have been confirmed load-bearing by setting the constant to 2 and
watching exactly one test fail per direction.

## Consequences

A Nester who writes their number as `917 555 01 99` and says "my number is" gets through this
filter. That is a stated gap, rowed in the corpus, and it is the cost of the choice rather than an
oversight.

Anyone tempted to move this number should read the second paragraph above first. The constant is a
proxy for a distinction this mechanism cannot make, so moving it trades one class of error for
another rather than reducing error. Closing the three-three-two-two family needs a different
mechanism, not a different number, and the filter's value is bounded anyway: a spelled-out number
(`nine one seven five five five zero one nine nine`) is open and always will be.

Changing the value requires changing this record, the two pinned tests, and the corpus rows that
move with it, in the same commit.
