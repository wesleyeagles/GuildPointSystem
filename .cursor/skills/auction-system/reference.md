# Auction System — Reference

## STOMP topics

| Topic | Payload examples |
|---|---|
| `/topic/auctions/{id}` | `BID`, `CHAT`, `TIMER`, `DOLE`, `TIE_BREAK_START`, `TIE_BREAK_RESULT`, `CLOSED` |
| `/topic/points/{memberId}` | `{ availableBalance, totalPoints, reservedPoints }` |
| `/topic/logs` | Auction created / closed audit entries |

App destination for chat/images: `/app/auctions/{id}/message` (pattern — match backend impl).

## Auction status flow

```
OPEN → (timer ends) → DOLE → (no bids) → CLOSED
                    ↘ (bid in DOLE) → OPEN (+10s)
OPEN → (timer ends, tied) → TIE_BREAK → CLOSED
```

## Tie-bid examples

| Current bid | Member available | Bid | Valid? |
|---|---|---|---|
| 500 | 500 | 500 | Yes (all-in tie) |
| 500 | 505 | 500 | No — must bid ≥501 |
| 500 | 600 | 501 | Yes |

## DOLE sequence

1. Timer reaches 0 without qualifying extension trigger.
2. Bot message `DOLE 1` — wait window.
3. If no bid → `DOLE 2` — wait window.
4. If no bid → `DOLE 3` — wait window.
5. If no bid → declare winner(s) or trigger tie-break.

Any bid during DOLE: extend auction by 10 seconds, return to active bidding.

## Roulette tie-break

1. Collect all members tied at winning bid amount.
2. Broadcast `TIE_BREAK_START` with participant list.
3. Generate random seed server-side; store in `auction.tie_break_seed`.
4. Frontend spins wheel ≥10s with deceleration; server announces winner.
5. Debit winner; log result; status `CLOSED`.

## Chat message types (`auction_message.type`)

- `TEXT` — member message
- `BID` — system bid entry (who, amount, when)
- `BOT` — DOLE announcements
- `IMAGE` — image upload in chat

## Reserved points example

Member: 500 total. Winning bid 300 on Auction A (open). Available on Auction B: **200**. If outbid on A, 300 released → available on B becomes 500.
