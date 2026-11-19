import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { afterEach, expect, it, vi } from 'vitest'
import type { AutoPointRule } from '@/Domain/AutoPoints/hooks/useAutoPoints'
import { AutoPointsPage } from './AutoPoints'

const state = vi.hoisted(() => ({ rules: [] as AutoPointRule[], save: vi.fn(), remove: vi.fn() }))
vi.mock('@/Domain/AutoPoints/hooks/useAutoPoints', () => ({
  useAutoPointRules: () => ({ data: state.rules }),
  useAutoPointMaps: () => ({ data: [{ map: 'Cora HQ', regions: ['Cora Outpost', 'Dark Plain', 'Hunter Cave'] }] }),
  useSaveAutoPointRule: () => ({ mutate: state.save, isPending: false, error: null, reset: vi.fn() }),
  useDeleteAutoPointRule: () => ({ mutate: state.remove, isPending: false }),
}))
vi.mock('@/Domain/Objective/hooks/useObjectives', () => ({
  useObjectives: () => ({ data: [{ id: 1, name: 'PB Elan', points: 5, type: 'NORMAL', dailyLimit: null, createdAt: '' }] }),
}))

afterEach(() => {
  cleanup()
  state.save.mockReset()
  state.rules = []
})

const rule: AutoPointRule = {
  id: 2, code: 'HQ_22', label: 'PB HQ 22:00', enabled: true, weekdays: [1, 2, 3, 4, 5, 6, 7],
  shoutsBefore: [12, 7, 2], shout: 'RG Base Avançada, Pontos de RG as 21:52', shoutEn: null,
  rgEnabled: true, rgAt: '21:52', rgMap: 'Cora HQ', rgRegions: ['Cora Outpost'], rgPoints: 2,
  presenceEnabled: true, presenceFrom: '21:52', presenceTo: '22:05', presenceMap: 'Cora HQ',
  presenceRegions: ['Dark Plain', 'Hunter Cave'], objectiveName: 'PB HQ', objectiveMissing: true,
  lastRuns: [{ date: '2026-11-18', rg: 12, objective: 9 }],
}

it('lists a rule with its RG, presence, missing objective and last run', () => {
  state.rules = [rule]
  render(<AutoPointsPage />)
  expect(screen.getByText('PB HQ 22:00')).toBeTruthy()
  expect(screen.getByText('Todo dia')).toBeTruthy()
  expect(screen.getByText(/Cora Outpost · Cora HQ/)).toBeTruthy()
  expect(screen.getByText(/Dark Plain, Hunter Cave · Cora HQ/)).toBeTruthy()
  expect(screen.getByText(/o objetivo não existe/)).toBeTruthy()
  expect(screen.getByText(/18\/11: RG 12/)).toBeTruthy()
  fireEvent.click(screen.getByText('Desligar'))
  expect(state.save).toHaveBeenCalledWith({ id: 2, body: expect.objectContaining({ enabled: false }) })
})

it('creates a Tuesday/Saturday presence-only rule on the whole map', () => {
  render(<AutoPointsPage />)
  fireEvent.click(screen.getByText('+ Nova regra'))
  fireEvent.change(screen.getByPlaceholderText('Ex.: PB Elan'), { target: { value: 'PB Big' } })
  fireEvent.click(screen.getByText('Nenhum'))                        // limpa os 7 dias
  fireEvent.click(screen.getByText('Ter'))
  fireEvent.click(screen.getByText('Sáb'))
  fireEvent.click(screen.getByLabelText(/RG no local/))              // desliga o RG
  fireEvent.change(screen.getByLabelText('De'), { target: { value: '14:00' } })
  fireEvent.change(screen.getByLabelText('Até'), { target: { value: '14:30' } })
  fireEvent.change(screen.getByLabelText('Objetivo'), { target: { value: 'PB Elan' } })
  fireEvent.change(screen.getByLabelText('Mapa'), { target: { value: 'Cora HQ' } })
  fireEvent.change(screen.getByPlaceholderText(/12, 7, 2/), { target: { value: '10, 5' } })
  fireEvent.change(screen.getByPlaceholderText(/RG Base Avançada/), { target: { value: 'PB Big às 14:00' } })
  fireEvent.click(screen.getByText('Criar regra'))
  expect(state.save).toHaveBeenCalledWith(
    {
      id: undefined,
      body: expect.objectContaining({
        label: 'PB Big', weekdays: [2, 6], rgEnabled: false, presenceEnabled: true, presenceFrom: '14:00',
        presenceTo: '14:30', presenceMap: 'Cora HQ', presenceRegions: [], objectiveName: 'PB Elan',
        shoutsBefore: [10, 5], shout: 'PB Big às 14:00',
      }),
    },
    expect.anything(),
  )
})
