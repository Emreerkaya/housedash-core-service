# 9. The call-out fee is a fifth of the accepted total, capped

## Status

Accepted, 2026-09-29. Records decision D147 from the project's design record, which amended invariant I2.

## Context

Invariant I2 says an accepted quote is binding. Its original form promised a Nester who declines a revised quote a full refund. That left a tasker who drove to the address, arrived inside the geofence, found the work bigger than described and stopped to ask, with nothing for the trip. D147 amended the promise: the Nester who declines a revision after a geofenced arrival pays a call-out fee, and the rest is refunded.

The fee had to be a number the tasker cannot influence. A revised quote is written by the tasker after arrival, so a fee based on it would let the tasker write their own fee, and any revision above the cap would collect the cap. The original accepted total was fixed before the tasker arrived and was already authorised by the Nester, so it needs no new consent and is computable the moment the Nester declines.

## Decision

The fee is a percentage of the original accepted total, rounded half up to the cent, and capped.

`PERCENT_OF_THE_ACCEPTED_TOTAL` in `CallOutFee` stays at 20.

`CAP_CENTS` in `CallOutFee` stays at 2500.

In integer arithmetic over minor units the fee is `min((acceptedTotalCents * 20 + 50) / 100, 2500)`. The `+ 50` is the rounding and is load-bearing: without it the fee truncates, which underpays the tasker by a cent on two totals in every five, in the platform's favour.

The right example for the rounding is $90.03, not $90.01. A fifth of an integer number of cents has a fractional part of exactly 0, .2, .4, .6 or .8 of a cent, so at twenty percent no accepted total ever produces an exact half cent, and half-up never decides a tie. What the `+ 50` does is carry .6 and .8 up: $90.01 is 1800.2 cents and rounds to $18.00 with or without it, $90.02 is 1800.4 and does the same, but $90.03 is 1800.6 and pays $18.01 only because the `+ 50` is there. A record that pinned $90.01 as the rounding demonstration would pin nothing, because that row passes under truncation too. The test suite pins $90.03 to $18.01 and $90.02 to $18.00 side by side for that reason.

The cap binds from $124.98, not from $125.00. A fifth of 12,498 cents is 2,499.6, which rounds to 2,500 and meets the cap; 12,497 cents pays 2,499. From $125.00 the cap binds by the minimum alone, without the rounding, which is why the code short-circuits to the cap at that figure and never needs to multiply a total above it. Both edges are pinned.

The fee is owed on exactly one ending of a visit: the tasker arrived inside the geofence and the Nester declined the revised quote. It is not owed when the tasker did not show, when the tasker cancelled, when the tasker never reached arrival, or when the Nester cancelled before arrival. The domain refuses to settle a hold with a fee on any other ending rather than paying nothing quietly, so the wrong command is an error the caller sees.

## Consequences

The fee is computed, never typed. No screen and no adapter carries the figure; both derive it from the accepted total through the one function that owns the rounding.

The binding-quote screen must state the fee before acceptance. A charge a Nester first meets at cancellation is the surprise charge I2 exists to prevent.

The incentive this creates is real: quote low, arrive, revise high, collect the cap for ten minutes. The control is a revision-decline rate per tasker above which the fee stops paying out and the account is flagged, which is outside the ledger and not built here.

Whether the protection fee survives a part refund is an open owner decision. The ledger's standing invariant is that what leaves a hold never exceeds what was captured, which holds under either answer; the part-refund shape recorded today returns everything above the fee to the Nester.

## Alternatives considered

A fee on the revised total was rejected because the tasker writes the revision.

A flat fee was rejected because a fifth of a small job is a fair trip charge and a fifth of a large one is not, which is what the cap is for.

Truncating instead of rounding was rejected because it is systematically in the platform's favour and a rounding rule that always favours one side is a rule the other side is entitled to distrust.
