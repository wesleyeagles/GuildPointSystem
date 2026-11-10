import { KEEN_PCT } from './data/boosts'

/** Efeitos já somados: a = "All attack %", c = "Critical probability", f = "Force Attack Power %". */
export interface Effects { a?: number; c?: number; f?: number }

export interface KbWeapon {
  c: string
  n: string
  l: number
  t: string
  r: string
  atk?: [number, number]
  fo?: [number, number]
  e?: Effects
  /** Índice na folha de ícones; g = grau (cor da borda). */
  i?: number
  g?: number
}

export interface KbAccessory { c: string; n: string; l: number; r: string; e: Effects; i?: number; g?: number }

export interface KbData {
  source: string
  server: string
  scrapedAt: string
  weapons: KbWeapon[]
  amulets: KbAccessory[]
  rings: KbAccessory[]
}

export type DamageMode = 'atk' | 'force'

export interface DamageInput {
  weapon: KbWeapon | null
  mode: DamageMode
  keen: number
  /** Efeitos de acessórios e da própria arma. */
  effects: Effects[]
  /** % de ataque de buffs, adrena e outros — todos somam no mesmo bônus. */
  bonusPct: number[]
  /** Pontos de crítico vindos de buffs. */
  bonusCrit: number
}

export interface DamageResult {
  base: [number, number]
  keenPct: number
  /** Soma dos % de acessórios, buffs, adrena, gerador e outros (sem o Keen). */
  atkPct: number
  /** Keen + atkPct: tudo entra na mesma soma. */
  totalPct: number
  multiplier: number
  final: [number, number]
  average: number
  crit: number
}

export function weaponRange(w: KbWeapon | null, mode: DamageMode): [number, number] {
  if (!w) return [0, 0]
  return (mode === 'force' ? w.fo : w.atk) ?? [0, 0]
}

/** Modo padrão: força se a arma só tem (ou tem mais) ataque de força. */
export function defaultMode(w: KbWeapon | null): DamageMode {
  if (!w?.fo) return 'atk'
  return (w.fo[1] > (w.atk?.[1] ?? 0)) ? 'force' : 'atk'
}

const sum = (xs: number[]) => xs.reduce((s, x) => s + x, 0)
const round2 = (x: number) => Math.round(x * 100) / 100
/** floor tolerante a erro de ponto flutuante (1450 × 2.3 = 3334.9999…). */
const floor = (x: number) => Math.floor(x + 1e-6)

/**
 * Ataque da arma × (1 + Keen% + soma dos % de ataque) — tudo numa soma só.
 * Conferido no jogo (10/11/2026, Gen. Attack da janela Character): Judgement 2693–3367, Keen +2 e
 * +189% de bônus → 8052–10067 = base × 2,99. Não considera defesa, resistência nem crítico do alvo.
 */
export function calcDamage(input: DamageInput): DamageResult {
  const base = weaponRange(input.weapon, input.mode)
  const keenPct = KEEN_PCT[Math.max(0, Math.min(7, input.keen))]
  const fromEffects = sum(input.effects.map(e => (e.a ?? 0) + (input.mode === 'force' ? e.f ?? 0 : 0)))
  const atkPct = round2(fromEffects + sum(input.bonusPct))
  const totalPct = round2(keenPct + atkPct)
  const multiplier = 1 + totalPct / 100
  const final = base.map(v => floor(v * multiplier)) as [number, number]
  return {
    base,
    keenPct,
    atkPct,
    totalPct,
    multiplier: round2(multiplier),
    final,
    average: Math.floor((final[0] + final[1]) / 2),
    crit: round2(sum(input.effects.map(e => e.c ?? 0)) + input.bonusCrit),
  }
}
