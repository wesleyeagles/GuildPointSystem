import { expect, it, vi } from 'vitest'
import type { Auction } from '@/Domain/types/models'
import { createAuctionNearDeadline } from './auctionDeadline'

const deadline = Date.parse('2026-11-14T00:50:00Z')

it.each([
  ['2026-11-13T20:46:14Z', 180, [60, 1, 1, 1, 1], 14_000],
  ['2026-11-14T00:49:59Z', 1, [], 59_000],
  ['2026-11-14T00:45:00Z', 5, [], 0],
] as const)('uses supported durations and extensions for a start at %s', async (start, initial, extensions, lateBy) => {
  const now = Date.parse(start)
  let endsAt = now
  const addMinutes = (minutes: number) => {
    endsAt += minutes * 60_000
    return { id: 42, status: 'OPEN', endsAt: new Date(endsAt).toISOString() } as Auction
  }
  const create = vi.fn(async (minutes: number) => addMinutes(minutes))
  const extend = vi.fn(async (_id: number, minutes: number) => addMinutes(minutes))
  const result = await createAuctionNearDeadline(deadline, now, create, extend)
  expect(create.mock.calls).toEqual([[initial]])
  expect(extend.mock.calls).toEqual(extensions.map((minutes) => [42, minutes]))
  expect(result.deadlineReached).toBe(true)
  expect(Date.parse(result.auction.endsAt)).toBe(deadline + lateBy)
})

it('reports the created lot without retrying a failed extension', async () => {
  const now = deadline - 6 * 60_000
  const auction = { id: 42, status: 'OPEN', endsAt: new Date(deadline - 60_000).toISOString() } as Auction
  const create = vi.fn().mockResolvedValue(auction)
  const extend = vi.fn().mockRejectedValue(new Error('Network error'))
  await expect(createAuctionNearDeadline(deadline, now, create, extend))
    .resolves.toEqual({ auction, deadlineReached: false })
  expect(create).toHaveBeenCalledTimes(1)
  expect(extend.mock.calls).toEqual([[42, 1]])
})
