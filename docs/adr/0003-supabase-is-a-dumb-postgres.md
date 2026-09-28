# 3. Supabase is a dumb Postgres

## Status

Accepted, 2026-09-28.

## Context

The service's database is Supabase's managed Postgres, chosen for its free tier. Supabase also ships an auto-generated REST API over every table and row-level security policies meant to make that API safe to call from a client holding an anonymous key. That is the Supabase-native model, and for many products the right one.

Four of this product's invariants are why it is not the right one here. An accepted price is binding and cannot change after acceptance. Money is held until the customer confirms the work and is released on nothing else. Reviews are double-blind, with neither side seeing the other's until both are in. Contact details are blocked until a booking exists. Decision D151 records the conclusion these force: invariants a client can reach with an anonymous key are not invariants.

## Decision

This service uses neither the generated API nor row-level security as its authorization layer. Every read and write goes through the Kotlin service, under a single database role whose credential lives in the deployment environment and nowhere else. No client, the iOS app included, ever holds a Supabase key.

The four invariants are workflows, not row predicates. "Money is released only after the customer confirms" is a sequence of state transitions with a guard; it cannot be expressed as a policy on who may select or update which rows. A policy that let a client update escrow holds at all would let it release money; one that forbade it would make the product unusable. The rule can only live in the aggregate that owns the transition, and the only guarantee it is reached is for the service to be the sole path to the row.

Schema changes are made only through Flyway migrations, the `V*.sql` files in this repository, applied by the service on startup. They are never made through the Supabase dashboard, CLI or MCP server, even when that would work. A schema not derivable from the migration files breaks the next deploy silently: a migration finds its column already there, or a dashboard-created column is missing in the next environment, and neither shows until a request hits it.

Row-level security may exist as defence in depth, never as the layer that decides whether an action is allowed. That decision is made in `domain/` and nowhere else.

One live hazard is recorded because it will recur. Supabase's official guidance, installed in this workspace as a local reference, advises row-level security as the security model. That advice is correct for a Supabase-native application and wrong for this one; it is not mistaken, it is answering a different question.

## Consequences

What is given up is most of what makes Supabase attractive. There is no instant API, so every endpoint is written by hand in Kotlin. There is no client SDK in the iOS app, no realtime subscriptions, no Supabase Auth wired to policies. Authentication and authorization are entirely the service's problem. The dashboard's schema editor, genuinely convenient, is off limits.

A single database role means a compromised service credential reaches every table. That is mitigated by keeping the credential out of the repository, which gitleaks enforces, and accepted because the alternative spreads the invariants across policies no test in this repository can exercise.

What is gained is that Supabase becomes replaceable: nothing in the service knows it is talking to Supabase rather than any other Postgres, so outgrowing the free tier changes a connection string.

## Alternatives considered

A Supabase-native design, with the iOS app calling the generated API under RLS, was rejected on the four invariants above. RLS answers "may this user touch this row"; the product needs "may this transition happen now", which is a different question.

Using the generated API for reads only was rejected because the read paths carry two of the four invariants: a review must not be readable before both are in, and contact details must not be readable before booking.

Making RLS the authorization layer with the service as its only client was rejected because the rules would then live in SQL policies the exhaustive tests cannot reach.

A different managed Postgres was not rejected on principle; the free tier decided it, and this record keeps that choice cheap to reverse.
