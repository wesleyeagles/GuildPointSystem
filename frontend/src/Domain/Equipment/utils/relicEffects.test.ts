import { describe, expect, it } from 'vitest'
import { formatRelicEffect, relicEffectTotals, relicLevelEffects, relicIdFromGameCode, relicName, relicUpgradeTexts } from './relicEffects'
import groups from '../relicData.json'

describe('relic effects', () => {
  it('covers the 120 relics of the game with names and four levels for most', () => {
    expect(Object.keys(groups.relics)).toHaveLength(120)
    expect(relicName(2)).toBe('(1GR) Frenzy Ratmoth Relic')
    expect(relicLevelEffects(2, 0)).toEqual([[19, 0.02], [20, 0.02], [21, 0.02], [22, 0.02]])
    expect(relicLevelEffects(2, 2)).toEqual([[19, 0.03], [20, 0.03], [21, 0.03], [22, 0.03]])
  })
  it('formats like the Relics window', () => {
    expect(formatRelicEffect(18, 0.02)).toBe('Attack: 2.0% increase')
    expect(formatRelicEffect(49, 1.3)).toBe('Dodge: 1.30 increase')
    expect(formatRelicEffect(208, -0.01)).toBe('Negative effects: 1.0% decrease')
  })
  it('reproduces the "Current effects" read from the game for the reference character', () => {
    const owned = [[2, 1], [3, 0], [6, 0], [7, 0], [10, 0], [30, 0], [31, 0], [98, 0], [111, 0]].map(([id, upgrade]) => ({ id, upgrade }))
    expect(relicEffectTotals(owned).map(t => t.text)).toEqual([
      'Attack: 2.0% increase',
      'Melee attack: 2.4% increase',
      'Melee skill attack: 2.4% increase',
      'Range attack: 2.4% increase',
      'Range skill attack: 2.4% increase',
      'PrecisionAccuracy: 1.30 increase',
      'Dodge: 1.30 increase',
      'Block penetration: 1.30 increase',
      'Damage absorbtion: 1.0% increase',
      'Attack on monsters: 1.3% increase',
      'Attack on monsters and PB: 6.0% increase',
      'Accuracy: 1.30 increase',
      'Drop from monsters: 16.3% increase',
    ])
  })

  it('reads the relic id from the item game code and lists each upgrade', () => {
    expect(relicIdFromGameCode('relic000')).toBe(0)
    expect(relicIdFromGameCode('relic031')).toBe(31)
    expect(relicIdFromGameCode('iymta55')).toBeNull()
    expect(relicIdFromGameCode(null)).toBeNull()
    expect(relicUpgradeTexts(0)).toEqual([
      ['Force attack: 2.0% increase'],
      ['Force attack: 2.4% increase'],
      ['Force attack: 3.0% increase'],
      ['Force attack: 4.0% increase'],
    ])
  })
})
