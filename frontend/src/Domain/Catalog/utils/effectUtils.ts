import { formatEffectValue } from '@/Domain/Catalog/utils/catalogUtils'
import type { GameAccessoryEffect, ItemSetEffect } from '@/Domain/types/models'

type MergeableEffect = GameAccessoryEffect | ItemSetEffect

export function mergeEffects(effects: MergeableEffect[]): MergeableEffect[] {
  const map = new Map<string, MergeableEffect>()

  for (const eff of effects) {
    const key = `${eff.code}-${eff.displayType}`
    const existing = map.get(key)

    if (!existing) {
      map.set(key, { ...eff })
      continue
    }

    if (eff.displayType === 'BOOLEAN') continue

    const sum = (existing.rawValue ?? 0) + (eff.rawValue ?? 0)
    map.set(key, {
      ...existing,
      rawValue: sum,
      displayValue: formatEffectValue(eff.displayType, sum, eff.code),
    })
  }

  return Array.from(map.values())
}
