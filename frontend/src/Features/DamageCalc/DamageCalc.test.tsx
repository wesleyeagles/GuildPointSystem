import { cleanup, fireEvent, render, screen, within } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { calcDamage, defaultMode, type KbWeapon } from './DamageCalc.utils'
import { DamageCalcPage } from './DamageCalc'

vi.mock('./data/kbase.json', () => ({ default: {
  source: 'https://cerberus-games.com/kbase/items/', server: 'CERBERUS - STAGE ERA', scrapedAt: '2026-11-09',
  weapons: [{ c: 'iwswa65', n: 'Saint Beam Sword', l: 65, t: 'sword', r: '11111', atk: [1000, 2000], i: 3, g: 1 }],
  amulets: [{ c: 'ia1', n: 'Fire Amulet', l: 1, r: '11111', e: { a: 30, c: 5 } }],
  rings: [],
} }))

afterEach(() => { cleanup(); localStorage.clear() })

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
    render(<DamageCalcPage />)
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
    render(<DamageCalcPage />)
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
