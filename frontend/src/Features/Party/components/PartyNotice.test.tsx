import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { PartyNotice } from './PartyNotice'

const state = vi.hoisted(() => ({ mutate: vi.fn(), toast: vi.fn() }))
vi.mock('@/Domain/Party/hooks/useParty', () => ({
  useSetPartyNotice: () => ({ mutate: state.mutate, isPending: false }),
}))
vi.mock('@/Shared/ui/components/AppToast/AppToast', () => ({
  useAppToast: () => ({ showToast: state.toast }),
}))
beforeEach(() => { state.mutate.mockReset(); state.toast.mockReset() })
afterEach(cleanup)

it('lets players read live updates and hides editing controls', () => {
  const view = render(<PartyNotice text="Up Z nos níveis 48" canEdit={false} />)
  expect(screen.getByText('Up Z nos níveis 48')).toBeVisible()
  expect(screen.queryByRole('button')).toBeNull()
  view.rerender(<PartyNotice text="Amanhã em Beast Mountain" canEdit={false} />)
  expect(screen.getByText('Amanhã em Beast Mountain')).toHaveAttribute('data-no-i18n')
  view.rerender(<PartyNotice text="" canEdit={false} />)
  expect(screen.queryByRole('region')).toBeNull()
})

it('preserves the admin draft on incoming updates and supports saving or removing the notice', () => {
  const view = render(<PartyNotice text="Atual" canEdit />)
  fireEvent.click(screen.getByRole('button', { name: 'Editar aviso' }))
  fireEvent.change(screen.getByRole('textbox'), { target: { value: 'Novo aviso' } })
  view.rerender(<PartyNotice text="Alterado por outro admin" canEdit />)
  expect(screen.getByRole('textbox')).toHaveValue('Novo aviso')
  fireEvent.click(screen.getByRole('button', { name: 'Salvar aviso' }))
  expect(state.mutate).toHaveBeenLastCalledWith('Novo aviso', expect.any(Object))
  fireEvent.click(screen.getByRole('button', { name: 'Remover aviso' }))
  expect(state.mutate).toHaveBeenLastCalledWith('', expect.any(Object))
  state.mutate.mock.calls[1][1].onError(new Error('Sem permissão'))
  expect(state.toast).toHaveBeenCalledWith('Sem permissão', 'error')
})
