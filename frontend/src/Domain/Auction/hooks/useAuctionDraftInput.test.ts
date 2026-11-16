import { beforeEach, expect, it, vi } from 'vitest'
import { apiClient } from '@/Shared/api/client'
import { lookupDraftItem } from './useAuctionDraftInput'

vi.mock('@/Shared/api/client', async (original) => ({
  ...await original<typeof import('@/Shared/api/client')>(), apiClient: vi.fn(),
}))
const api = vi.mocked(apiClient)
const coin = { id: 17, name: '500 CASH COIN', type: 'MISC', gameCode: null }
const potion = { id: 58, name: 'Bishop Stamina Potion 800', type: 'POTION', gameCode: 'ipcss04' }
beforeEach(() => api.mockReset())

it('resolves the cash token instead of the legacy potion, searching every page', async () => {
  api.mockResolvedValueOnce({ content: [potion], totalPages: 2 })
    .mockResolvedValueOnce({ content: [coin], totalPages: 2 })
  await expect(lookupDraftItem('ipcss04', new AbortController().signal)).resolves.toEqual(coin)
  expect(api.mock.calls.map(([path]) => path)).toEqual([
    '/items?search=500+CASH+COIN&page=0&size=24', '/items?search=500+CASH+COIN&page=1&size=24',
  ])
})

it.each([
  [potion], [{ ...coin, name: '5000 CASH COIN' }],
  [{ ...coin, gameCode: 'other01' }], [{ ...coin, type: 'WEAPON' }], [],
])('rejects unconfirmed coin matches: %j', async (...content) => {
  api.mockResolvedValue({ content, totalPages: 1 })
  await expect(lookupDraftItem('ipcss04', new AbortController().signal)).rejects.toMatchObject({ status: 404 })
})

it('rejects duplicate cash token entries across pages', async () => {
  api.mockResolvedValueOnce({ content: [coin], totalPages: 2 })
    .mockResolvedValueOnce({ content: [{ ...coin, id: 18 }], totalPages: 2 })
  await expect(lookupDraftItem('ipcss04', new AbortController().signal)).rejects.toMatchObject({ status: 409 })
})

it('also accepts the corrected catalog code and preserves other exact matches', async () => {
  api.mockResolvedValueOnce({ content: [{ ...coin, gameCode: 'ipcss04' }], totalPages: 1 })
  await expect(lookupDraftItem('IPCSS04', new AbortController().signal)).resolves.toMatchObject({ id: 17 })
  api.mockResolvedValueOnce({ content: [{ id: 35, name: 'Staff', gameCode: 'iwtji40' }], totalPages: 1 })
  await expect(lookupDraftItem('iwtji40', new AbortController().signal)).resolves.toMatchObject({ id: 35 })
})
