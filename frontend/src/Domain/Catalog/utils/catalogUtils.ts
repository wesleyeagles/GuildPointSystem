import type { EffectDisplayType } from '@/Domain/types/models'

/** RF stores time bonuses as millis-like units; launcher uses ÷2000 when |raw| ≥ 100. */
function secDivisor(code: number | undefined, rawValue: number): number {
  if (code === 25 && Math.abs(rawValue) >= 100) return 2000
  return 1000
}

export function formatEffectValue(
  displayType: EffectDisplayType,
  rawValue: number | null | undefined,
  code?: number
): string {
  if (displayType === 'BOOLEAN') return 'true'
  if (rawValue == null) return ''
  if (displayType === 'PERCENT_100') {
    const pct = rawValue * 100
    const rounded = Math.round(pct * 10) / 10
    return `${rounded}%`
  }
  if (displayType === 'SEC_MILLIS') {
    const divisor = secDivisor(code, rawValue)
    const sec = Math.abs(rawValue) / divisor
    return trimTrailingZeros(sec)
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
  0: '#FFFFFF',
  7: '#61ff39',
}

export function gradeColor(grade: number): string {
  return GRADE_COLORS[grade] ?? '#FFFFFF'
}

export const ARMOR_GRADE_NAMES = [
  'Normal',
  'Intense',
  'Unknown',
  'Orange',
  'Relic',
  'Pink',
  'Green',
  'Hero',
  'DarkRay',
  'Unknown',
  'Leon',
  'Pvp',
  'Red',
] as const

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
