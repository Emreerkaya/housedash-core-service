# 2. The quality gate: green is a prerequisite for review, never a substitute

## Status

Accepted, 2026-09-28.

## Context

The handbook's review chapter requires two things before a merge, a green automated run and a human approval, and states that neither substitutes for the other. The second half cannot be implemented here as written. GitHub forbids an author from approving their own pull request, and this is a one-owner repository in which every review is authored under the owner's account. A required approval would be unsatisfiable, so decision D144 set required approvals to zero and moved the blocking force into a required status check.

Decision D154 made both repositories public after discovering that a Free-plan private repository gets no server-side branch protection at all. Public also made SonarCloud free. Decision D155 removed comments from code entirely: a comment explaining what belongs in a better name, and a comment explaining why belongs in a record like this one.

## Decision

A green gate is what earns a pull request the right to be reviewed. It never replaces the review.

The ruleset on `main` requires a pull request, linear history, resolution of every review thread, and the following checks on the head commit.

`commitlint` rejects any commit subject that is not a conventional commit with an issue reference. `ktlint` enforces formatting. `detekt` enforces smells and complexity and, through its `ForbiddenComment` rule, the no-comment rule from D155. `gitleaks` scans for secrets. `test` runs the unit and Testcontainers suites. `jacoco` fails below 90% line coverage in `domain/` and 75% overall. `agent-review` queries the pull request through GraphQL and fails unless a review exists whose recorded SHA equals the exact head commit and no review at that SHA carries a blocking verdict; a new push changes the SHA and invalidates every prior review. Only reviews by an account with write access to this repository count, because a trailer in a review body is a machine-readable claim and anyone with a GitHub account can post one.

Two parts that decisions D145 and D154 counted as belonging to this gate are not wired into it, and are named here rather than assumed. SonarCloud is free on a public repository and would contribute the cross-file taint analysis those decisions wanted, but no workflow invokes it and neither does `just sonar`. SQL injection was to be closed structurally by an ArchUnit test banning string-interpolated SQL; no such test exists, and there is no persistence code yet for it to govern. Both are intention, not a gate, on the same terms as the one below, and each is verified in the change that first makes it real. detekt stays regardless of what Sonar would add, because it reads Kotlin idioms better.

Because there are no comments in code, there are also no suppressions. `@Suppress` is not used: a suppression without a stated reason is worse than the smell it hides, and the place a reason would go no longer exists. If detekt objects, the code changes. A source scan enforces that on every pull request, and a second check fails a build whose integration-test source set is empty so that an integration suite with nothing in it cannot report green. Both ride inside the `detekt` and `test` contexts rather than publishing contexts of their own, because a required context that has never reported blocks every pull request forever.

A gate never seen to fail is not known to be a gate, so each one is broken on purpose before it is trusted. Seven have been: a non-conventional commit subject, a `//` comment in Kotlin, malformed formatting, a deliberately failed assertion, an `@Suppress` planted on a declaration, a review trailer naming the right dimension at the right SHA from an account with no write access, and a domain class reaching into the application layer. Each returned a non-zero exit and named the offence.

One has not, and is listed here rather than assumed. The coverage threshold could not be exercised while no production code existed to leave uncovered. It is verified in the change that first makes it possible, and until then it is an intention, not a gate.

## Consequences

Two things are stated plainly because they are not closed. There is no cross-file taint analysis at all; detekt contributes none and SonarCloud is not wired in, tolerable only because `domain/` has no I/O, which confines the surface to `adapters/`. And a repository administrator can merge past a red check. That is a visible act in the history, not a hole in the design. Calling this gate unskippable when it is not would be worse than calling it advisory.

What is given up is the second pair of human eyes the handbook assumes. Reviews here are structured and recorded, but authored under one account, and responsibility for what ships sits with one person. CODEOWNERS marks `money`, `quote`, `review` and `invariant` so that changes there announce themselves, which is the substitute for having someone else notice.

Also given up is the ability to wave through a false positive. When detekt is wrong, the code still changes, sometimes into a shape slightly worse than the one it objected to. The alternative is a growing set of unexplained exceptions.

A coverage number can be gamed by tests that run lines without proving anything; the threshold is a floor, not a claim, and review exists to catch tests that exercise rather than prove.

## Alternatives considered

Requiring one approval and satisfying it from a second account was rejected. It would manufacture the appearance of a review that does not exist.

Making `agent-review` advisory was rejected: a rule not enforced by a check is not a rule here.

Dropping detekt in favour of SonarCloud alone was rejected on idiom coverage. Allowing `@Suppress` with a written justification was rejected because the justification would be a comment, and D155 removed those.
