# Guild Points System — Agent Guide

RF Online guild points web app. **Spec (source of truth):** [especificacao-guild-points-rf-online.md](./especificacao-guild-points-rf-online.md)

## Stack

React + React Query + SCSS | Spring Boot 3 + PostgreSQL | STOMP/SockJS

Monorepo: `frontend/` · `backend/`

## Cursor rules (`.cursor/rules/`)

| Rule | Scope |
|---|---|
| `project-overview.mdc` | Always — stack, roles, atomic ops |
| `business-rules.mdc` | Always — PENDENTE, LIMITADO, auctions, etc. |
| `react-frontend.mdc` | `frontend/**/*.{ts,tsx,scss}` |
| `spring-backend.mdc` | `backend/**/*.java` |
| `database-postgres.mdc` | `backend/**/db/migration/**/*` |

## Cursor skills (`.cursor/skills/`)

| Skill | Use for |
|---|---|
| `guild-points-spec` | Spec navigation, section→module map, checklist |
| `auction-system` | Bids, availableBalance, DOLE, tie-break, WS |
| `talic-validation` | Weapon/armor talic tables and rules |
| `feature-scaffold` | New feature frontend + backend templates |

## Dev commands

```bash
# Frontend (port 5173)
cd frontend && npm install && npm run dev

# Backend (port 8080) — requires PostgreSQL guild_points DB
cd backend && mvn spring-boot:run
```

## Suggested build order

1. Auth + PENDENTE approval + Discord profile modal
2. Member profile + audit logs + dashboard
3. Objetivos/Eventos (LIMITADO daily cap, WS toasts)
4. Items + talic validation
5. Auctions (reserve balance, DOLE, roulette)

Do not edit the plan file.
