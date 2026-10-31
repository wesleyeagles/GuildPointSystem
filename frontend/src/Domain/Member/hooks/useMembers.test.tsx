import { act, cleanup, renderHook, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import { afterEach, expect, it, vi } from 'vitest'
import { useUpdateProfile } from './useMembers'

const api = vi.hoisted(() => vi.fn())
vi.mock('@/Shared/api/client', () => ({ apiClient: api }))
afterEach(() => { cleanup(); api.mockReset() })

it('invalidates the cached quest eligibility after changing the profile level', async () => {
  const client = new QueryClient({ defaultOptions: { mutations: { retry: false } } })
  const key = ['quests', 1, '2026-10-30', true]
  client.setQueryData(key, { expected: [] })
  api.mockResolvedValue({ id: 1, level: 46 })
  function Wrapper({ children }: { children: ReactNode }) {
    return <QueryClientProvider client={client}>{children}</QueryClientProvider>
  }
  const view = renderHook(() => useUpdateProfile(), { wrapper: Wrapper })
  await act(async () => {
    await view.result.current.mutateAsync({ id: 1, nickname: 'Player', raceId: 2, classId: 1, level: 46 })
  })
  await waitFor(() => expect(client.getQueryState(key)?.isInvalidated).toBe(true))
  expect(api).toHaveBeenCalledWith('/members/1/profile', {
    method: 'PUT', body: { nickname: 'Player', raceId: 2, classId: 1, level: 46 },
  })
  client.clear()
})
