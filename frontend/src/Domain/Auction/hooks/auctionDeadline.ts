import type { Auction } from '@/Domain/types/models'

const MINUTE_MS = 60_000
const AUCTION_DURATIONS = [1440, 720, 360, 180, 60, 30, 15, 5, 1]
const EXTENSION_MINUTES = [60, 30, 15, 5, 1]

export async function createAuctionNearDeadline(
  deadline: number,
  now: number,
  create: (minutes: number) => Promise<Auction>,
  extend: (id: number, minutes: number) => Promise<Auction>,
) {
  const remaining = deadline - now
  if (!(remaining > 0)) throw new Error('O horário de encerramento já passou.')

  const duration = AUCTION_DURATIONS.find((minutes) => minutes * MINUTE_MS <= remaining) ?? 1
  let auction = await create(duration)
  let minutesLeft = Math.ceil((deadline - Date.parse(auction.endsAt)) / MINUTE_MS)

  try {
    while (minutesLeft > 0 && auction.status === 'OPEN') {
      const minutes = EXTENSION_MINUTES.find((option) => option <= minutesLeft) ?? 1
      auction = await extend(auction.id, minutes)
      minutesLeft -= minutes
    }
  } catch {
    // Creation already succeeded. Never retry a write whose response may have been lost.
    return { auction, deadlineReached: false }
  }
  const difference = Date.parse(auction.endsAt) - deadline
  return { auction, deadlineReached: auction.status === 'OPEN' && difference >= 0 && difference < MINUTE_MS }
}
