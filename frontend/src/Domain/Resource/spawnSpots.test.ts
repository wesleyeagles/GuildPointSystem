import { describe, expect, it } from 'vitest'
import type { ResourceSpawn } from './hooks/useResources'
import { groupSpawnSpots } from './spawnSpots'

let next = 1
const at = (minute: number) => new Date(Date.UTC(2026, 10, 6, 12, minute)).toISOString()
const spawn = (x: number, z: number, minute: number, tier: number | null = 2, diedAt: string | null = null): ResourceSpawn =>
  ({ id: next++, name: 'Spiky Cactus', tier, x, z, foundAt: at(minute), foundBy: null, diedAt })

describe('groupSpawnSpots', () => {
  it('junta achados próximos do mesmo grau num ponto, com intervalo e próximo provável', () => {
    const spots = groupSpawnSpots([
      spawn(100, 100, 0, 2, at(1)),
      spawn(104, 98, 30, 2, at(31)),
      spawn(101, 103, 50),
      spawn(400, 400, 10),
    ], 20)
    expect(spots).toHaveLength(2)
    const main = spots.find((s) => s.count === 3)!
    expect(main.x).toBe(102)
    expect(main.z).toBe(100)
    expect(main.medianGapMs).toBe(25 * 60_000)  // intervalos de 30 e 20 min
    expect(main.nextAt).toBe(Date.parse(at(75)))
    expect(main.alive).toBe(true)
    const lone = spots.find((s) => s.count === 1)!
    expect(lone.medianGapMs).toBeNull()
    expect(lone.nextAt).toBeNull()
  })

  it('graus diferentes no mesmo lugar ficam em pontos separados; longe demais abre ponto novo', () => {
    const spots = groupSpawnSpots([spawn(0, 0, 0, 1), spawn(1, 1, 1, 3), spawn(50, 0, 2, 1)], 20)
    expect(spots).toHaveLength(3)
    expect(spots.every((s) => s.count === 1)).toBe(true)
  })
})
