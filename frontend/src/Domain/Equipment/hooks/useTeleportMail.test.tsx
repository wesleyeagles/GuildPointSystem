import { apiClient } from '@/Shared/api/client'
import { act, renderHook, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { afterEach, describe, expect, it, vi } from 'vitest'
import type { ReactNode } from 'react'
import { useTeleportMail, type TeleportRequest } from './useTeleportMail'

vi.mock('@/Shared/api/client', () => ({ apiClient: vi.fn() }))
afterEach(() => { vi.unstubAllGlobals(); vi.mocked(apiClient).mockReset() })
const data: TeleportRequest = { id: '1'.repeat(32), sender: 'meow', recipients: ['Alpha'], items: [{ kind: 'ether', quantity: 3 }] }
function hook() {
  const queries = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })
  return renderHook(() => useTeleportMail(false), { wrapper: ({ children }: { children: ReactNode }) => <QueryClientProvider client={queries}>{children}</QueryClientProvider> })
}
describe('Ponte de envio em lote', () => {
  it('checks stock before starting and uses the same batch id', async () => {
    const fetch = vi.fn().mockResolvedValue({ ok: true, json: async () => ({ id: data.id }) })
    vi.stubGlobal('fetch', fetch)
    const { result } = hook()
    await act(async () => { await result.current.start.mutateAsync(data) })
    expect(fetch.mock.calls.map(c => c[0])).toEqual(['http://127.0.0.1:15183/teleport-plan', 'http://127.0.0.1:15183/teleport-start'])
    expect(JSON.parse(fetch.mock.calls[1][1].body)).toEqual({ id: data.id })
    expect(fetch.mock.calls[0][1]).toMatchObject({ credentials: 'omit', redirect: 'error', targetAddressSpace: 'loopback' })
  })
  it('preserves the subject in the immutable plan and starts only that batch id', async () => {
    const fetch = vi.fn().mockResolvedValue({ ok: true, json: async () => ({ id: data.id }) })
    vi.stubGlobal('fetch', fetch)
    const { result } = hook()
    await act(async () => { await result.current.start.mutateAsync({ ...data, subject: 'Teleportes' }) })
    expect(JSON.parse(fetch.mock.calls[0][1].body)).toMatchObject({ ...data, subject: 'Teleportes' })
    expect(JSON.parse(fetch.mock.calls[1][1].body)).toEqual({ id: data.id })
  })
  it('does not start on a rejected preflight, and never retries a timed out start', async () => {
    const fetch = vi.fn().mockResolvedValueOnce({ ok: false, json: async () => ({ message: 'Estoque insuficiente' }) })
    vi.stubGlobal('fetch', fetch)
    const { result } = hook()
    await act(async () => { await expect(result.current.start.mutateAsync(data)).rejects.toThrow('Estoque insuficiente') })
    expect(fetch).toHaveBeenCalledOnce()
    fetch.mockReset().mockResolvedValueOnce({ ok: true, json: async () => ({ id: data.id }) }).mockRejectedValueOnce(new Error('Timeout'))
    await act(async () => { await expect(result.current.start.mutateAsync(data)).rejects.toThrow('Timeout') })
    await waitFor(() => expect(result.current.start.isPending).toBe(false))
    expect(fetch).toHaveBeenCalledTimes(2)
  })
})

it('revalidates event claims before any game request and refuses renamed/missing recipients', async () => {
  const fetch=vi.fn().mockResolvedValue({ok:true,json:async()=>({id:data.id})});vi.stubGlobal('fetch',fetch)
  const rewardData={...data,items:[{kind:'cash499' as const,quantity:10}],reward:{name:'PBs Major',claims:{Alpha:[11]}}}
  vi.mocked(apiClient).mockResolvedValue({name:'PBs Major',recipients:[{memberId:1,nickname:'Renamed',claimIds:[11]}]})
  const {result}=hook()
  await act(async()=>{await expect(result.current.start.mutateAsync(rewardData)).rejects.toThrow('participantes mudaram')})
  expect(fetch).not.toHaveBeenCalled()
  vi.mocked(apiClient).mockResolvedValue({name:'PBs Major',recipients:[{memberId:1,nickname:'Alpha',claimIds:[11]}]})
  await act(async()=>{await result.current.start.mutateAsync(rewardData)})
  expect(fetch).toHaveBeenCalledTimes(2)
  expect(JSON.parse(fetch.mock.calls[0][1].body)).toMatchObject(rewardData)
})
