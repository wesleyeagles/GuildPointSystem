import { cleanup, fireEvent, render, screen, within } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import type { RecentPointsEntry } from '@/Domain/Log/hooks/useLogs'
import { formatPointsTime, splitPages } from '@/Features/Dashboard/utils/recentPoints'
import { RecentPointsPanel } from './RecentPointsPanel'

const state = vi.hoisted(() => ({ data: [] as RecentPointsEntry[], cancel: vi.fn() }))
vi.mock('@/Domain/Log/hooks/useLogs', () => ({
  useRecentPoints: () => ({ data: state.data, isPending: false, isError: false }),
  useCancelRecentRound: () => ({ mutate: state.cancel, isPending: false, isError: false, error: null }),
}))

afterEach(cleanup)

describe('Últimos pontos', () => {
  it('shows one line per round, without player names', () => {
    state.data = [
      { kind: 'EVENT', name: 'CW Major', detail: null, members: 24, points: 5, total: 120, at: '2026-11-10T17:05:00Z', received: true, myPoints: 5, limited: false },
      { kind: 'RG', name: 'RG', detail: 'Vapor Lake', members: 1, points: 2, total: 2, at: '2026-11-09T22:40:00Z', received: false, myPoints: null, limited: true },
      { kind: 'EVENT', name: 'Boss', detail: null, members: 3, points: 4, total: 12, at: '2026-11-09T20:00:00Z', received: false, myPoints: null, limited: false },
    ]
    render(<RecentPointsPanel />)
    const rows = screen.getAllByRole('listitem')
    expect(rows).toHaveLength(3)
    expect(within(rows[0]).getByText('Evento')).toBeTruthy()
    expect(within(rows[0]).getByText('CW Major')).toBeTruthy()
    expect(within(rows[0]).getByText('+5')).toBeTruthy()
    expect(within(rows[0]).getByText(/24 membros/)).toBeTruthy()
    // RG mostra o local no lugar do nome
    expect(within(rows[1]).getByText('Vapor Lake')).toBeTruthy()
    expect(within(rows[1]).getByText(/1 membro ·/)).toBeTruthy()
    // situação do jogador logado vai na cor da borda; só o limite ganha texto
    expect(rows[0].className).toContain('recent-points__row--got')
    expect(within(rows[0]).queryByText(/Você/)).toBeNull()
    expect(rows[1].className).toContain('recent-points__row--limit')
    expect(within(rows[1]).getByText('Você não recebeu: limite de RG da CW (2x dia)')).toBeTruthy()
    expect(rows[2].className).toContain('recent-points__row--none')
    expect(within(rows[2]).queryByText(/Você/)).toBeNull()
  })

  it('labels the 10-min-before CW bonus as RG', () => {
    state.data = [
      { kind: 'CW', name: 'CW2 10/11: em Crag Mind Field 10 min antes', detail: null, members: 5, points: 2, total: 10, at: '2026-11-10T10:50:00Z', received: false, myPoints: null, limited: false },
    ]
    render(<RecentPointsPanel />)
    const [row] = screen.getAllByRole('listitem')
    expect(within(row).getByText('RG')).toBeTruthy()
  })

  it('lets an admin take back the points of a whole round', () => {
    state.data = [
      { kind: 'EVENT', name: 'PB Colônia', detail: null, members: 19, points: 5, total: 95, at: '2026-11-11T05:03:00Z', received: false, myPoints: null, limited: false, claimIds: [7, 8] },
      { kind: 'EVENT', name: 'Boss', detail: null, members: 3, points: 4, total: 12, at: '2026-11-09T20:00:00Z', received: false, myPoints: null, limited: false },
    ]
    render(<RecentPointsPanel />)
    const [round, other] = screen.getAllByRole('listitem')
    expect(within(other).queryByText('Cancelar')).toBeNull()   // sem claimIds: não é admin
    fireEvent.click(within(round).getByText('Cancelar'))
    expect(within(round).getByText('Tirar 5 pontos de 19 membros?')).toBeTruthy()
    fireEvent.change(within(round).getByPlaceholderText('Motivo...'), { target: { value: 'lançado em dobro' } })
    fireEvent.click(within(round).getByText('Tirar os pontos'))
    expect(state.cancel).toHaveBeenCalledWith({ claimIds: [7, 8], reason: 'lançado em dobro' }, expect.anything())
  })

  it('says when nothing gave points', () => {
    state.data = []
    render(<RecentPointsPanel />)
    expect(screen.getByText('Nenhum ponto dado nos últimos 30 dias.')).toBeTruthy()
  })

  it('formats time in Brasília: only the hour today, day/month otherwise', () => {
    const now = new Date('2026-11-10T18:00:00Z') // 15:00 em Brasília
    expect(formatPointsTime('2026-11-10T17:05:00Z', now)).toBe('14:05')
    expect(formatPointsTime('2026-11-09T22:40:00Z', now)).toBe('09/11 19:40')
  })

  it('splits rows into pages that fit the available height', () => {
    // 100 px, 4 px entre linhas: 40+4+40 cabe; 40+4+60 não; 60+4+10 cabe.
    expect(splitPages([40, 40, 40, 60, 10], 100, 4, 5)).toEqual([[0, 2], [2, 3], [3, 5]])
    // linha maior que a área: fica sozinha numa página
    expect(splitPages([150, 20], 100, 4, 2)).toEqual([[0, 1], [1, 2]])
    // sem medida (ex.: ainda não montou): tudo numa página
    expect(splitPages([], 0, 4, 3)).toEqual([[0, 3]])
    expect(splitPages([], 100, 4, 0)).toEqual([])
  })
})
