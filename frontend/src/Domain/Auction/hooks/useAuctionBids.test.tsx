import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, renderHook, waitFor } from '@testing-library/react'
import type { ReactNode } from 'react'
import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { apiClient, ApiError } from '@/Shared/api/client'
import { useAuctionBids } from './useAuction'

vi.mock('@/Shared/api/client', async (importOriginal) => ({
  ...await importOriginal<typeof import('@/Shared/api/client')>(), apiClient: vi.fn(),
}))
beforeEach(() => { vi.mocked(apiClient).mockReset() })
afterEach(cleanup)
function wrapper() {
  const client = new QueryClient({ defaultOptions: { queries: { retryDelay: 0, gcTime: 0 } } })
  return ({ children }: { children: ReactNode }) => <QueryClientProvider client={client}>{children}</QueryClientProvider>
}
it.each([403, 404, 405])('does not retry permanent HTTP %s errors', async (status) => {
  vi.mocked(apiClient).mockRejectedValue(new ApiError('Unavailable', status))
  const { result } = renderHook(() => useAuctionBids(91, 0, true), { wrapper: wrapper() })
  await waitFor(() => expect(result.current.isError).toBe(true))
  expect(apiClient).toHaveBeenCalledExactlyOnceWith('/auctions/91/bids?page=0')
})
it('still retries one temporary server failure', async () => {
  const page = { content: [], totalElements: 0, totalPages: 0, number: 0, size: 20 }
  vi.mocked(apiClient).mockRejectedValueOnce(new ApiError('Temporary failure', 503)).mockResolvedValueOnce(page)
  const { result } = renderHook(() => useAuctionBids(91, 0, true), { wrapper: wrapper() })
  await waitFor(() => expect(result.current.data).toEqual(page))
  expect(apiClient).toHaveBeenCalledTimes(2)
})
it('never requests history while the window is closed', () => {
  renderHook(() => useAuctionBids(91, 0, false), { wrapper: wrapper() })
  expect(apiClient).not.toHaveBeenCalled()
})
