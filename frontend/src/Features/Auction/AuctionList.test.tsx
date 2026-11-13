import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, expect, it, vi } from 'vitest'
import type { Auction } from '@/Domain/types/models'
import { AuctionListPage } from './AuctionList'

const state = vi.hoisted(() => ({
  admin: true,
  auctions: [] as Auction[],
  create: vi.fn(),
  lookup: vi.fn(),
  toast: vi.fn(),
}))
vi.mock('@/Shared/api/client', () => ({
  apiClient: state.lookup,
  ApiError: class extends Error { status = 404 },
}))

vi.mock('@/Domain/Auction/hooks/useAuction', () => ({
  serverNow: () => Date.now(),
  useAuctions: () => ({ data: state.auctions }),
  useCreateAuction: () => ({ mutateAsync: state.create }),
  useDeleteAuction: () => ({}),
  useAuctionAdminAction: () => ({}),
}))
vi.mock('@/Domain/Discord/hooks/useDiscordMembers', () => ({ useDiscordBySiteMember: () => new Map() }))
vi.mock('@/Features/Auth/contexts/AuthContext', () => ({ useAuthContext: () => ({ hasRole: () => state.admin }) }))
vi.mock('@/Shared/ui/components/AppToast/AppToast', () => ({ useAppToast: () => ({ showToast: state.toast }) }))
vi.mock('./components/AuctionItemPicker/AuctionItemPicker', () => ({
  AuctionItemPicker: ({ itemId, onChange }: { itemId: number; onChange: (id: number) => void }) => (
    <button type="button" onClick={() => onChange(99)}>Item {itemId}</button>
  ),
}))

afterEach(() => {
  cleanup()
  state.create.mockReset()
  state.lookup.mockReset()
  vi.unstubAllGlobals()
})

function sampleAuction(endsAt: string): Auction {
  return {
    id: 42, status: 'OPEN', durationMinutes: 30, createdAt: new Date().toISOString(), endsAt, startsAt: null, pausedRemainingMs: null,
    currentBid: 20, winnerId: null, winnerNickname: null, leaderId: null, leaderNickname: null,
    tiedCount: 0, tied: false, tiedMemberIds: [], tiedMemberNicknames: [], dolePhase: 0,
    tieBreakPhase: 0, version: 1, serverTime: new Date().toISOString(),
    items: [{ itemId: 17, quantity: 3, itemName: 'Item original', imageUrl: null, gameIcon: null }],
  }
}

it('adds one unit per RF-ui click, even when the first acknowledgement fails', async () => {
  state.admin = true
  state.auctions = []
  state.lookup.mockResolvedValue({ id: 35, name: 'Intense Staff' })
  const events = [{ id: 'a'.repeat(32), code: 'iwtji40' }, { id: 'b'.repeat(32), code: 'iwtji40' }]
  let acknowledgements = 0
  vi.stubGlobal('fetch', vi.fn(async (_url, options) => {
    const { action } = JSON.parse(options.body)
    if (action === 'read') return Response.json(events)
    if (action === 'ack' && ++acknowledgements === 1) return new Response(null, { status: 503 })
    return new Response(null, { status: 204 })
  }))
  render(<MemoryRouter><AuctionListPage /></MemoryRouter>)
  fireEvent.click(screen.getByRole('button', { name: 'Usar como destino na RF-ui' }))
  await waitFor(() => expect(acknowledgements).toBe(2), { timeout: 2000 })
  expect(screen.getByRole('button', { name: 'Item 35' })).toBeVisible()
  expect(screen.getByLabelText('Quantidade')).toHaveValue(2)
  expect(state.lookup).toHaveBeenCalledTimes(1)
  expect(state.create).not.toHaveBeenCalled()
  fireEvent.click(screen.getByRole('button', { name: 'Criar Leilão' }))
  await waitFor(() => expect(state.create).toHaveBeenCalledWith({
    startsAt: undefined, startLater: undefined, durationMinutes: 15, items: [{ itemId: 35, quantity: 2 }],
  }))
})

