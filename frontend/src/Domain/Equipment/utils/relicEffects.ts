import data from '../relicData.json'
import type { RelicEntry } from '../types'

type Kind = 'percent' | 'number' | 'integer' | 'seconds'
interface EffectInfo { name: string; kind: string }
interface RelicInfo { name: string; levels: [number, number][][] }

const effects = data.effects as Record<string, EffectInfo>
const relics = data.relics as unknown as Record<string, RelicInfo>

export interface RelicEffectTotal { code: number; name: string; value: number; text: string }

export function relicName(id: number): string | undefined {
  return relics[String(id)]?.name
}

/** Texto como na janela Relics: "Attack: 2.0% increase", "Dodge: 1.30 increase". */
export function formatRelicEffect(code: number, value: number): string {
  const info = effects[String(code)]
  const name = info?.name ?? `Efeito #${code}`
  const kind = (info?.kind ?? 'number') as Kind
  const size = Math.abs(value)
  const amount = kind === 'percent' ? `${(size * 100).toFixed(1)}%`
    : kind === 'seconds' ? `${(size / 1000).toFixed(2)} sec.`
      : kind === 'integer' ? String(Math.round(size))
        : size.toFixed(2)
  return `${name}: ${amount} ${value < 0 ? 'decrease' : 'increase'}`
}

/** Relic do catálogo de itens (game_code "relic003" → 3); null para os outros itens. */
export function relicIdFromGameCode(gameCode: string | null | undefined): number | null {
  const match = gameCode?.match(/^relic(\d+)$/i)
  return match && relics[String(Number(match[1]))] ? Number(match[1]) : null
}

/** Efeitos de cada upgrade (+0, +1…) já no texto da janela Relics. */
export function relicUpgradeTexts(id: number): string[][] {
  return (relics[String(id)]?.levels ?? []).map((level) => level.map(([code, value]) => formatRelicEffect(code, value)))
}

/** Efeitos de uma relic no upgrade indicado (0 = base). */
export function relicLevelEffects(id: number, upgrade: number): [number, number][] {
  const levels = relics[String(id)]?.levels ?? []
  return levels[Math.min(Math.max(upgrade, 0), levels.length - 1)] ?? []
}

/** Soma dos efeitos ("Current effects"), em ordem de código como o jogo. */
export function relicEffectTotals(owned: RelicEntry[]): RelicEffectTotal[] {
  const sum = new Map<number, number>()
  for (const relic of owned)
    for (const [code, value] of relicLevelEffects(relic.id, relic.upgrade))
      sum.set(code, (sum.get(code) ?? 0) + value)
  return [...sum.entries()].sort(([a], [b]) => a - b).filter(([, v]) => Math.abs(v) > 1e-9).map(([code, raw]) => {
    const value = Math.round(raw * 1e6) / 1e6
    return { code, name: effects[String(code)]?.name ?? `Efeito #${code}`, value, text: formatRelicEffect(code, value) }
  })
}
