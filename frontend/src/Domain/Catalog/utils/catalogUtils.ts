import type { EffectDisplayType } from '@/Domain/types/models'

/** RF stores time bonuses as millis-like units; launcher uses ÷2000 when |raw| ≥ 100. */
function secDivisor(code: number | undefined, rawValue: number): number {
  if (code === 25 && Math.abs(rawValue) >= 100) return 2000
  return 1000
}

/** Weapon/set data stores codes 19, 28, 32 as whole percent points (e.g. 10 = 10%). */
function formatPercentDisplay(rawValue: number, code?: number): string {
  if ((code === 19 || code === 28 || code === 32) && Math.abs(rawValue) > 1) {
    return `${trimTrailingZeros(Math.round(rawValue * 10) / 10)}%`
  }
  const pct = Math.round(rawValue * 1000) / 10
  return `${pct}%`
}

export function formatEffectValue(
  displayType: EffectDisplayType,
  rawValue: number | null | undefined,
  code?: number
): string {
  if (displayType === 'BOOLEAN') return 'true'
  if (rawValue == null) return ''
  if (displayType === 'PERCENT_100') {
    return formatPercentDisplay(rawValue, code)
  }
  if (displayType === 'SEC_MILLIS') {
    const divisor = secDivisor(code, rawValue)
    const sec = Math.abs(rawValue) / divisor
    return trimTrailingZeros(sec)
  }
  if (displayType === 'FLAT') {
    return trimTrailingZeros(Math.round(rawValue * 100) / 100)
  }
  return String(Math.round(rawValue))
}

function trimTrailingZeros(value: number): string {
  return value
    .toFixed(3)
    .replace(/\.?0+$/, '')
}

export function formatEffectDirection(
  displayType: EffectDisplayType,
  rawValue: number | null | undefined
): 'Increase' | 'Decrease' | null {
  if (displayType === 'BOOLEAN') return null
  if (rawValue == null) return 'Increase'
  return rawValue < 0 ? 'Decrease' : 'Increase'
}

export function isTimedEffect(displayType: EffectDisplayType): boolean {
  return displayType === 'SEC_MILLIS'
}

export const GRADE_COLORS: Record<number, string> = {
  0: '#FFFFFF', // Normal
  1: '#FFD700', // Intense
  2: '#BF5FFF', // Purple
  3: '#FF9900', // Orange
  4: '#6EB5FF', // Relic
  6: '#39FF14', // Green
  7: '#61ff39', // Hero
  8: '#39FF14', // DarkRay
  9: '#FF4444', // PVP
  10: '#39FF14', // Leon
}

export function gradeColor(grade: number): string {
  return GRADE_COLORS[grade] ?? '#FFFFFF'
}

export const ARMOR_GRADE_NAMES = [
  'Normal',
  'Intense',
  'Purple',
  'Orange',
  'Relic',
  'Pink',
  'Green',
  'Hero',
  'DarkRay',
  'PVP',
  'Leon',
  'Pvp',
  'Red',
] as const

/** Grades omitted from catalog filter (unused or duplicate labels). */
const GRADE_FILTER_EXCLUDED = new Set([5, 11, 12])

export const GRADE_FILTER_OPTIONS = ARMOR_GRADE_NAMES.map((label, grade) => ({
  value: String(grade),
  label,
})).filter((opt) => !GRADE_FILTER_EXCLUDED.has(Number(opt.value)))

export function armorGradeLabel(grade: number): string {
  return ARMOR_GRADE_NAMES[grade] ?? `Grade ${grade}`
}

export function gradeLabel(grade: number): string {
  if (grade === 7) return 'Hero'
  if (grade === 0) return 'Normal'
  return ARMOR_GRADE_NAMES[grade] ?? `Grade ${grade}`
}

export function elementSummary(fire: number, water: number, soil: number, wind: number): string {
  const parts: string[] = []
  if (fire > 0) parts.push(`Fire ${fire}`)
  if (water > 0) parts.push(`Water ${water}`)
  if (soil > 0) parts.push(`Soil ${soil}`)
  if (wind > 0) parts.push(`Wind ${wind}`)
  return parts.join(' · ')
}
