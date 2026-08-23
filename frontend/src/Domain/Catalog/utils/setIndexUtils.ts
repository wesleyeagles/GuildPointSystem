import type { ItemSet } from '@/Domain/types/models'

export const SET_SLOT_KEYS: (keyof ItemSet)[] = [
  'head',
  'upper',
  'lower',
  'shoes',
  'gauntlet',
  'weapon',
  'shield',
  'amul1',
  'amul2',
  'ring1',
  'ring2',
  'cloack',
]

const SET_SLOTS = SET_SLOT_KEYS

export const SET_SLOT_LABELS: Record<string, string> = {
  head: 'Helm',
  upper: 'Upper',
  lower: 'Lower',
  shoes: 'Boots',
  gauntlet: 'Gloves',
  weapon: 'Weapon',
  shield: 'Shield',
  amul1: 'Amulet',
  amul2: 'Amulet',
  ring1: 'Ring',
  ring2: 'Ring',
  cloack: 'Cloak',
}

export interface SetMemberSlot {
  slot: keyof ItemSet
  gameCode: string
}

export function getSetMemberSlots(set: ItemSet): SetMemberSlot[] {
  const members: SetMemberSlot[] = []

  for (const slot of SET_SLOT_KEYS) {
    const code = set[slot]
    if (typeof code !== 'string' || !code || code === '-1') continue
    members.push({ slot, gameCode: code })
  }

  return members
}

const ACCESSORY_SLOTS: Set<keyof ItemSet> = new Set([
  'amul1',
  'amul2',
  'ring1',
  'ring2',
])

function comboDisplayKey(set: ItemSet): string {
  const members = getSetMemberSlots(set)
  const armorSlots = members
    .filter((m) => !ACCESSORY_SLOTS.has(m.slot))
    .map((m) => m.slot)
    .join('|')
  const accessoryCodes = members
    .filter((m) => ACCESSORY_SLOTS.has(m.slot))
    .map((m) => m.gameCode)
    .join('|')
  const effectsKey = set.effects
    .map((e) => `${e.code}:${e.displayType}:${e.rawValue}`)
    .sort()
    .join('|')

  return `${armorSlots}|${accessoryCodes}|${effectsKey}`
}

/** Collapse tier-variant sets (e.g. WA50 vs WA55 armor) that share the same combo layout and bonuses. */
export function deduplicateComboSets(sets: ItemSet[]): ItemSet[] {
  const seen = new Map<string, ItemSet>()

  for (const set of sets) {
    const key = comboDisplayKey(set)
    if (!seen.has(key)) {
      seen.set(key, set)
    }
  }

  return Array.from(seen.values())
}

export function buildSetIndex(sets: ItemSet[]): Map<string, ItemSet[]> {
  const map = new Map<string, ItemSet[]>()

  for (const set of sets) {
    for (const slot of SET_SLOTS) {
      const code = set[slot]
      if (typeof code !== 'string' || !code || code === '-1') continue

      const existing = map.get(code) ?? []
      if (!existing.some((s) => s.id === set.id)) {
        existing.push(set)
      }
      map.set(code, existing)
    }
  }

  return map
}
