import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, expect, it, vi } from 'vitest'
import type { Auction } from '@/Domain/types/models'
import { AuctionDetailPage } from './AuctionDetail'

const state = vi.hoisted(() => ({ admin: true, auction: {} as Auction, confirm: vi.fn(), member: undefined as object | undefined, bid: vi.fn(), others: [] as Auction[] }))
vi.mock('@/Domain/Auction/hooks/useAuction', () => ({
  serverNow: () => Date.now(),
  useAuction: () => ({ data: state.auction }),
  useAuctions: () => ({ data: state.others ?? [] }),
  useAuctionTieBreak: () => undefined,
  useCancelAuction: () => ({}),
  useAuctionBids: () => ({}),
  useCancelAuctionBid: () => ({}),
  useConfirmAuctionDelivery: () => ({ mutateAsync: state.confirm }),
  useDeleteAuction: () => ({}),
  useAuctionAdminAction: () => ({}),
  useExtendAuction: () => ({}),
  usePlaceBid: () => ({ mutateAsync: state.bid }),
}))
vi.mock('@/Domain/Member/hooks/useMembers', () => ({ useCurrentMember: () => ({ data: state.member }) }))
vi.mock('@/Domain/Seed/hooks/useItemSeeds', () => ({ useItemSeeds: () => ({}) }))
vi.mock('@/Domain/Item/hooks/useItems', () => ({ useItem: () => ({ data: undefined }) }))
vi.mock('@/Features/Auth/contexts/AuthContext', () => ({ useAuthContext: () => ({ hasRole: () => state.admin }) }))
vi.mock('@/Shared/ui/components/AppToast/AppToast', () => ({ useAppToast: () => ({ showToast: vi.fn() }) }))
vi.mock('@/Features/Auction/components/AuctionChat/AuctionChat', () => ({ AuctionChat: () => null }))
vi.mock('@/Features/Auction/components/AuctionViewers/AuctionViewers', () => ({ AuctionViewers: () => null }))
vi.mock('@/Features/Auction/hooks/useFillViewport', () => ({ useFillViewport: () => null }))

afterEach(() => {
  cleanup()
  localStorage.removeItem('equipment-mail-sender')
  state.confirm.mockReset()
  state.bid.mockReset()
  state.member = undefined
  state.others = []
  vi.unstubAllEnvs()
  vi.unstubAllGlobals()
})

it('prepares the game letter for the winner and only records delivery after confirmation', async () => {
  state.admin = true
  state.auction = {
    id: 40, status: 'CLOSED', items: [], winnerId: 8, winnerNickname: 'Vencedor local',
    currentBid: 10, endsAt: '2026-11-14T00:50:00Z',
    durationMinutes: 30, createdAt: '2026-11-14T00:20:00Z', startsAt: null,
    leaderId: null, leaderNickname: null, tiedCount: 0, tied: false, tiedMemberIds: [],
    tiedMemberNicknames: [], dolePhase: 0, tieBreakPhase: 0, version: 1,
    serverTime: '2026-11-14T01:00:00Z', pausedRemainingMs: null,
  }
  vi.stubEnv('VITE_GAME_MAIL_PREVIEW', 'true')
  localStorage.setItem('equipment-mail-sender', 'boi')
  const bridge = vi.fn().mockResolvedValue(Response.json({ message: 'Carta preparada.' }))
  vi.stubGlobal('fetch', bridge)
  state.confirm.mockResolvedValue(state.auction)
  const page = () => (
    <MemoryRouter initialEntries={['/auctions/40']}>
      <Routes><Route path="/auctions/:id" element={<AuctionDetailPage />} /></Routes>
    </MemoryRouter>
  )
  const view = render(page())
  fireEvent.click(screen.getByRole('button', { name: 'Preparar carta (boi)' }))
  await waitFor(() => expect(bridge).toHaveBeenCalledOnce())
  expect(bridge.mock.calls[0][0]).toBe('http://127.0.0.1:15183/prepare-letter')
  expect(JSON.parse(bridge.mock.calls[0][1].body)).toEqual({ sender: 'boi', recipient: 'Vencedor local' })
  expect(state.confirm).not.toHaveBeenCalled()
  fireEvent.click(screen.getByRole('button', { name: 'Confirmar envio' }))
  expect(state.confirm).not.toHaveBeenCalled()
  fireEvent.click(screen.getByRole('button', { name: 'Marcar como enviado' }))
  await waitFor(() => expect(state.confirm).toHaveBeenCalledWith(40))
  state.auction = { ...state.auction, deliveredAt: '2026-11-14T01:00:00Z' }
  state.admin = false
  view.rerender(page())
  expect(screen.getByText(/Item enviado em.*Brasília/)).toBeVisible()
  expect(screen.queryByRole('button', { name: 'Confirmar envio' })).not.toBeInTheDocument()
})

const openAuction = (): Auction => ({
  id: 41, status: 'OPEN', items: [], winnerId: null, winnerNickname: null,
  currentBid: 10, endsAt: new Date(Date.now() + 600_000).toISOString(),
  durationMinutes: 30, createdAt: '2026-11-14T00:20:00Z', startsAt: null,
  leaderId: 99, leaderNickname: 'Outro', tiedCount: 0, tied: false, tiedMemberIds: [],
  tiedMemberNicknames: [], dolePhase: 0, tieBreakPhase: 0, version: 1,
  serverTime: new Date().toISOString(), pausedRemainingMs: null,
})
const renderAuction = (id: number) => render(
  <MemoryRouter initialEntries={[`/auctions/${id}`]}>
    <Routes><Route path="/auctions/:id" element={<AuctionDetailPage />} /></Routes>
  </MemoryRouter>,
)

