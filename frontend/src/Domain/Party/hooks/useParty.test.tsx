import { act, cleanup, renderHook } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import { afterEach, expect, it, vi } from 'vitest'
import { partyKeys, usePartyBoard } from './useParty'

const api = vi.hoisted(() => vi.fn())
vi.mock('@/Shared/api/client', () => ({ apiClient: api }))
vi.mock('@/Shared/websocket/socketClient', () => ({
  subscribeTopicEffect: () => () => {}, onSocketReconnect: () => () => {},
}))
vi.mock('@/Shared/utils/serverClock', () => ({ serverNow: () => Date.now(), trackServerTime: () => {} }))
afterEach(() => { cleanup(); vi.useRealTimers(); api.mockReset() })

it('refreshes once at the next queue boundary without periodic polling', async () => {
  vi.useFakeTimers()
  vi.setSystemTime(new Date('2026-10-31T20:59:59Z'))
  const client = new QueryClient({ defaultOptions: { queries: { staleTime: Infinity, retry: false, gcTime: Infinity } } })
  const board = { parties: [], lfg: [], my: {}, serverTime: new Date().toISOString(),
    queueAccess: { open: false, manual: false, nextChangeAt: '2026-10-31T21:00:00Z' } }
  client.setQueryData(partyKeys.board, board)
  api.mockImplementation(async () => ({ ...board, serverTime: new Date().toISOString(),
    queueAccess: { open: true, manual: false, nextChangeAt: '2026-11-01T09:00:00Z' } }))
  function Wrapper({ children }: { children: ReactNode }) { return <QueryClientProvider client={client}>{children}</QueryClientProvider> }
  renderHook(() => usePartyBoard(), { wrapper: Wrapper })
  await act(async () => { await vi.advanceTimersByTimeAsync(1000) })
  expect(api).not.toHaveBeenCalled()
  await act(async () => { await vi.advanceTimersByTimeAsync(150) })
  expect(api).toHaveBeenCalledTimes(1)
  await act(async () => { await vi.advanceTimersByTimeAsync(60_000) })
  expect(api).toHaveBeenCalledTimes(1)
  client.clear()
})
