import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { calcDamage, defaultMode, type KbWeapon } from './DamageCalc.utils'
import { DamageCalcPage } from './DamageCalc'
import { relicBonus } from './relics'

vi.mock('./data/kbase.json', () => ({ default: {
  source: 'https://cerberus-games.com/kbase/items/', server: 'CERBERUS - STAGE ERA', scrapedAt: '2026-11-09',
  weapons: [{ c: 'iwswa65', n: 'Saint Beam Sword', l: 65, t: 'sword', k: 'melee', r: '11111', atk: [1000, 2000], i: 3, g: 1 }],
  amulets: [{ c: 'ia1', n: 'Fire Amulet', l: 1, r: '11111', e: { a: 30, c: 5 } }],
  rings: [],
} }))

const relicState = vi.hoisted(() => ({ mine: [] as { id: number; upgrade: number }[] }))
vi.mock('@/Domain/Member/hooks/useMembers', () => ({ useCurrentMember: () => ({ data: { id: 7 } }) }))
vi.mock('@/Domain/Equipment/hooks/useEquipment', () => ({
  useEquipmentRelics: () => ({ data: { relics: relicState.mine, syncedAt: '2026-11-11T12:00:00Z' } }),
}))

/** Servidor de presets em memória (o membro logado). */
const server = vi.hoisted(() => ({ presets: [] as { name: string; state: unknown }[] }))
vi.mock('@/Shared/api/client', () => ({
  apiClient: async (path: string, opts: { method?: string; body?: unknown } = {}) => {
    const same = (a: string, b: string) => a.trim().toLowerCase() === b.trim().toLowerCase()
    if (path === '/damage-presets' && opts.method === 'PUT') {
      const p = opts.body as { name: string; state: unknown }
      const at = server.presets.findIndex(x => same(x.name, p.name))
      if (at >= 0) server.presets[at] = p; else server.presets.push(p)
    } else if (path === '/damage-presets/import') {
      for (const p of opts.body as { name: string; state: unknown }[])
        if (!server.presets.some(x => same(x.name, p.name))) server.presets.push(p)
    } else if (path.startsWith('/damage-presets?name=') && opts.method === 'DELETE') {
      const name = decodeURIComponent(path.split('=')[1])
      server.presets = server.presets.filter(x => !same(x.name, name))
    } else if (path !== '/damage-presets') throw new Error(`unexpected ${path}`)
    return structuredClone(server.presets)
  },
}))

function renderPage() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(<QueryClientProvider client={client}><DamageCalcPage /></QueryClientProvider>)
}

afterEach(() => { cleanup(); localStorage.clear(); server.presets = [] })

/** A página carrega a kbase com import(); com a suíte toda rodando, passa de 1 s. */
const KBASE_LOAD = { timeout: 10_000 }

const sword: KbWeapon = { c: 'x', n: 'Sword', l: 65, t: 'sword', r: '11111', atk: [1000, 2000], e: { a: 10 } }

describe('calcDamage', () => {
  it('sums Keen and every % into one bonus over the weapon attack', () => {
    const r = calcDamage({ weapon: sword, mode: 'atk', keen: 4, effects: [sword.e!, { a: 30, c: 5 }], bonusPct: [35, 55], bonusCrit: 10 })
    expect(r.atkPct).toBe(130) // 10 arma + 30 amuleto + 35 buff + 55 adrena
    expect(r.totalPct).toBe(175) // + Keen +4 (45%)
    expect(r.final).toEqual([2750, 5500])
    expect(r.crit).toBe(15)
  })

  it('uses force attack (and Force Attack Power %) for staffs', () => {
    const staff: KbWeapon = { c: 's', n: 'Staff', l: 60, t: 'staff', r: '11111', atk: [100, 150], fo: [2000, 3000] }
    expect(defaultMode(staff)).toBe('force')
    const r = calcDamage({ weapon: staff, mode: 'force', keen: 0, effects: [{ a: 10, f: 10 }], bonusPct: [], bonusCrit: 0 })
    expect(r.final).toEqual([2400, 3600]) // ×1.2
  })
})

it('matches the game: Judgement, Keen +2, +189% → Gen. Attack 8052–10067', () => {
  const judgement: KbWeapon = { c: 'j', n: 'Judgement', l: 49, t: 'mace', r: '11111', atk: [2693, 3367] }
  const r = calcDamage({ weapon: judgement, mode: 'atk', keen: 2, effects: [], bonusPct: [189], bonusCrit: 0 })
  expect(r.final).toEqual([8052, 10067])
})

