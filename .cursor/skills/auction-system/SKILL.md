---
name: auction-system
description: Implements and validates Guild Points auction rules — availableBalance, DOLE sequence, tie bids, roulette tie-break, and STOMP topics. Use when building or debugging auction features, bids, chat, or real-time auction state.
---

# Auction System

Full rules: spec §9. Details: [reference.md](reference.md).

## Core formulas

```
availableBalance(member) = totalPoints − Σ(winningBid in other OPEN auctions)
```

- Bid amount ≤ availableBalance (not just total points).
- When outbid, release reserved points → push `/topic/points/{memberId}` immediately.

## Bid validation (backend)

1. Auction status `OPEN` (or `DOLE` during countdown).
2. Amount > currentBid, **unless** tie rule applies (bid == currentBid **and** amount == member's full availableBalance).
3. If member can exceed current bid, equal bids are rejected.
4. Atomic: lock auction row + validate balance in same transaction.

## Time rules

- Bid with ≤15s remaining → reset timer to **25s**.
- Timer hits 0 with no late bid → bot posts `DOLE 1`, `DOLE 2`, `DOLE 3` sequentially.
- Bid during any DOLE → **+10s** extension.

## End states

- Single winner → debit via `LEILAO` modality, status `CLOSED`, audit log.
- N-way tie → status `TIE_BREAK`, roulette ≥10s, random seed (`tie_break_seed`), then close.

## Frontend

- Subscribe: `/topic/auctions/{id}` (bids, chat, timer, DOLE, roulette).
- Subscribe: `/topic/points/{memberId}` for available balance updates.
- Quick-bid buttons add fixed increment to current bid; disable when insufficient availableBalance.

## Backend packages

`auction/` — controller, service (bid logic, timer/DOLE state), repository. Consider Redis for timer; persist final state to Postgres.
