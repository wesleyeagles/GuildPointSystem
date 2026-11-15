import { ADRENAS, BUFFS, GENERATORS } from './data/boosts'
import { calcDamage, type DamageMode, type KbAccessory, type KbWeapon } from './DamageCalc.utils'
import { DAMAGE_RELICS, relicBonus } from './relics'

export const ACC_SLOTS = [
  { key: 'amulet1', label: 'Amuleto 1', list: 'amulets' },
  { key: 'amulet2', label: 'Amuleto 2', list: 'amulets' },
  { key: 'ring1', label: 'Anel 1', list: 'rings' },
  { key: 'ring2', label: 'Anel 2', list: 'rings' },
] as const

export interface SavedState {
  weaponType: string
  weapon: string
  mode: DamageMode
  keen: number
  acc: Record<string, string>
  buffs: string[]
  adrena: string
  generator: string
  /** id da relic → upgrade (0 = base). */
  relics: Record<string, number>
  extraAtk: number
}

export const INITIAL: SavedState = {
  weaponType: 'sword', weapon: '', mode: 'atk', keen: 0, acc: {}, buffs: [], adrena: '', generator: '', relics: {}, extraAtk: 0,
}

/** Estado salvo (página ou preset) com o que saiu das listas removido. */
export function sanitizeState(raw: unknown): SavedState {
  const saved = { ...INITIAL, ...(raw && typeof raw === 'object' ? raw : {}) } as SavedState
  // buffs/adrenas que saíram da lista (só Cora agora) não podem continuar somando
  return {
    ...saved,
    acc: { ...(saved.acc ?? {}) },
    buffs: (saved.buffs ?? []).filter(id => BUFFS.some(b => b.id === id)),
    adrena: ADRENAS.some(a => a.id === saved.adrena) ? saved.adrena : '',
    generator: GENERATORS.some(g => g.id === saved.generator) ? saved.generator : '',
    relics: Object.fromEntries(Object.entries(saved.relics ?? {})
      .filter(([id, up]) => DAMAGE_RELICS.some(r => String(r.id) === id) && Number.isInteger(up) && up >= 0 && up <= 3)),
  }
}

export type ItemIndex = Map<string, KbWeapon | KbAccessory>

/** Arma, buffs e dano final de um estado (o da página ou o de um preset). */
export function evaluate(s: SavedState, byCode: ItemIndex) {
  const weapon = (byCode.get(s.weapon) as KbWeapon | undefined) ?? null
  const accessories = ACC_SLOTS.map(slot => byCode.get(s.acc[slot.key] ?? '') as KbAccessory | undefined)
  // Rage Range só vale no ataque físico; Adept só na força
  const buffs = BUFFS.filter(b => s.buffs.includes(b.id) && (!b.only || b.only === s.mode))
  const adrena = ADRENAS.find(a => a.id === s.adrena)
  const generator = GENERATORS.find(g => g.id === s.generator)
  const relics = Object.entries(s.relics).map(([id, up]) => relicBonus(Number(id), up, weapon?.k, s.mode))
  const relicAtk = Math.round(relics.reduce((t, r) => t + r.atk, 0) * 100) / 100
  const relicCrit = relics.reduce((t, r) => t + r.crit, 0)
  const result = calcDamage({
    weapon,
    mode: s.mode,
    keen: s.keen,
    effects: [weapon?.e ?? {}, ...accessories.map(a => a?.e ?? {})],
    bonusPct: [...buffs.map(b => b.atk), adrena?.atk ?? 0, generator?.atk ?? 0, relicAtk, s.extraAtk],
    bonusCrit: buffs.reduce((t, b) => t + (b.crit ?? 0), 0) + relicCrit,
  })
  return { weapon, accessories, buffs, adrena, generator, relics, relicAtk, result }
}