describe('Calculadora de dano', () => {
  it('shows the final damage after picking a weapon, keen and accessory', async () => {
    renderPage()
    // fechado não mostra lista; clicar abre
    expect(screen.queryByRole('listbox')).toBeNull()
    fireEvent.click(screen.getByRole('combobox', { name: 'Arma' }))
    fireEvent.click(await screen.findByRole('option', { name: /Saint Beam Sword/ }, KBASE_LOAD))
    expect(screen.getByRole('combobox', { name: 'Arma' })).toHaveValue('Saint Beam Sword')
    fireEvent.change(screen.getByRole('combobox', { name: 'Talics Keen' }), { target: { value: '7' } })
    // o próprio campo é a busca
    fireEvent.change(screen.getByRole('combobox', { name: 'Amuleto 1' }), { target: { value: 'fire' } })
    const amulet = within(screen.getByRole('listbox', { name: 'Amuleto 1' }))
    fireEvent.click(amulet.getByRole('option', { name: /Fire Amulet/ }))
    expect(screen.queryByRole('listbox', { name: 'Amuleto 1' })).toBeNull()
    expect(screen.getByRole('combobox', { name: 'Amuleto 1' })).toHaveValue('Fire Amulet')
    expect(screen.getByRole('button', { name: 'Remover Amuleto 1' })).toBeTruthy()
    // 1000–2000 × (1 + 200% Keen +7 + 30%)
    expect(screen.getByText('3.300')).toBeTruthy()
    expect(screen.getByText('6.600')).toBeTruthy()
    expect(JSON.parse(localStorage.getItem('damageCalc.v1')!).keen).toBe(7)
  })

  it('lists only Cora buffs and one adrena at a time', async () => {
    localStorage.setItem('damageCalc.v1', JSON.stringify({ weapon: 'iwswa65', buffs: ['howl'], adrena: 'custom' }))
    renderPage()
    await screen.findByText('2.000', undefined, KBASE_LOAD)
    const buffs = within(screen.getByRole('group', { name: 'Buffs' }))
    expect(buffs.queryByText('Howl')).toBeNull() // buff de outra raça salvo antes não soma
    fireEvent.click(buffs.getByRole('checkbox', { name: /Focus/ }))
    const adrena = within(screen.getByRole('radiogroup', { name: 'Adrena' }))
    fireEvent.click(adrena.getByRole('radio', { name: /Burst Potion/ }))
    // 1000–2000 × (1 + 40% + 55%)
    expect(screen.getByText('1.950')).toBeTruthy()
    expect(screen.getByText('3.900')).toBeTruthy()
    fireEvent.click(adrena.getByRole('radio', { name: /Burst Potion/ })) // clicar de novo tira
    expect(screen.getByText('1.400')).toBeTruthy()
    expect(screen.queryByText(/pontos de crítico|Debuffs no alvo|kbase do Cerberus|Conta:/)).toBeNull()
    // Adept é só força: marcado numa espada não muda o dano
    fireEvent.click(buffs.getByRole('checkbox', { name: /Adept/ }))
    expect(screen.getByText('1.400')).toBeTruthy()
    fireEvent.click(buffs.getByRole('checkbox', { name: /Rage Range/ }))
    expect(screen.getByText('1.700')).toBeTruthy() // 1000 × (1 + 40% + 30%)
    fireEvent.click(screen.getByRole('radio', { name: /Attack and Defense Generator/ }))
    expect(screen.getByText('1.900')).toBeTruthy() // + 20% do gerador
  })
})

describe('Relics', () => {
  it('each damage effect only counts for its weapon class and mode', () => {
    // Frenzy Ratmoth (id 2): Melee e Range 2% / 2,4% / 3% / 4%
    expect(relicBonus(2, 3, 'melee', 'atk').atk).toBe(4)
    expect(relicBonus(2, 0, 'range', 'atk').atk).toBe(2)
    expect(relicBonus(2, 3, 'force', 'force').atk).toBe(0)
    // Splinter Rex (id 0): Força 2% → só no dano de força
    expect(relicBonus(0, 0, 'force', 'force').atk).toBe(2)
    expect(relicBonus(0, 0, 'melee', 'atk').atk).toBe(0)
    // Freak Vafer Niffer (id 4): só crítico
    expect(relicBonus(4, 3, 'melee', 'atk')).toEqual({ atk: 0, crit: 2.6 })
  })

  it('loads the synced relics of the player and adds them to the bonus', async () => {
    relicState.mine = [{ id: 2, upgrade: 3 }, { id: 0, upgrade: 1 }, { id: 9999, upgrade: 0 }]
    localStorage.setItem('damageCalc.v1', JSON.stringify({ weapon: 'iwswa65' }))
    renderPage()
    await screen.findByText('2.000', undefined, KBASE_LOAD)
    fireEvent.click(screen.getByRole('button', { name: 'Usar minhas relics' }))
    // espada (melee): só o Melee 4% do Frenzy Ratmoth +3 soma; a de força fica marcada mas não soma
    expect(screen.getByText('1.040')).toBeTruthy()
    expect(screen.getByText('2.080')).toBeTruthy()
    expect(screen.getByRole('checkbox', { name: /Splinter Rex/ })).toBeChecked()
    expect(screen.getByRole('combobox', { name: 'Upgrade de Frenzy Ratmoth Relic' })).toHaveValue('3')
    relicState.mine = []
  })
})

