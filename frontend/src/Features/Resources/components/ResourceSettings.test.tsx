import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { afterEach, expect, it, vi } from 'vitest'
import { ResourceSettings } from './ResourceSettings'
const calls = vi.hoisted(() => ({ plants: vi.fn(), gr: vi.fn() }))
vi.mock('@/Domain/Member/hooks/useMembers', () => ({ useMemberRanking: () => ({ data: [] }) }))
vi.mock('@/Shared/ui/components/AppToast/AppToast', () => ({ useAppToast: () => ({ showToast: vi.fn() }) }))
vi.mock('@/Domain/Resource/hooks/useResources', async original => ({
  ...await original<object>(),
  useSetResourceTier: () => ({ mutate: vi.fn(), isPending: false }),
  useUpdateResourceAccess: (gr: boolean) => ({ mutate: gr ? calls.gr : calls.plants, isPending: false }),
}))
afterEach(() => { cleanup(); vi.clearAllMocks() })
it('keeps GR permissions separate from plants when editing and switching tabs', () => {
  render(<ResourceSettings overview={{ canView: true, canConfigure: true, maps: [], mobs: [], trackers: [{id:1,nickname:'PlantViewer'}], grTrackers:[{id:2,nickname:'Assassino'},{id:3,nickname:'meow'}] }} />)
  expect(screen.getByText('PlantViewer')).toBeInTheDocument()
  fireEvent.click(screen.getByRole('tab', {name:'GR4 / GR5 / GR6 / GR7'}))
  expect(screen.queryByText('PlantViewer')).not.toBeInTheDocument()
  expect(screen.getByText('Assassino')).toBeInTheDocument()
  fireEvent.click(screen.getByTitle('Tirar meow'))
  fireEvent.click(screen.getByRole('button', {name:'Salvar lista'}))
  expect(calls.gr.mock.calls[0][0]).toEqual({memberIds:[2]})
  expect(calls.plants).not.toHaveBeenCalled()
  fireEvent.click(screen.getByRole('tab', {name:'Plantas'}))
  expect(screen.getByText('PlantViewer')).toBeInTheDocument()
  expect(screen.getByRole('button', {name:'Salvar lista'})).toBeDisabled()
})
