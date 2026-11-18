import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { afterEach, expect, it, vi } from 'vitest'
import { QuestGuildDaily } from './QuestGuildDaily'
const state = vi.hoisted(() => ({ day: vi.fn(), refetch: vi.fn() }))
vi.mock('@/Domain/Quest/hooks/useGuildBd', () => ({ useGuildBd: (day: string) => {
  state.day(day)
  return { data: { members: [{ character: 'meow', completedAt: '2026-11-18T00:01:54Z' }, { character: 'boi', completedAt: null }] }, refetch: state.refetch }
} }))
afterEach(cleanup)
it('shows observed completion, pending 50-55 and absence without declaring failure', () => {
  render(<QuestGuildDaily />)
  expect(screen.getAllByText('Pendente de validação')).toHaveLength(2)
  expect(screen.getByText('21:01:54')).toBeInTheDocument()
  expect(screen.getByText('Sem registro')).toBeInTheDocument()
  expect(screen.getByText(/1 conclusões observadas/)).toBeInTheDocument()
  fireEvent.change(screen.getByLabelText('Dia'), { target: { value: '2026-11-16' } })
  expect(state.day).toHaveBeenLastCalledWith('2026-11-16')
  fireEvent.click(screen.getByRole('button', { name: 'Atualizar' }))
  expect(state.refetch).toHaveBeenCalled()
})