describe('Presets', () => {
  it('saves with a name, loads it back and compares up to 3 side by side', async () => {
    localStorage.setItem('damageCalc.v1', JSON.stringify({ weapon: 'iwswa65' }))
    renderPage()
    await screen.findByText('2.000', undefined, KBASE_LOAD)
    const name = await screen.findByRole('textbox', { name: 'Nome do preset' })
    const save = async (n: string) => {
      fireEvent.change(name, { target: { value: n } })
      fireEvent.click(screen.getByRole('button', { name: /^(Salvar|Substituir)$/ }))
      await screen.findByRole('checkbox', { name: `Comparar ${n}` })
    }
    await save('Sem keen')
    fireEvent.change(screen.getByRole('combobox', { name: 'Talics Keen' }), { target: { value: '7' } })
    await save('Keen 7')
    fireEvent.change(screen.getByRole('combobox', { name: 'Talics Keen' }), { target: { value: '4' } })
    await save('Keen 4')
    await save('Extra')
    expect(server.presets).toHaveLength(4) // fica no membro, não no navegador
    expect(localStorage.getItem('damageCalc.presets.v1')).toBeNull()

    // carregar volta a configuração do preset
    const row = (n: string) => within(screen.getByRole('checkbox', { name: `Comparar ${n}` }).closest('li')!)
    fireEvent.click(row('Sem keen').getByRole('button', { name: 'Carregar' }))
    expect(screen.getByRole('combobox', { name: 'Talics Keen' })).toHaveValue('0')

    // mesmo nome substitui
    fireEvent.change(name, { target: { value: 'extra' } })
    expect(screen.getByRole('button', { name: 'Substituir' })).toBeTruthy()

    // no máximo 3 marcados
    const compare = screen.getByRole('button', { name: /^Comparar/ })
    expect(compare).toBeDisabled()
    for (const n of ['Sem keen', 'Keen 7', 'Keen 4']) fireEvent.click(screen.getByRole('checkbox', { name: `Comparar ${n}` }))
    expect(screen.getByRole('checkbox', { name: 'Comparar Extra' })).toBeDisabled()
    fireEvent.click(compare)
    const modal = within(screen.getByRole('dialog', { name: 'Comparar presets' }))
    const col = (n: string) => within(modal.getByRole('region', { name: n }))
    expect(col('Sem keen').getByText('1.000')).toBeTruthy()
    expect(col('Keen 7').getByText('3.000')).toBeTruthy() // 1000 × (1 + 200%)
    expect(col('Keen 7').getByText('6.000')).toHaveClass('is-best')
    expect(col('Keen 4').getByText('2.900')).toBeTruthy() // 2000 × (1 + 45%)
    expect(col('Keen 4').getAllByText('Saint Beam Sword')).toBeTruthy()
    fireEvent.click(modal.getByRole('button', { name: 'Fechar' }))
    expect(screen.queryByRole('dialog')).toBeNull()

    // excluir
    fireEvent.click(screen.getByRole('button', { name: 'Excluir Extra' }))
    await waitFor(() => expect(screen.queryByRole('checkbox', { name: 'Comparar Extra' })).toBeNull())
    expect(server.presets).toHaveLength(3)
  })

  it('uploads the presets that were only in this browser once', async () => {
    server.presets = [{ name: 'Na conta', state: { weapon: 'iwswa65', keen: 1 } }]
    localStorage.setItem('damageCalc.presets.v1', JSON.stringify([
      { name: 'na conta', state: { weapon: 'iwswa65', keen: 7 } },
      { name: 'Do navegador', state: { weapon: 'iwswa65', keen: 2 } },
    ]))
    renderPage()
    await screen.findByRole('checkbox', { name: 'Comparar Do navegador' }, KBASE_LOAD)
    expect(server.presets.map(p => p.name)).toEqual(['Na conta', 'Do navegador'])
    expect(server.presets[0].state).toEqual({ weapon: 'iwswa65', keen: 1 }) // o da conta fica
    expect(localStorage.getItem('damageCalc.presets.v1')).toBeNull()
  })
})
