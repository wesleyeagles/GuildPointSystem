import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { QuestPlayerPanel, QuestDailyList } from './QuestLivePanels'
import type { QuestHistory } from '@/Domain/Quest/types'
const state = vi.hoisted(() => ({ admin: false, mutate: vi.fn() }))
vi.mock('@/Features/Auth/contexts/AuthContext', () => ({ useAuthContext: () => ({ hasRole: () => state.admin }) }))
vi.mock('@/Domain/Quest/hooks/useQuests', () => ({ useQuestVisibility: () => ({ mutate: state.mutate }) }))
afterEach(() => { cleanup(); state.admin = false; state.mutate.mockClear(); vi.unstubAllGlobals() })
function fixture(): QuestHistory {
  return { memberId: 1, sessions: [], events: [], hasMore: false, nextOffset: 0, simulationEnabled: true, liveEnabled: true,
    player: { sessionId: 's', receivedAt: new Date().toISOString(), revoked: false, sample: { sequence: 1, sampledAt: new Date().toISOString(), status: 'OBSERVED', name: 'meow', serial: 51, identityEpoch: 'a', level: 55, xpPercent: 0, quests: [] } },
    expected: [{ definition: { quest: { code: 'Q071439', title: 'Guild EXP 3', definitionHash: 'a' }, npcName: 'Coupon Reward Manager 3', npcCodes: ['050C2'], races: [1], objectives: ['Derrotar 1 Flym'] }, completionSignals: 0, hidden: false }],
    npcCoverage: [{ name: 'Coupon Reward Manager 4', npcCodes: ['060C2'], questCount: 0 }] }
}
describe('live character and curated checklist', () => {
  it('separates EXP NPCs and distinguishes direct XP from potion rewards', () => {
    const data = fixture()
    const base = data.expected[0]
    data.expected.push({ ...base, experiencePercent: 9, rewardLevel: 40, definition: { ...base.definition,
      quest: { code: 'Q071923', title: '[24h](EXP)[40-42][1/2] Infernal Demolis', definitionHash: 'exp' },
      npcName: 'EXP Quester(40+)', minLevel: 40, maxLevel: 42,
      rewards: [{ code: 'bxbos15', name: 'Small Quest Reward Box', quantity: 1 }] } },
    { ...base, definition: { ...base.definition,
      quest: { code: 'Q071207', title: '[24h][Group] Hero Path (EXP 5%)', definitionHash: 'hero' },
      npcName: 'PartyEXPQuester(CashShop)', minLevel: 50, maxLevel: 65,
      rewards: [{ code: 'ipexp20', name: 'EXP Potion 5%', quantity: 1 }] } })
    render(<QuestDailyList data={data} />)
    expect(screen.getByRole('region', { name: 'EXP Quester(40+)' })).toBeInTheDocument()
    expect(screen.getByRole('region', { name: 'PartyEXPQuester(CashShop)' })).toBeInTheDocument()
    expect(screen.getAllByText('[1/2] Infernal Demolis')).toHaveLength(1)
    expect(screen.getByText('9% XP · 1× caixa de recompensa')).toBeInTheDocument()
    expect(screen.getByText('1× EXP Potion 5%')).toBeInTheDocument()
    expect(screen.queryByText(/0% XP do nível/)).not.toBeInTheDocument()
  })
  it('shows name, objective and reward and opens a collapsed group when searching its objective', () => {
    const data = fixture()
    data.expected = [{ ...data.expected[0], definition: { ...data.expected[0].definition,
      quest: { code: 'Q071404', title: '[24h] Medium Cartella Quest Reward 1', definitionHash: 'medium' },
      objectives: ['Derrotar 1 Flem'], rewards: [{ code: 'iyfiw57', name: 'Red Cartella Emblem(st.1.1)', quantity: 9 }] } }]
    render(<QuestDailyList data={data} />)
    const group = screen.getByRole('button', { name: /Medium \/ Medium\+/ })
    expect(group).toHaveAttribute('aria-expanded', 'false')
    fireEvent.change(screen.getByRole('textbox', { name: 'Buscar quest' }), { target: { value: 'Flem' } })
    expect(group).toHaveAttribute('aria-expanded', 'true')
    expect(screen.getByText('Medium · Quest 1')).toBeVisible()
    expect(screen.getByText('Derrotar 1 Flem')).toBeVisible()
    expect(screen.getByText('9× Emblema vermelho')).toBeVisible()
    fireEvent.change(screen.getByRole('textbox', { name: 'Buscar quest' }), { target: { value: '' } })
    fireEvent.click(group)
    expect(group).toHaveAttribute('aria-expanded', 'true')
    fireEvent.click(screen.getByRole('button', { name: 'Recolher grupos' }))
    expect(group).toHaveAttribute('aria-expanded', 'false')
  })
  it('starts compact groups closed on small screens and keeps them keyboard-accessible', () => {
    vi.stubGlobal('matchMedia', vi.fn(() => ({ matches: true })))
    const data = fixture()
    data.expected[0].definition.quest.title = '[24h] Easy Cartella Quest Reward 1'
    render(<QuestDailyList data={data} />)
    const group = screen.getByRole('button', { name: /Easy \/ Easy\+/ })
    expect(group).toHaveAttribute('aria-expanded', 'false')
    fireEvent.click(group)
    expect(group).toHaveAttribute('aria-expanded', 'true')
    expect(screen.getByText('Easy · Quest 1')).toBeVisible()
  })
  it('shows observed player stats and stale status', () => {
    const data = fixture()
    const view = render(<QuestPlayerPanel data={data} />)
    expect(screen.getByText('meow')).toBeInTheDocument()
    expect(screen.getByText('0.00%')).toBeInTheDocument()
    expect(screen.getByRole('progressbar', { name: 'Progresso de XP' })).toHaveAttribute('value', '0')
    view.rerender(<QuestPlayerPanel data={{ ...data, player: { ...data.player!, receivedAt: '2020-01-01T00:00:00Z' } }} />)
    expect(screen.getByText('Sem leitura recente')).toBeInTheDocument()
  })
  it('shows expected quests with objectives before acceptance and gates admin controls', async () => {
    const data = fixture()
    const view = render(<QuestDailyList data={data} />)
    fireEvent.click(screen.getByText('Guild EXP 3'))
    expect(screen.getByText('Guild EXP 3')).toBeInTheDocument()
    expect(screen.getByText('Derrotar 1 Flym')).toBeInTheDocument()
    expect(screen.getByRole('checkbox', { name: 'Guild EXP 3' })).not.toBeChecked()
    expect(screen.queryByRole('button', { name: 'Ocultar da lista' })).not.toBeInTheDocument()
    state.admin = true
    view.rerender(<QuestDailyList data={data} />)
    fireEvent.click(screen.getByText('Gerenciar lista'))
    fireEvent.click(await screen.findByRole('button', { name: 'Ocultar da lista' }))
    expect(state.mutate).toHaveBeenCalledWith({ hash: 'a', hidden: true })
    expect(screen.getByText(/nenhum vínculo de quest encontrado/)).toBeInTheDocument()
  })
  it('groups reward variants and keeps hidden quests out of progress', () => {
    const data = fixture()
    const base = data.expected[0]
    data.expected = [
      { ...base, completionSignals: 1, definition: { ...base.definition, quest: { code: 'Q071396', title: '[24h] Easy Cartella Quest Reward 1', definitionHash: 'easy' } } },
      { ...base, definition: { ...base.definition, quest: { code: 'Q071401', title: '[24h] Easy+ Cartella Quest Reward 1', definitionHash: 'easy-plus' } } },
      { ...base, hidden: true, definition: { ...base.definition, quest: { code: 'Q071123', title: '[24h] BD Reward Shards', definitionHash: 'shards' } } },
    ]
    render(<QuestDailyList data={data} />)
    expect(screen.getByRole('region', { name: 'Easy / Easy+' })).toBeInTheDocument()
    expect(screen.queryByText(/Shards/)).not.toBeInTheDocument()
    expect(screen.getByRole('progressbar', { name: 'Progresso da checklist' })).toHaveAttribute('max', '2')
    expect(screen.getByRole('progressbar', { name: 'Progresso da checklist' })).toHaveAttribute('value', '1')
    expect(screen.getByRole('checkbox', { name: '[24h] Easy Cartella Quest Reward 1' })).toBeChecked()
  })
  it('counts only 2 of the 3 Easy+ per day and marks the third as capped', () => {
    const data = fixture()
    const base = data.expected[0]
    const easyPlus = (n: number, signals: number) => ({ ...base, completionSignals: signals,
      definition: { ...base.definition, quest: { code: `Q07140${n}`, title: `[24h] Easy+ Cartella Quest Reward ${n}`, definitionHash: `ep${n}` } } })
    data.expected = [easyPlus(1, 0), easyPlus(2, 0), easyPlus(3, 0)]
    const view = render(<QuestDailyList data={data} />)
    const bar = () => screen.getByRole('progressbar', { name: 'Progresso da checklist' })
    expect(bar()).toHaveAttribute('max', '2')
    expect(bar()).toHaveAttribute('value', '0')
    expect(screen.getByRole('button', { name: /Easy \/ Easy\+/ })).toHaveTextContent('0/2')

    data.expected = [easyPlus(1, 1), easyPlus(2, 0), easyPlus(3, 1)]
    view.rerender(<QuestDailyList data={{ ...data }} />)
    expect(bar()).toHaveAttribute('value', '2')
    expect(screen.getByRole('button', { name: /Easy \/ Easy\+/ })).toHaveTextContent('2/2')
    expect(screen.getByText('Limite do dia atingido (2 de 2)')).toBeInTheDocument()
    expect(screen.getByRole('checkbox', { name: '[24h] Easy+ Cartella Quest Reward 2' })).not.toBeChecked()
  })
  it('includes Medium+ and the two explicit extra quests', () => {
    const data = fixture()
    const base = data.expected[0]
    data.expected = ['Q071382', 'Q070944', 'Q070947'].map((code, i) => ({ ...base,
      definition: { ...base.definition, quest: { code, title: ['[24h][Medium+ Cartella] Invader Spider', '[24h] Chocolate Reward', '[24h] Chaos Potion Rewad'][i], definitionHash: code } },
    }))
    render(<QuestDailyList data={data} />)
    expect(screen.getByRole('region', { name: 'Medium / Medium+' })).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: /Medium \/ Medium\+/ }))
    expect(screen.getByText('Invader Spider')).toBeInTheDocument()
    expect(screen.getByRole('region', { name: 'Extras' })).toBeInTheDocument()
    expect(screen.getByText('Chocolate Reward')).toBeInTheDocument()
    expect(screen.getByText('Chaos Potion Reward')).toBeInTheDocument()
    expect(screen.getByText('1 caixa com 10 chocolates')).toBeVisible()
    expect(screen.getByText('1 caixa com 2 poções do caos')).toBeVisible()
    expect(screen.getByText('Q070944')).not.toBeVisible()
    expect(screen.getByText('Q070947')).not.toBeVisible()
    expect(screen.getAllByRole('checkbox')).toHaveLength(3)
  })
})
