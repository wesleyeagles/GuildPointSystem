# Resource history API

`GET /api/resources/history?map=11&after=0&limit=500`

Requires the existing bearer login and an approved, complete, active Administrator/Leader account.
Being on the in-game resource allowlist is not sufficient. Maps: 2, 10, 11.
`after` must be nonnegative; `limit` is 1–1000 (default 500).

Response: `{ map, nextCursor, hasMore, entries }`. Each entry contains:
`revision`, `kind`, `id` (spawn database ID), `serial` (game identity), `name`,
`tier`, `x`, `z`, `foundAt`, `foundBy`, `diedAt`.
Dates are UTC ISO-8601, coordinates use the game client's X/Z convention.

Start with `after=0`, save/upsert entries by spawn `id`, then persist `nextCursor`
atomically with those entries. Continue while `hasMore`; save one cursor per map.
Poll again from that cursor after reconnecting. Replaying a page is safe.
Never treat the number of history entries as the number of spawns: a spawn can
appear again when its death is recorded, with a new revision and the same ID.

Migration V122 backfills every existing spawn, including old deaths, as `SNAPSHOT`.
New observations emit `FOUND`; the first recorded death emits `DIED` even when
the spawn ID is older than previously downloaded records. There is no 90-second
history cutoff. No existing spawn/death data is deleted or rewritten.

This is a change feed returning the **current state** of each changed spawn,
not immutable event payloads. An older FOUND entry may already contain diedAt.
Tier/name labels reflect current catalog data; catalog reclassification itself
does not emit spawn changes. Ignored/unknown mobs are included for historical
analysis, so clients should filter by tier/name as appropriate.

Writers acquire a database row lock per map before changing resources. Revisions
are assigned in that same transaction, preventing concurrent commits from hiding
older revisions behind an already persisted cursor. Empty polls and map projection
updates do not take the lock. No DLL/protocol update is needed; /game and /spawns
responses remain unchanged.

V122 installs a database trigger for inserts and death updates, so capture also
continues during a rolling deployment or application rollback. A correction or
clearing of diedAt also emits DIED; always use the current diedAt value rather
than interpreting the event kind alone as proof of death. The backfill holds a
write-blocking table lock until the trigger is installed in the same transaction.

Deploy the backend with V122 before using this endpoint. The documented Coolify
flow deploys main after merge; verify the actual deployment/health separately.
Keeping V122 installed preserves collection if application code is rolled back;
the older version simply will not expose the new history endpoint. Database
deletion/restore or removing the trigger requires a separate recovery review.

This exposes stored evidence, not proof of respawn. Legacy records do not retain
the client observation time, world/session identity, or whether death was inferred
from disappearance. Offline recovery can retrieve reports submitted by other
clients; events observed by nobody cannot be reconstructed.
