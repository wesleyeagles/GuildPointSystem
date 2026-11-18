import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { act, renderHook } from '@testing-library/react'
import type { ReactNode } from 'react'
import { expect, it, vi } from 'vitest'
import { apiClient } from '@/Shared/api/client'
import { auctionKeys, useCreateAuction } from './useAuction'

vi.mock('@/Shared/api/client', () => ({ apiClient: vi.fn() }))

it('creates directly at the selected time and releases the form before the list finishes refreshing', async () => {
  const client = new QueryClient()
  let finishRefresh!: () => void
  const refresh = new Promise<void>((resolve) => { finishRefresh = resolve })
  const invalidate = vi.spyOn(client, 'invalidateQueries').mockReturnValue(refresh)
  const endsAt = '2026-11-15T00:50:00.000Z'
  const auction = { id: 42, endsAt, fixedDeadline: false }
  vi.mocked(apiClient).mockResolvedValue(auction)
  const wrapper = ({ children }: { children: ReactNode }) =>
    <QueryClientProvider client={client}>{children}</QueryClientProvider>
  const { result } = renderHook(useCreateAuction, { wrapper })
  const items = [{ itemId: 7, quantity: 3 }]
  try {
    await act(async () => {
      await expect(result.current.mutateAsync({ durationMinutes: 15, targetEndsAt: endsAt, items })).resolves.toEqual(auction)
    })
    expect(apiClient).toHaveBeenCalledExactlyOnceWith('/auctions/target-end', {
      method: 'POST', body: { durationMinutes: 15, endsAt, items },
    })
    expect(invalidate).toHaveBeenCalledWith({ queryKey: auctionKeys.all, exact: true })
  } finally {
    finishRefresh()
  }
})
