import { describe, expect, it } from 'vitest'
import { computeSpinRotation } from './Roulette'

describe('Roulette', () => {
  it('computeSpinRotation returns positive degrees', () => {
    const rotation = computeSpinRotation(4, 1, 12345)
    expect(rotation).toBeGreaterThan(360)
  })

  it('computeSpinRotation is deterministic for same seed', () => {
    const a = computeSpinRotation(3, 0, 999)
    const b = computeSpinRotation(3, 0, 999)
    expect(a).toBe(b)
  })

  it('computeSpinRotation differs for different winner index', () => {
    const a = computeSpinRotation(4, 0, 42)
    const b = computeSpinRotation(4, 2, 42)
    expect(a).not.toBe(b)
  })
})
