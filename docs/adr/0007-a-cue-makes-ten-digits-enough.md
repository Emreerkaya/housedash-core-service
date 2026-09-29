# 7. A cue makes ten digits enough, and nine not enough

## Status

Accepted, 2026-09-29.

## Context

The I7 contact filter reads a run of digit groups. Without a cue it asks for a
dialable shape: ten digits ending in a four-digit line group behind a group of
three or four. With a cue nearby — `call`, `ring`, `my mobile` and the rest of
the list in `domain/shared/NumberShape.kt` — it asks for a count of digits and
for two limits a cue does not lower, and `DIGITS_IN_A_DIALABLE_NUMBER` in
`domain/shared/NumberShape.kt` is that count.

The count prices the whole cue arm, which is why it belongs in a record rather
than in a commit message. At ten, `call me when the 300 600 900 mm boards
arrive` is accepted and `call me on 917 555 0199` is caught. At nine, eleven
ordinary repair sentences in the corpus are refused, all of them lists of three
three-digit quantities beside a cue word — measured, not estimated.

The same count is the floor the arm that reads a run spread one digit to a
separator uses, so `9,1,7,5,5,5,0,1,9,9` is a phone number and
`replace washers 1, 2, 3, 4, 5, 6, 7, 8, 9` is not. That was nine until D292
measured what nine costs: it refused three spellings of one repair sentence and
accepted a dialable number one digit longer, and separator spelling decided
which. One count for both arms is the point.

## Decision

`DIGITS_IN_A_DIALABLE_NUMBER` stays at 10.

A cue lowers the shape requirement and must not lower the length requirement as
well, because the two together are all that stands between a cued quantity list
and a phone number.

What a cue lowers is how long a single group may be. What it does not lower is
how many groups there may be, and how long the last one must be. Those two are
what a spacing list and a quantity list are recognised by — eight groups of one
digit, or a run ending in a two-digit tail — while a group longer than a phone
group holds is a real number typed without one of its spaces. Reading the whole
grouping limit before the count stored `call me on 917 5550199 about the leak`,
ten digits in two groups beside a cue, because its second group is seven digits
long. Rejecting a legitimate description is a worse product
failure than leaking a phone number is a business one (D158), and nine buys a
small number of catches for eleven refusals.

## Consequences

A cued run of nine digits gets through, spelled any way. That is a stated gap
and it is rowed in the corpus rather than left in prose.

Changing the value requires changing this record, the tests whose names say ten
and not nine, and the corpus rows that move with it, in the same commit.
