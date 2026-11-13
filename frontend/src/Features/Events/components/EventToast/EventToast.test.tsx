import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { afterEach, expect, it, vi } from 'vitest'
import type { GuildEvent } from '@/Domain/types/models'
import { EventToastProvider } from './EventToast'

const state = vi.hoisted(() => ({ events: [] as GuildEvent[] }))
vi.mock('@/Domain/Event/hooks/useEvents', () => ({
  useActiveEvents: () => ({ data: state.events }),
  useClaimEvent: () => ({ mutateAsync: vi.fn(), isPending: false }),
}))
vi.mock('@/Domain/Log/hooks/useLogs', () => ({ useEventSubscription: () => {} }))
vi.mock('@/Shared/ui/components/AppToast/AppToast', () => ({ useAppToast: () => ({ showToast: vi.fn() }) }))
vi.mock('@/Shared/utils/serverClock', () => ({ serverNow: () => Date.now() }))
afterEach(() => { cleanup(); state.events = [] })

function event(id: number, patch: Partial<GuildEvent> = {}): GuildEvent {
  return {
    id, objectiveId: 1, objectiveName: `Objetivo ${id}`, points: 10, durationMinutes: 5,
    createdAt: new Date().toISOString(), expiresAt: new Date(Date.now() + 5 * 60_000).toISOString(),
    active: true, claimedByMe: false, ...patch,
  } as GuildEvent
}

it('opens the card for an active event already running when the page loads (after F5)', () => {
  state.events = [event(1), event(2, { claimedByMe: true }), event(3, { expiresAt: new Date(Date.now() - 1000).toISOString() })]
  render(<EventToastProvider><div /></EventToastProvider>)
  expect(screen.getAllByText('Novo Evento!')).toHaveLength(1)
  expect(screen.getByText(/Objetivo 1/)).toBeTruthy()
  expect(screen.getByPlaceholderText('Senha')).toBeTruthy()
})

it('keeps a card closed with "x" closed when the list refetches', () => {
  state.events = [event(1)]
  const view = render(<EventToastProvider><div /></EventToastProvider>)
  fireEvent.click(screen.getByText('×'))
  expect(screen.queryByText('Novo Evento!')).toBeNull()
  state.events = [event(1)]  // new array, as after a refetch
  view.rerender(<EventToastProvider><div /></EventToastProvider>)
  expect(screen.queryByText('Novo Evento!')).toBeNull()
})
