import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import type { Auction, AuctionBidHistory } from '@/Domain/types/models'
import { ManageAuctionBids } from './ManageAuctionBids'
const state = vi.hoisted(() => ({ mutate: vi.fn(), pending: false, rows: [] as AuctionBidHistory[], enabled: false }))
vi.mock('@/Domain/Auction/hooks/useAuction', () => ({
  useAuctionBids: (_id: number, _page: number, enabled: boolean) => {
    state.enabled = enabled
    return { data: { content: state.rows, totalPages: 1 } }
  },
  useCancelAuctionBid: () => ({ mutateAsync: state.mutate, isPending: state.pending }),
}))
const auction = { id: 17, status: 'OPEN' } as Auction
beforeEach(() => {
  state.pending = false
  state.mutate.mockReset().mockResolvedValue(auction)
  state.rows = [{ id: 22, memberId: 4, nickname: 'Jogador', amount: 50, createdAt: '2026-11-21T15:00:00Z',
    cancelledAt: null, cancelledBy: null, cancellationReason: null }]
})
afterEach(cleanup)
function open(status: Auction['status'] = 'OPEN') {
  render(<ManageAuctionBids auction={{ ...auction, status }} />)
  expect(state.enabled).toBe(false)
  fireEvent.click(screen.getByRole('button', { name: 'Gerenciar lances' }))
  expect(state.enabled).toBe(true)
}
it('requires selecting and confirming the exact bid, with a reason', async () => {
  open()
  fireEvent.click(screen.getByRole('button', { name: 'Cancelar lance' }))
  expect(state.mutate).not.toHaveBeenCalled()
  fireEvent.change(screen.getByRole('textbox', { name: 'Motivo' }), { target: { value: ' ' } })
  fireEvent.click(screen.getByRole('button', { name: 'Confirmar cancelamento' }))
  expect(state.mutate).not.toHaveBeenCalled()
  expect(screen.getByRole('alert')).toHaveTextContent('Informe o motivo')
  fireEvent.change(screen.getByRole('textbox', { name: 'Motivo' }), { target: { value: 'Clique acidental' } })
  fireEvent.click(screen.getByRole('button', { name: 'Confirmar cancelamento' }))
  await waitFor(() => expect(state.mutate).toHaveBeenCalledWith({ bidId: 22, reason: 'Clique acidental' }))
  await waitFor(() => expect(screen.getByText('Jogador')).toBeVisible())
})
it('shows server refusal without losing the selection', async () => {
  state.mutate.mockRejectedValue(new Error('Saldo disponível insuficiente'))
  open()
  fireEvent.click(screen.getByRole('button', { name: 'Cancelar lance' }))
  fireEvent.click(screen.getByRole('button', { name: 'Confirmar cancelamento' }))
  await waitFor(() => expect(screen.getByRole('alert')).toHaveTextContent('Saldo disponível insuficiente'))
  expect(screen.getByRole('textbox')).toBeVisible()
})
it('keeps roulette read-only', () => {
  open('TIE_BREAK')
  expect(screen.getByRole('button', { name: 'Cancelar lance' })).toBeDisabled()
})
it('keeps cancelled bids with their admin and reason in history', () => {
  state.rows[0] = { ...state.rows[0], cancelledAt: '2026-11-21T15:01:00Z', cancelledBy: 'Administrador', cancellationReason: 'Missclick' }
  open()
  expect(screen.getByText(/Cancelado por Administrador.*Missclick/)).toBeVisible()
  expect(screen.queryByRole('button', { name: 'Cancelar lance' })).not.toBeInTheDocument()
})
