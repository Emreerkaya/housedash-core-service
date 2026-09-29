# 8. POST /cases is idempotent on a key the client chooses

## Status

Accepted, 2026-09-29.

## Context

`POST /cases` is the first endpoint on this service and the first one the iOS intake screens call. Creation over a mobile network is retried: the request goes out, the answer is lost, and the app has no way to tell a request that never arrived from one whose answer did. A retry that creates a second case is a Nester looking at two identical problems in their list and two sets of invitations going out for one dripping tap.

The endpoint therefore has to be safe to repeat, and the repeat has to be recognisable. Nothing in the request body is a reliable identity: two genuinely different cases can carry the same description and the same photographs, and a fingerprint over the body would refuse the second one. Nothing on the server is either, because there is no authentication yet and no session to hang an attempt on.

## Decision

The client names the attempt. `POST /cases` requires an `Idempotency-Key` header, and an intake is identified by the pair of that key and the owner it names. A request with no key is refused with `400` and `IntakeKeyAbsent`; a key outside the identifier character shape is refused with `400` and `MalformedIntakeKey`. The iOS app generates one key per intake, not one per attempt, and reuses it for every retry of that intake.

The first request for a pair creates the case and answers `201`. Every later request for the same pair answers `200` with the identifier of the case the first one created, and creates nothing. The pair is claimed by one atomic operation on the repository rather than by a read followed by a write, because a read followed by a write has a window in which two concurrent retries both find nothing and both store. The port says so in its one method name, `storeUnlessAlreadyStored`, and the test that can see the difference is the one that races sixteen threads at a single key: a read-then-write implementation passes every sequential test in the suite and fails only that one.

The same key from a different owner is a different intake and creates a second case. Two Nesters cannot collide by choosing the same key, and a key is not a capability: presenting somebody else's key returns nothing of theirs.

Errors are typed values rather than exceptions, and the controller maps them to statuses in one place. A description the domain refuses is `400`, except a description carrying a contact detail, which is `422`: the request was understood and the content was refused, and the client needs to tell those apart because only one of them is worth re-prompting for. A case belonging to somebody else is `403`. The one `500` this endpoint can answer with is the service failing to mint its own case identifier, which is the server's fault and not the client's.

## Consequences

Retries are safe and the client carries the obligation. An app that generates a fresh key per attempt gets a case per attempt, and no server rule can catch that, so the key's lifetime is part of the client contract rather than something this service can enforce.

The mapping from attempt to case is held in memory in this slice, so it does not survive a restart and does not hold across instances. A retry that lands after a restart, or on a second instance, creates a second case. That is the whole reason the persistence work is its own change: the pair has to be stored beside the case, under a unique constraint, so the database refuses the duplicate rather than a map in one process. Until then this endpoint is idempotent within the life of one instance and no further.

A duplicate request still mints a case identifier and builds an aggregate before the repository discards it. That costs a wasted identifier and nothing else, and it keeps the claim on the attempt in one atomic step instead of two.

## Alternatives considered

A fingerprint over the request body was rejected because two different cases can have the same body. It would refuse a genuine second intake, which is worse than the duplicate it prevents.

Letting the client choose the case identifier was rejected because the identifier is part of the model and appears in every later reference to the case. A client that chooses it can also guess someone else's.

Answering `201` to a repeat, rather than `200`, was rejected because the client cannot then tell that its retry was recognised, which is the one thing it most needs to know.

Making the key optional, with duplicate protection only when it is present, was rejected on the same reasoning as ADR-0003's: a rule a client can decline is not a rule.