it.each([true, false])('copies the lot draft with the exact deadline for admins only (%s)', async (admin) => {
  const endsAt = new Date(Date.now() + 15 * 60_000).toISOString()
  state.admin = admin
  state.auctions = [sampleAuction(endsAt)]
  render(<MemoryRouter><AuctionListPage /></MemoryRouter>)

  if (!admin) {
    expect(screen.queryByRole('button', { name: 'Copiar leilão' })).toBeNull()
    return
  }
  fireEvent.click(screen.getByRole('button', { name: 'Copiar leilão' }))
  expect(state.create).not.toHaveBeenCalled()
  expect(screen.getByLabelText('Duração')).toHaveValue('copied')
  expect(screen.getByRole('option', { name: /Até .*Brasília/ })).toBeVisible()
  expect(screen.getByLabelText('Quantidade')).toHaveValue(3)

  fireEvent.click(screen.getByRole('button', { name: 'Item 17' }))
  fireEvent.click(screen.getByRole('button', { name: 'Criar Leilão' }))
  await waitFor(() => expect(state.create).toHaveBeenCalledWith({
    startsAt: undefined, startLater: undefined, durationMinutes: 30, endsAt, items: [{ itemId: 99, quantity: 3 }],
  }))
})


it('drops the copied deadline when switching to a scheduled start', async () => {
  state.admin = true
  state.auctions = [sampleAuction(new Date(Date.now() + 60_000).toISOString())]
  render(<MemoryRouter><AuctionListPage /></MemoryRouter>)
  fireEvent.click(screen.getByRole('button', { name: 'Copiar leilão' }))
  fireEvent.change(screen.getByLabelText('Início'), { target: { value: 'later' } })
  fireEvent.click(screen.getByRole('button', { name: 'Criar sem iniciar' }))
  await waitFor(() => expect(state.create).toHaveBeenCalled())
  expect(state.create.mock.calls[0][0]).not.toHaveProperty('endsAt')
  expect(state.create.mock.calls[0][0].startLater).toBe(true)
})

it('rejects copying an expired auction without replacing the draft', () => {
  state.admin = true
  state.auctions = [sampleAuction(new Date(Date.now() - 60_000).toISOString())]
  render(<MemoryRouter><AuctionListPage /></MemoryRouter>)
  fireEvent.click(screen.getByRole('button', { name: 'Copiar leilão' }))
  expect(screen.getByRole('button', { name: 'Item 0' })).toBeVisible()
  expect(state.create).not.toHaveBeenCalled()
})

it.each([409, 'malformed'])('disconnects on a replaced draft or invalid bridge payload (%s)', async (reply) => {
  state.admin = true
  state.auctions = []
  vi.stubGlobal('fetch', vi.fn(async (_url, options) => {
    const { action } = JSON.parse(options.body)
    if (action === 'read') return reply === 409 ? new Response(null, { status: 409 }) : Response.json([{ id: 'bad', code: '../bad' }])
    return new Response(null, { status: 204 })
  }))
  render(<MemoryRouter><AuctionListPage /></MemoryRouter>)
  fireEvent.click(screen.getByRole('button', { name: 'Usar como destino na RF-ui' }))
  await waitFor(() => expect(screen.getByRole('status').textContent).toMatch(/deixou de ser o destino|Resposta inválida/))
  expect(state.lookup).not.toHaveBeenCalled()
  expect(screen.getByRole('button', { name: 'Item 0' })).toBeVisible()
})

it('closing the page aborts reading and releases the bridge destination', async () => {
  state.admin = true
  state.auctions = []
  const fetch = vi.fn(async (_url, options) => JSON.parse(options.body).action === 'read' ? Response.json([]) : new Response(null, { status: 204 }))
  vi.stubGlobal('fetch', fetch)
  const view = render(<MemoryRouter><AuctionListPage /></MemoryRouter>)
  fireEvent.click(screen.getByRole('button', { name: 'Usar como destino na RF-ui' }))
  await waitFor(() => expect(fetch).toHaveBeenCalledTimes(2))
  view.unmount()
  expect(JSON.parse(fetch.mock.calls.at(-1)![1].body).action).toBe('close')
  expect(fetch.mock.calls[0][1].signal.aborted).toBe(true)
})