it('blocks bidding with the reason when the account is not blocked for sale', () => {
  state.admin = false
  state.auction = openAuction()
  state.member = { id: 7, availablePoints: 500, blockedForSellUntil: null }
  renderAuction(41)
  expect(screen.getByRole('status')).toHaveTextContent('Sua conta não está bloqueada para venda no site')
  const lance = screen.getByRole('button', { name: 'Lance bloqueado' })
  expect(lance).toBeDisabled()
  expect(screen.getByRole('spinbutton')).toBeDisabled()
  expect(screen.getByRole('button', { name: '+50' })).toBeDisabled()
  expect(screen.queryByRole('button', { name: /All-in/ })).not.toBeInTheDocument()
})

it('lets a member whose account is blocked for sale bid', () => {
  state.admin = false
  state.auction = openAuction()
  state.member = { id: 7, availablePoints: 500, blockedForSellUntil: '2099-12-31' }
  renderAuction(41)
  expect(screen.queryByText(/não está bloqueada para venda/)).not.toBeInTheDocument()
  expect(screen.getByRole('button', { name: 'All-in (500)' })).toBeEnabled()
  fireEvent.change(screen.getByRole('spinbutton'), { target: { value: '20' } })
  fireEvent.click(screen.getByRole('button', { name: 'Lance' }))
  expect(state.bid).toHaveBeenCalledWith({ id: 41, amount: 20 })
})

it('asks for at least +10 over the current bid', () => {
  state.admin = false
  state.auction = openAuction() // lance atual 10
  state.member = { id: 7, availablePoints: 500, blockedForSellUntil: '2099-12-31' }
  renderAuction(41)
  const input = screen.getByRole('spinbutton')
  expect(input).toHaveAttribute('placeholder', 'Mínimo 20')
  expect(screen.queryByRole('button', { name: '+5' })).not.toBeInTheDocument()
  fireEvent.change(input, { target: { value: '15' } })
  fireEvent.click(screen.getByRole('button', { name: 'Lance' }))
  expect(state.bid).not.toHaveBeenCalled()
  expect(screen.getByText('O lance mínimo é 20 (lance atual 10 + 10), ou all-in com todo o seu saldo.')).toBeVisible()
  fireEvent.click(screen.getByRole('button', { name: '+10' }))
  expect(input).toHaveValue(20)
})

it('all-in has no +10 minimum, and an exact balance still ties', () => {
  state.admin = false
  state.auction = openAuction() // lance atual 10
  state.member = { id: 7, availablePoints: 15, blockedForSellUntil: '2099-12-31' }
  renderAuction(41)
  fireEvent.click(screen.getByRole('button', { name: 'All-in (15)' }))
  expect(state.bid).toHaveBeenCalledWith({ id: 41, amount: 15 })
  fireEvent.change(screen.getByRole('spinbutton'), { target: { value: '15' } }) // digitado também vale
  fireEvent.click(screen.getByRole('button', { name: 'Lance' }))
  expect(state.bid).toHaveBeenCalledTimes(2)
  fireEvent.change(screen.getByRole('spinbutton'), { target: { value: '14' } })
  fireEvent.click(screen.getByRole('button', { name: 'Lance' }))
  expect(state.bid).toHaveBeenCalledTimes(2)
  state.member = { id: 7, availablePoints: 10, blockedForSellUntil: '2099-12-31' }
  cleanup()
  renderAuction(41)
  expect(screen.getByRole('button', { name: 'Empatar com all-in (10)' })).toBeEnabled()
})

it('blocks bidding on a lot identical to one the member is already winning', () => {
  state.admin = false
  const cash = { itemId: 5, itemName: '500 Cash Coin', imageUrl: null, quantity: 10, gameIcon: null }
  state.auction = { ...openAuction(), items: [cash] }
  state.member = { id: 7, availablePoints: 500, blockedForSellUntil: '2099-12-31' }
  state.others = [{ ...openAuction(), id: 47, items: [cash], leaderId: 7, currentBid: 30 }]
  renderAuction(41)
  expect(screen.getByRole('status')).toHaveTextContent('Você já está ganhando o leilão #047 com os mesmos itens')
  expect(screen.getByRole('button', { name: 'Lance bloqueado' })).toBeDisabled()
  cleanup()
  // Mesmo item em outra quantidade: pode.
  state.others = [{ ...openAuction(), id: 47, items: [{ ...cash, quantity: 5 }], leaderId: 7, currentBid: 30 }]
  renderAuction(41)
  expect(screen.queryByText(/mesmos itens/)).not.toBeInTheDocument()
  expect(screen.getByRole('spinbutton')).toBeEnabled()
  cleanup()
  // Leilão do mesmo lote já encerrado: não conta.
  state.others = [{ ...openAuction(), id: 47, items: [cash], leaderId: 7, currentBid: 30, status: 'CLOSED' }]
  renderAuction(41)
  expect(screen.getByRole('spinbutton')).toBeEnabled()
})
