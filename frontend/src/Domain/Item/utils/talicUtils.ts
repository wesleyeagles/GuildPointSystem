import type { ItemTalic } from '@/Domain/types/models'

/** Level index 0–7 → bonus % */
export const TALIC_BONUS_PCT = [0, 5, 13, 25, 50, 80, 135, 200]

export function applyBonus(base: number, pct: number): number {
  return Math.round(base * (1 + pct / 100))
}

export function getKeenLevel(talics: ItemTalic[]): number {
  return talics.find((t) => t.talicType === 'KEEN')?.level ?? 0
}

export function getFavorLevel(talics: ItemTalic[]): number {
  return talics.find((t) => t.talicType === 'FAVOR')?.level ?? 0
}

export function getKeenPct(talics: ItemTalic[]): number {
  return TALIC_BONUS_PCT[getKeenLevel(talics)] ?? 0
}

export function getFavorPct(talics: ItemTalic[]): number {
  return TALIC_BONUS_PCT[getFavorLevel(talics)] ?? 0
}

/** Expand compressed API talics (e.g. KEEN lv5 → 5 icons). */
export function expandTalicsForDisplay(talics: ItemTalic[]): ItemTalic[] {
  const expanded: ItemTalic[] = []
  for (const t of talics) {
    if (t.talicType === 'KEEN' || t.talicType === 'FAVOR') {
      for (let i = 0; i < t.level; i++) {
        expanded.push({ talicType: t.talicType, level: 1, slot: i })
      }
    } else {
      expanded.push(t)
    }
  }
  return expanded.sort((a, b) => a.slot - b.slot)
}
