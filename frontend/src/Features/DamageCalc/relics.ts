import relicGroups from '@/Domain/Equipment/relics.json'
import { relicLevelEffects, relicName } from '@/Domain/Equipment/utils/relicEffects'
import type { DamageMode, WeaponClass } from './DamageCalc.utils'

type RelicGroup = { grade: number; label?: string; hidden?: boolean; items: { id: number; position: string }[] }

/** Efeitos de relic que entram no ataque mostrado no jogo, e quando cada um vale. */
const DAMAGE_EFFECTS: Record<number, { label: string; applies: (k: WeaponClass | undefined, mode: DamageMode) => boolean }> = {
  18: { label: 'Atk', applies: () => true },
  212: { label: 'Atk geral', applies: () => true },
  19: { label: 'Melee', applies: (k, m) => m === 'atk' && k === 'melee' },
  21: { label: 'Range', applies: (k, m) => m === 'atk' && k === 'range' },
  200: { label: 'Launcher', applies: (k, m) => m === 'atk' && k === 'launcher' },
  211: { label: 'Granada', applies: (k, m) => m === 'atk' && k === 'grenade' },
  23: { label: 'Força', applies: (_, m) => m === 'force' },
}
const CRIT = 47

export interface DamageRelic {
  id: number
  name: string
  group: string
  /** Posição do ícone na folha /relics/item-1-6.jpg (64 px). */
  position: string
}

/** Relics que dão algum efeito de dano, na ordem das abas do jogo (sem Event / OLD). */
export const DAMAGE_RELICS: DamageRelic[] = (relicGroups as RelicGroup[])
  .filter(g => !g.hidden)
  .flatMap(g => g.items
    .filter(item => [0, 1, 2, 3].some(up => relicLevelEffects(item.id, up).some(([code]) => code in DAMAGE_EFFECTS || code === CRIT)))
    .map(item => ({
      id: item.id,
      name: (relicName(item.id) ?? `Relic ${item.id}`).replace(/^\(\d+GR\)\s*/, ''),
      group: g.label ?? `Grade ${g.grade}`,
      position: item.position,
    })))

const pct = (v: number) => Math.round(v * 1000) / 10

/** Texto curto dos efeitos de dano de uma relic: "Melee 4% · Range 4%". */
export function relicDamageText(id: number, upgrade: number): string {
  return relicLevelEffects(id, upgrade)
    .map(([code, v]) => code === CRIT ? `Crít ${Math.round(v * 100) / 100}` : DAMAGE_EFFECTS[code] ? `${DAMAGE_EFFECTS[code].label} ${pct(v)}%` : null)
    .filter(Boolean).join(' · ')
}

/** Quanto uma relic soma no ataque (%) e no crítico para a arma e o modo escolhidos. */
export function relicBonus(id: number, upgrade: number, k: WeaponClass | undefined, mode: DamageMode) {
  let atk = 0
  let crit = 0
  for (const [code, v] of relicLevelEffects(id, upgrade)) {
    if (code === CRIT) crit += v
    else if (DAMAGE_EFFECTS[code]?.applies(k, mode)) atk += v * 100
  }
  return { atk: Math.round(atk * 100) / 100, crit: Math.round(crit * 100) / 100 }
}
