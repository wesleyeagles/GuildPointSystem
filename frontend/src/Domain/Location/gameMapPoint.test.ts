import { describe, expect, it } from 'vitest'
import { gameMapPoint } from './gameMapPoint'

describe('gameMapPoint', () => {
  it('uses the client bounds like the game map window', () => {
    // Sette Desert (11): 337x400, X -5198..5845, Z -6812..6339 (escala 0,0305171, a mesma que a DLL leu).
    const corner = gameMapPoint(11, -5198, 6339)
    expect(corner?.px).toBeCloseTo(0)
    expect(corner?.py).toBeCloseTo(0)
    const far = gameMapPoint(11, 5845, -6812)
    expect(far?.px).toBeCloseTo(337)
    expect(far?.py).toBeCloseTo(400)
  })

  it('prefers the bounds sent by the DLL and needs them when the client has none', () => {
    expect(gameMapPoint(53, 0, 0)).toBeNull() // Elan novo: .bsp criptografado
    const live = { minX: -100, minZ: -100, maxX: 100, maxZ: 100 }
    const p = gameMapPoint(53, 0, 0, live)
    expect(p?.px).toBeCloseTo(p!.geometry.width / 2)
    expect(p?.py).toBeCloseTo(p!.geometry.height / 2)
  })

  it('returns null outside the map or for unknown maps', () => {
    expect(gameMapPoint(11, 9000, 0)).toBeNull()
    expect(gameMapPoint(999, 0, 0)).toBeNull()
  })
})
