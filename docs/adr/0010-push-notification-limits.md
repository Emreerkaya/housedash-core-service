# 10. Push notification retry count and content limits

## Status

Accepted, 2026-09-29.

## Context

The notification outbox retries a failed delivery with backoff before giving up, and a queued notification's
title and body are bounded before they ever reach APNs. Both are product decisions a Nester's device shows the
effect of, like the other bounds this project already records, not incidental to the implementation.

## Decision

MAX_ATTEMPTS stays at 5.

An outbox entry that fails five times moves to DEAD rather than retrying forever; a stale token or a transient
APNs failure is normal, and it is not allowed to occupy the queue indefinitely on that Nester's behalf.

TITLE_MIN stays at 1.

TITLE_MAX stays at 128.

A push title beyond this length is truncated by the platform anyway, so the domain refuses it at the point a
notification is queued rather than silently truncating content a Nester wrote.

BODY_MIN stays at 1.

BODY_MAX stays at 500.

A push body can carry an address or a person's name, and five hundred characters sits comfortably inside APNs'
own payload ceiling once the rest of the `aps` dictionary is accounted for.

## Consequences

A notification whose title or body falls outside these bounds is refused where it is queued
(`QueuedNotification.queue`), not discovered later when APNs rejects the payload or truncates it on-device.

## Alternatives considered

Leaving the limits unbounded and letting APNs truncate was rejected: a notification that silently loses its
last sentence on-device is a defect nobody wrote a test for, whereas a refusal at the queue boundary is visible
and testable.
