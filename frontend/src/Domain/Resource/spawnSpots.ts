import type { ResourceSpawn } from './hooks/useResources'

/** Um ponto de respawn: achados do mesmo grau que caem praticamente no mesmo lugar do mapa. */
export interface SpawnSpot {
  key: string
  tier: number | null
  /** Centro do ponto, em coordenadas do mundo (média dos achados). */
  x: number
  z: number
  count: number
  /** Do mais antigo ao mais novo. */
  spawns: ResourceSpawn[]
  lastAt: number
  /** Intervalo mediano entre nascimentos seguidos neste ponto (ms); null com menos de 2 achados. */
  medianGapMs: number | null
  /** lastAt + intervalo mediano; null sem intervalo. Só uma estimativa: o servidor sorteia. */
  nextAt: number | null
  /** O último achado ainda está vivo (ninguém viu morrer). */
  alive: boolean
}

function median(values: number[]): number | null {
  if (values.length === 0) return null
  const v = [...values].sort((a, b) => a - b)
  const mid = Math.floor(v.length / 2)
  return v.length % 2 ? v[mid] : (v[mid - 1] + v[mid]) / 2
}

/**
 * Agrupa achados em pontos: do mais antigo ao mais novo, cada achado entra no ponto do mesmo grau cujo centro
 * está a até {@code radius} unidades do mundo; se nenhum, abre um ponto novo. O centro é a média dos achados.
 */
export function groupSpawnSpots(spawns: ResourceSpawn[], radius: number): SpawnSpot[] {
  type Acc = { tier: number | null; sx: number; sz: number; list: ResourceSpawn[] }
  const spots: Acc[] = []
  const r2 = radius * radius
  const ordered = [...spawns].sort((a, b) => Date.parse(a.foundAt) - Date.parse(b.foundAt) || a.id - b.id)
  for (const s of ordered) {
    let best: Acc | null = null
    let bestD = Infinity
    for (const spot of spots) {
      if (spot.tier !== s.tier) continue
      const n = spot.list.length
      const dx = spot.sx / n - s.x
      const dz = spot.sz / n - s.z
      const d = dx * dx + dz * dz
      if (d <= r2 && d < bestD) { best = spot; bestD = d }
    }
    if (best) { best.sx += s.x; best.sz += s.z; best.list.push(s) }
    else spots.push({ tier: s.tier, sx: s.x, sz: s.z, list: [s] })
  }
  return spots.map((spot) => {
    const times = spot.list.map((s) => Date.parse(s.foundAt))
    const gaps = times.slice(1).map((t, i) => t - times[i])
    const gap = median(gaps)
    const last = spot.list[spot.list.length - 1]
    const lastAt = times[times.length - 1]
    const n = spot.list.length
    return {
      key: `${spot.tier ?? 'u'}:${spot.list[0].id}`,
      tier: spot.tier,
      x: Math.round(spot.sx / n),
      z: Math.round(spot.sz / n),
      count: n,
      spawns: spot.list,
      lastAt,
      medianGapMs: gap,
      nextAt: gap == null ? null : lastAt + gap,
      alive: !last.diedAt,
    }
  })
}
