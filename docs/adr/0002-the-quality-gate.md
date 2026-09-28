# 2. The quality gate: green is a prerequisite for review, never a substitute

## Status

Accepted, 2026-09-28.

## Context

The handbook's review chapter requires two things before a merge, a green automated run and a human approval, and states that neither substitutes for the other. The second half cannot be implemented here as written. GitHub forbids an author from approving their own pull request, and this is a one-owner repository in which every review is authored under the owner's account. A required approval would be unsatisfiable, so decision D144 set required approvals to zero and moved the blocking force into a required status check.

Decision D154 made both repositories public after discovering that a Free-plan private repository gets no server-side branch protection at all. Public also made SonarCloud free. Decision D155 removed comments from code entirely: a comment explaining what belongs in a better name, and a comment explaining why belongs in a record like this one.

## Decision

A green gate is what earns a pull request the right to be reviewed. It never replaces the review.

The ruleset on `main` requires a pull request, linear history, resolution of every review thread, and the following checks on the head commit.

`commitlint` rejects any commit subject that is not a conventional commit with an issue reference. `ktlint` enforces formatting. `detekt` enforces smells and complexity and, through its `ForbiddenComment` rule, the no-comment rule from D155. `gitleaks` scans for secrets. `test` runs the unit and Testcontainers suites. `jacoco` fails below 90% line coverage in `domain/` and 75% overall. `agent-review` queries the pull request through GraphQL and fails unless a review exists whose recorded SHA equals the exact head commit and no review at that SHA carries a blocking verdict; a new push changes the SHA and invalidates every prior review.

SonarCloud is included because it is free on a public repository. detekt stays regardless, because it reads Kotlin idioms better than Sonar does. SQL injection is closed structurally rather than by scanner: an ArchUnit test bans string-interpolated SQL, so the pattern cannot survive the build.

Because there are no comments in code, there are also no suppressions. `@Suppress` is not used: a suppression without a stated reason is worse than the smell it hides, and the place a reason would go no longer exists. If detekt objects, the code changes.

Each gate was observed failing against a deliberately broken input before it was trusted: a non-conventional subject, a deleted test dropping coverage below threshold, a Spring import added to `domain/`, a blocking review left standing. A gate never seen to fail is not known to be a gate.

## Consequences

Two things are stated plainly because they are not closed. Cross-file taint analysis is thin; SonarCloud contributes some and detekt none, tolerable only because `domain/` has no I/O, which confines the surface to `adapters/`. And a repository administrator can merge past a red check. That is a visible act in the history, not a hole in the design. Calling this gate unskippable when it is not would be worse than calling it advisory.

What is given up is the second pair of human eyes the handbook assumes. Reviews here are structured and recorded, but authored under one account, and responsibility for what ships sits with one person. CODEOWNERS marks `money`, `quote`, `review` and `invariant` so that changes there announce themselves, which is the substitute for having someone else notice.

Also given up is the ability to wave through a false positive. When detekt is wrong, the code still changes, sometimes into a shape slightly worse than the one it objected to. The alternative is a growing set of unexplained exceptions.

A coverage number can be gamed by tests that run lines without proving anything; the threshold is a floor, not a claim, and review exists to catch tests that exercise rather than prove.

## Alternatives considered

Requiring one approval and satisfying it from a second account was rejected. It would manufacture the appearance of a review that does not exist.

Making `agent-review` advisory was rejected: a rule not enforced by a check is not a rule here.

Dropping detekt in favour of SonarCloud alone was rejected on idiom coverage. Allowing `@Suppress` with a written justification was rejected because the justification would be a comment, and D155 removed those.
