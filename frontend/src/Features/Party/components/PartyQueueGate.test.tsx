import { cleanup, render, screen } from '@testing-library/react'
import { afterEach, expect, it } from 'vitest'
import { PartyQueueGate } from './PartyQueueGate'

afterEach(cleanup)
const access = { open: false, manual: false, nextChangeAt: '2026-10-31T21:00:00Z' }

it('shows the schedule and the administrator override expiry', () => {
  const view = render(<PartyQueueGate access={access} />)
  expect(screen.getByText('Fila fechada')).toBeVisible()
  expect(screen.getByText('Entradas das 18h às 06h · Brasília')).toBeVisible()
  view.rerender(<PartyQueueGate access={{ ...access, open: true, manual: true }} />)
  expect(screen.getByText('Fila aberta')).toBeVisible()
  expect(screen.getByText('Abertura manual até 18:00.')).toBeVisible()
})
