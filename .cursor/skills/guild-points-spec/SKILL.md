---
name: guild-points-spec
description: Navigate the Guild Points RF Online spec, map sections to code modules, and track implementation checklist. Use when implementing features, clarifying requirements, or verifying business rules against especificacao-guild-points-rf-online.md.
---

# Guild Points Spec Navigation

**Source:** [especificacao-guild-points-rf-online.md](../../especificacao-guild-points-rf-online.md)

## Section → module map

| Spec § | Topic | Frontend | Backend |
|---|---|---|---|
| 3 | Auth, Discord, approval | `Features/Auth` | `auth/` |
| 4 | Profile, point adjust | `Features/Member` | `member/`, `points/` |
| 5 | Objetivos, Eventos | `Features/Event` | `objective/`, `event/` |
| 6 | Audit logs | `Features/Logs` | `log/` |
| 7 | Dashboard, ranking | `Features/Dashboard` | `member/` |
| 8 | Itens, Talics | `Features/Item` | `item/` |
| 9 | Leilões | `Features/Auction` | `auction/` |
| 10–11 | Architecture | `Features/`, `Shared/`, `Domain/` | all packages |
| 12 | Database | — | `db/migration/` |

## When to read the spec

1. Before implementing any feature — read the matching § entirely.
2. On ambiguity — spec wins over assumptions; flag §13 open items to user.
3. On review — verify behavior matches spec tables and rules.

## Implementation checklist

Copy and track:

```
- [ ] §3 Auth — email, Discord modal, PENDENTE gate
- [ ] §4 Profile — edits logged; Admin point adjust with reason
- [ ] §5 Objetivos — NORMAL/LIMITADO daily cap
- [ ] §5 Eventos — WS toast, password, deny reversal
- [ ] §6 Logs — all AuditLogType events, real-time
- [ ] §7 Dashboard — podium + active events
- [ ] §8 Items — 4 types, image required, talic validation
- [ ] §9 Auctions — bids, reserve, DOLE, tie-break roulette
- [ ] §11 Security — @PreAuthorize, atomic claims/bids
- [ ] §12 DB — Flyway, JSONB, indexes
```

## Open decisions (§13)

Cast seed, leader count policy, no-bid auction timeout, upload size limits — confirm with user before hardcoding.
