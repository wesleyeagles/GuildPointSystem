import { fireEvent, render, screen, within } from '@testing-library/react'
import { beforeEach, expect, it, vi } from 'vitest'
import { EnemyRadarSettings } from './EnemyRadarSettings'

const state = vi.hoisted(() => ({
  data: {
    canConfigure: true,
    guilds: [{ id: 7, name: 'Betrayed', createdAt: '2026-11-26T20:00:00Z' }],
    sightings: [
      { nick: 'Judas', guild: 'Betrayed', map: 'Crag Mind Field', region: 'Portal', seenBy: 'Scout', ageSeconds: 4 },
      { nick: 'MaterWelon', guild: 'Betrayed', map: null, region: null, seenBy: 'Scout', ageSeconds: 130 },
    ],
  },
  add: vi.fn(),
  remove: vi.fn(),
}))
vi.mock('@/Domain/EnemyRadar/hooks/useEnemyRadar', () => ({
  useEnemyRadar: () => ({ data: state.data }),
  useAddEnemyGuild: () => ({ mutate: state.add, isPending: false }),
  useRemoveEnemyGuild: () => ({ mutate: state.remove, isPending: false }),
}))
vi.mock('@/Shared/ui/components/AppToast/AppToast', () => ({ useAppToast: () => ({ showToast: vi.fn() }) }))
beforeEach(() => { state.data.canConfigure = true; state.add.mockClear(); state.remove.mockClear() })

it('lists who was seen, with the map label and age', () => {
  render(<EnemyRadarSettings />)
  const table = screen.getByRole('table')
  expect(within(table).getByText('Judas')).toBeInTheDocument()
  expect(within(table).getByText('Crag Mine Field · Portal')).toBeInTheDocument()
  expect(within(table).getByText('2 min')).toBeInTheDocument()
})

it('lets admins add a guild without spaces and remove one', () => {
  render(<EnemyRadarSettings />)
  fireEvent.change(screen.getByLabelText('Nome da guild inimiga'), { target: { value: 'BIack LisT' } })
  fireEvent.click(screen.getByRole('button', { name: 'Adicionar' }))
  expect(state.add).toHaveBeenCalledWith('BIackLisT', expect.anything())
  fireEvent.click(screen.getByTitle('Tirar Betrayed do radar'))
  expect(state.remove).toHaveBeenCalledWith(7, expect.anything())
})

it('members only see the list', () => {
  state.data.canConfigure = false
  render(<EnemyRadarSettings />)
  expect(screen.queryByLabelText('Nome da guild inimiga')).not.toBeInTheDocument()
  expect(screen.queryByTitle('Tirar Betrayed do radar')).not.toBeInTheDocument()
})
