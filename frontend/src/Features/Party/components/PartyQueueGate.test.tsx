import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { afterEach, expect, it, vi } from 'vitest'
import { PartyQueueGate } from './PartyQueueGate'

const mutate = vi.hoisted(() => vi.fn())
vi.mock('@/Domain/Party/hooks/useParty', () => ({ useSetPartyQueueAccess: () => ({ mutate, isPending: false }) }))
afterEach(() => { cleanup(); mutate.mockReset() })
const access = { open: false, manual: false, nextChangeAt: '2026-10-31T21:00:00Z' }

it('shows the schedule to members and only shows opening controls to administrators', () => {
  const view = render(<PartyQueueGate access={access} canManage={false} />)
  expect(screen.getByText('Fila fechada')).toBeVisible()
  expect(screen.getByText('Entradas das 18h às 06h · Brasília')).toBeVisible()
  expect(screen.queryByRole('button', { name: 'Abrir fila' })).not.toBeInTheDocument()
  view.rerender(<PartyQueueGate access={access} canManage />)
  fireEvent.click(screen.getByRole('button', { name: 'Abrir fila' }))
  expect(mutate).toHaveBeenCalledWith(true)
  view.rerender(<PartyQueueGate access={{ ...access, open: true, manual: true }} canManage />)
  expect(screen.getByText('Abertura manual até 18:00.')).toBeVisible()
  fireEvent.click(screen.getByRole('button', { name: 'Fechar fila' }))
  expect(mutate).toHaveBeenLastCalledWith(false)
})
