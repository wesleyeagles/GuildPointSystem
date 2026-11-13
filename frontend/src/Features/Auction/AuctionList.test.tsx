import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, expect, it, vi } from 'vitest'
import type { Auction } from '@/Domain/types/models'
import { AuctionListPage } from './AuctionList'

const state = vi.hoisted(() => ({
  admin: true,
  auctions: [] as Auction[],
  create: vi.fn(),
  startBatch: vi.fn(),
  now: undefined as number | undefined,
  lookup: vi.fn(),
  toast: vi.fn(),
}))
vi.mock('@/Shared/api/client', () => ({
  apiClient: state.lookup,
  ApiError: class extends Error {
    status: number
    constructor(message: string, status: number) {
      super(message)
      this.status = status
    }
  },
}))

vi.mock('@/Domain/Auction/hooks/useAuction', () => ({
  serverNow: () => state.now ?? Date.now(),
  useAuctions: () => ({ data: state.auctions }),
  useCreateAuction: () => ({ mutateAsync: state.create }),
  useDeleteAuction: () => ({}),
  useAuctionAdminAction: () => ({}),
  useStartAuctionBatch: () => ({ mutateAsync: state.startBatch }),
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
  state.startBatch.mockReset()
  state.now = undefined
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

it('uses the same Brasília deadline for new auctions and pending lots', async () => {
  state.admin = true
  state.now = Date.parse('2026-11-14T00:00:00Z') // 21h on November 13 in Brasília
  const endsAt = '2026-11-14T00:50:00.000Z'
  const pending = { ...sampleAuction(endsAt), status: 'SCHEDULED' as const }
  state.create.mockResolvedValue({ auction: pending, deadlineReached: true })
  state.auctions = [pending, { ...pending, id: 43 }, { ...pending, id: 44, status: 'OPEN' }]
  state.startBatch.mockResolvedValue(state.auctions.slice(0, 2))
  render(<MemoryRouter><AuctionListPage /></MemoryRouter>)
  fireEvent.change(screen.getByLabelText('Duração'), { target: { value: 'today' } })
  fireEvent.click(screen.getByRole('button', { name: 'Item 0' }))
  fireEvent.click(screen.getByRole('button', { name: 'Criar Leilão' }))
  await waitFor(() => expect(state.create).toHaveBeenCalledWith(expect.objectContaining({ targetEndsAt: endsAt })))
  fireEvent.click(screen.getByRole('button', { name: 'Iniciar todos até 21h50' }))
  await waitFor(() => expect(state.startBatch).toHaveBeenCalledWith({ auctionIds: [42, 43], endsAt }))
})

it('adds one unit per RF-ui click, even when the first acknowledgement fails', async () => {
  state.admin = true
  state.auctions = []
  state.lookup.mockResolvedValue({ content: [{ id: 35, name: 'Intense Staff', gameCode: 'iwtji40' }], totalPages: 1 })
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

it.each([false, true])('requires a unique exact Relic code across pages (duplicate: %s)', async (duplicate) => {
  state.admin = true
  state.auctions = []
  const relic = { id: 44, name: '(1GR) Freak Vafer Niffer Relic', gameCode: 'relic004' }
  state.lookup
    .mockResolvedValueOnce({ content: [{ ...relic, name: 'iyfig89', gameCode: 'other01' }], totalPages: 1 })
    .mockResolvedValueOnce({ content: [relic], totalPages: 2 })
    .mockResolvedValueOnce({ content: duplicate ? [{ ...relic, id: 45 }] : [], totalPages: 2 })
  let sent = false
  vi.stubGlobal('fetch', vi.fn(async (_url, options) => {
    if (JSON.parse(options.body).action === 'read') {
      if (sent) return Response.json([])
      sent = true
      return Response.json([{ id: 'c'.repeat(32), code: 'iyfig89' }])
    }
    return new Response(null, { status: 204 })
  }))
  render(<MemoryRouter><AuctionListPage /></MemoryRouter>)
  fireEvent.click(screen.getByRole('button', { name: 'Usar como destino na RF-ui' }))
  await waitFor(() => expect(screen.getByRole('status')).toHaveTextContent(
    duplicate ? 'Há mais de um cadastro para o código relic004.' : `+1 ${relic.name}`,
  ))
  expect(screen.getByRole('button', { name: duplicate ? 'Item 0' : 'Item 44' })).toBeVisible()
  expect(state.lookup.mock.calls.map(([path]) => path)).toEqual([
    '/items?search=iyfig89&page=0&size=24',
    '/items?search=relic004&page=0&size=24',
    '/items?search=relic004&page=1&size=24',
  ])
  expect(state.create).not.toHaveBeenCalled()
})

it.each([true, false])('copies the lot draft with its target deadline for admins only (%s)', async (admin) => {
  const endsAt = new Date(Date.now() + 15 * 60_000).toISOString()
  state.admin = admin
  state.auctions = [sampleAuction(endsAt)]
  state.create.mockResolvedValue({ auction: state.auctions[0], deadlineReached: true })
  render(<MemoryRouter><AuctionListPage /></MemoryRouter>)

  if (!admin) {
    expect(screen.queryByRole('button', { name: 'Copiar leilão' })).toBeNull()
    return
  }
  fireEvent.click(screen.getByRole('button', { name: 'Copiar leilão' }))
  expect(state.create).not.toHaveBeenCalled()
  expect(screen.getByLabelText('Duração')).toHaveValue('copied')
  expect(screen.getByRole('option', { name: /Até \d{2}\/\d{2}\/\d{4}.*Brasília/ })).toBeVisible()
  expect(screen.getByLabelText('Quantidade')).toHaveValue(3)

  fireEvent.click(screen.getByRole('button', { name: 'Item 17' }))
  fireEvent.click(screen.getByRole('button', { name: 'Criar Leilão' }))
  await waitFor(() => expect(state.create).toHaveBeenCalledWith({
    startsAt: undefined, startLater: undefined, durationMinutes: 30, targetEndsAt: endsAt, items: [{ itemId: 99, quantity: 3 }],
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
  expect(state.create.mock.calls[0][0]).not.toHaveProperty('targetEndsAt')
  expect(state.create.mock.calls[0][0].startLater).toBe(true)
})

it('duplicates a scheduled lot into a draft without starting or creating it', async () => {
  state.admin = true
  const startsAt = new Date(Math.ceil(Date.now() / 60_000) * 60_000 + 600_000).toISOString()
  state.auctions = [{
    ...sampleAuction(new Date(Date.parse(startsAt) + 1_800_000).toISOString()),
    status: 'SCHEDULED', startsAt,
  }]
  render(<MemoryRouter><AuctionListPage /></MemoryRouter>)
  const duplicate = screen.getByRole('button', { name: 'Duplicar' })
  expect(duplicate.nextElementSibling).toBe(screen.getByRole('button', { name: 'Excluir' }))
  fireEvent.click(duplicate)
  expect(state.create).not.toHaveBeenCalled()
  expect(screen.getByLabelText('Início')).toHaveValue('at')
  expect(screen.getByLabelText('Quantidade')).toHaveValue(3)
  fireEvent.submit(screen.getByLabelText('Início').closest('form')!)
  await waitFor(() => expect(state.create).toHaveBeenCalledWith({
    startsAt, startLater: undefined, durationMinutes: 30, items: [{ itemId: 17, quantity: 3 }],
  }))
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


it('shows each item quantity in its own badge, separate from numbers in the name', () => {
  state.admin = false
  const auction = sampleAuction(new Date(Date.now() + 60_000).toISOString())
  auction.items = [
    { itemId: 1, itemName: '500 CASH COIN', quantity: 10, imageUrl: null, gameIcon: null },
    { itemId: 2, itemName: 'Armor 50', quantity: 1, imageUrl: null, gameIcon: null },
    { itemId: 3, itemName: 'Rare Ore', quantity: 99, imageUrl: null, gameIcon: null },
  ]
  state.auctions = [auction]
  render(<MemoryRouter><AuctionListPage /></MemoryRouter>)
  for (const [name, label] of [['500 CASH COIN', '10 unidades'], ['Armor 50', '1 unidade'], ['Rare Ore', '99 unidades']]) {
    const title = screen.getByText(name)
    expect(screen.getByRole('img', { name: label })).toBeVisible()
    expect(title.parentElement?.querySelector(`[aria-label="${label}"]`)).not.toBeNull()
  }
  expect(screen.queryByText('500 CASH COIN x10')).toBeNull()
})
