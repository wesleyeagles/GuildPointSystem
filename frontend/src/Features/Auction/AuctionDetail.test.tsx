import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, expect, it, vi } from 'vitest'
import type { Auction } from '@/Domain/types/models'
import { AuctionDetailPage } from './AuctionDetail'

const state = vi.hoisted(() => ({ admin: true, auction: {} as Auction, confirm: vi.fn() }))
vi.mock('@/Domain/Auction/hooks/useAuction', () => ({
  serverNow: () => Date.now(),
  useAuction: () => ({ data: state.auction }),
  useAuctionTieBreak: () => undefined,
  useCancelAuction: () => ({}),
  useConfirmAuctionDelivery: () => ({ mutateAsync: state.confirm }),
  useDeleteAuction: () => ({}),
  useAuctionAdminAction: () => ({}),
  useExtendAuction: () => ({}),
  usePlaceBid: () => ({}),
}))
vi.mock('@/Domain/Member/hooks/useMembers', () => ({ useCurrentMember: () => ({}) }))
vi.mock('@/Domain/Seed/hooks/useItemSeeds', () => ({ useItemSeeds: () => ({}) }))
vi.mock('@/Features/Auth/contexts/AuthContext', () => ({ useAuthContext: () => ({ hasRole: () => state.admin }) }))
vi.mock('@/Shared/ui/components/AppToast/AppToast', () => ({ useAppToast: () => ({ showToast: vi.fn() }) }))
vi.mock('@/Features/Auction/components/AuctionChat/AuctionChat', () => ({ AuctionChat: () => null }))
vi.mock('@/Features/Auction/components/AuctionViewers/AuctionViewers', () => ({ AuctionViewers: () => null }))
vi.mock('@/Features/Auction/hooks/useFillViewport', () => ({ useFillViewport: () => null }))

afterEach(() => {
  cleanup()
  localStorage.removeItem('equipment-mail-sender')
  state.confirm.mockReset()
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
