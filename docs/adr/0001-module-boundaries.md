# 1. Module boundaries: one service, cut where the repositories will split

## Status

Accepted, 2026-09-28.

## Context

The engineering handbook this project takes its practices from describes an organisation of roughly nine tribes running more than a hundred microservices behind GraphQL federation, with dual production clusters and GitOps promotion. That machinery exists so that many teams can ship without blocking each other. HouseDash has one owner. Decision D140 in the project's design record settled the response: adopt the handbook's practices in full, because each costs almost nothing, and defer its topology until a second service exists.

Adopted: conventional commits, CODEOWNERS, ADR governance, quality gates, the test pyramid, a hexagonal layout, a typed error hierarchy. Deferred: federation, Kubernetes, GitOps, multi-environment promotion.

## Decision

There is one deployable, `housedash-core-service`, with three layers under `com.housedash`.

`domain/` holds one package per bounded context: `case`, `matching`, `quote`, `booking`, `money`, `review` and `licence`. Each owns its aggregates, its state machine and its sealed error type. These package lines are where the future repositories will split, so nothing in one context reaches into another's internals.

Alongside them sits one package that is not a bounded context. `domain/shared` holds the kernel every context needs and none of them owns: the result type, the plain-text and free-text rules, the contact-and-payment filter that enforces invariant I7, the identifier shape and its flaw type, and the nester identifier. A nester is an actor in every context — case, matching, quote, booking, money and review — so the identifier belongs to none of them; leaving it in `case` would make `matching` import from `case` to name its own participant, which the slice rule forbids. The rule that keeps it honest is that **`domain/shared` never names a type belonging to a context** — a shared kernel that knows what a `CaseError` is has stopped being shared and has become a second home for `case`. Each context maps the kernel's failures onto its own sealed error type at the point of use.

`app/` holds the use cases, one class each. A use case is the transaction boundary: it loads aggregates, calls domain commands, persists the result.

`adapters/` holds the inbound REST controllers with their request and response types, and the outbound Postgres repositories with their Flyway migrations. DTOs never expose domain types, so the wire format and the model version independently.

The load-bearing rule is that `domain/` imports nothing. No Spring, no Jackson, no SQL, no `java.time.Clock`, no `UUID.randomUUID()`. Time and identifiers arrive as parameters on the command that needs them. An ArchUnit test enforces the ban. It is in place from the first slice, while the layer is three files, because retrofitting it is the expensive version.

The rule earns its cost through how the invariants are tested. Decision D150 records that the nine product invariants are proven by exhaustive state-space exploration rather than by example: an explorer applies every command in every reachable combined state of case, invitation, quote, booking, escrow hold and review pair, asserting all nine after every transition. That only works if a transition is a pure function of its inputs. A domain that reads the wall clock or draws its own identifiers is not enumerable, and one that needs a Spring context cannot run millions of transitions in seconds. The import ban is what lets "does any path violate the binding-price rule" be answered by search instead of by spot-check.

Two split points are named now. Diagnosis becomes `housedash-diagnosis-api`, a Python and FastAPI service, because model work is not Kotlin work. Notifications become `housedash-notification-worker`, an asynchronous consumer, because they are not request-serving. Both are reached from `app/` through ports, so lifting them out changes an adapter, not a use case.

## Consequences

What is given up is convenience and independence. Every use case threads a clock and an id generator through to the domain, and every aggregate is mapped to a row by hand, because persistence annotations on domain classes would be an import. That mapping is the price of a domain that can be instantiated in a plain unit test.

There is one deploy unit and one database, so a bad migration in `booking` takes `quote` down with it, and nothing scales or releases separately. That is acceptable while one person releases everything anyway.

The explorer proves what lives in `domain/` and nothing else. Adapters are covered by integration tests against a real Postgres, slower and less complete; the seam between the two is where the bugs the explorer cannot see will live.

## Alternatives considered

Following the handbook's micro-repository rule from day one was rejected: seven repositories, seven pipelines and a federation gateway are coordination machinery for teams that do not exist.

A conventional layered Spring application with annotated entities was rejected because it makes the domain depend on the container. The invariants could still be tested, but only by example, and exhaustive exploration would be off the table.

Gradle subprojects per bounded context was deferred rather than rejected: package boundaries plus an ArchUnit test give the same guarantee today at lower build cost, and subprojects become worthwhile when a context first needs its own consumer.
