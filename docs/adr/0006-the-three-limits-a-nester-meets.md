# 6. The three limits a Nester meets when describing a case

## Status

Accepted, 2026-09-29.

## Context

Three numbers in the domain decide what a Nester is allowed to send, and all
three are visible to them as a refusal rather than as a preference. None of them
carried a record, while a filter constant one Nester in a thousand ever reaches
carried ADR-0005. That is the wrong way round: a limit the product enforces on
ordinary use is more worth writing down than a limit on an evasion.

**How many photographs a case may carry.** A case is a request for a quote from
someone who has not seen the room. Four photographs is enough for a tasker to
price the commonest jobs and few enough that the upload works on a phone in a
basement with one bar of signal. It is a product judgement and not a storage
one.

**How short a description may be.** A description shorter than a short sentence
is not a description; it is a subject line, and a tasker cannot quote from it.
Twenty characters is about four words. Below that the case wastes the tasker's
reply, which is the scarcest thing on the platform.

**How much text the contact filter will read at all.** The filter normalises and
scans the whole string before any length check can run, so the cap is a bound on
work rather than a bound on meaning, and it is also the description's own
maximum. Two thousand characters is far more than any description this product
has seen and small enough that the scan stays in microseconds.

## Decision

`MAX_PHOTOS` stays at 4.

`MIN_LENGTH` stays at 20.

`MOST_CHARACTERS_THE_GUARD_READS` stays at 2000.

Each is a product decision, so each is written here rather than inferred from the
tests that enforce it, and each is bound to its declaration by a test that reads
both and fails when they part. The binding is the same mechanism ADR-0005 uses,
and it now covers every constant the domain publishes: a new one arrives as a
failure naming it, not as a line nobody reviews.

## Consequences

Changing any of the three means changing this record in the same commit, because
the test that reads both will otherwise fail and name the constant.

A Nester with five photographs of one leak has to choose four. A Nester who
writes "tap drips" is asked for more. Both are refusals the product makes on
purpose, and both are worth revisiting with real use rather than by argument
here.

What the binding does not deliver is the reasoning above. It checks that the
number in this file is the number in the code, applied to every sentence that
says what a constant stays at, and that no line naming a constant names another
number. Prose that describes a limit without naming its constant is as unchecked
as any other prose, which is why the reasoning sits in Context where a reader
can disagree with it.
