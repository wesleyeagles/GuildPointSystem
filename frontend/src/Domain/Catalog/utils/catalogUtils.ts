import type { EffectDisplayType } from '@/Domain/types/models'

export function formatEffectValue(
  displayType: EffectDisplayType,
  rawValue: number | null | undefined
): string {
  if (displayType === 'BOOLEAN') return 'true'
  if (rawValue == null) return ''
  if (displayType === 'PERCENT_100') {
    const pct = rawValue * 100
    const rounded = Math.round(pct * 10) / 10
    return `${rounded}%`
  }
  return String(Math.round(rawValue))
}

export const GRADE_COLORS: Record<number, string> = {
  0: '#FFFFFF',
  7: '#61ff39',
}

export function gradeColor(grade: number): string {
  return GRADE_COLORS[grade] ?? '#FFFFFF'
}

export function gradeLabel(grade: number): string {
  if (grade === 7) return 'Hero'
  if (grade === 0) return 'Normal'
  return `Grade ${grade}`
}

export function elementSummary(fire: number, water: number, soil: number, wind: number): string {
  const parts: string[] = []
  if (fire > 0) parts.push(`Fire ${fire}`)
  if (water > 0) parts.push(`Water ${water}`)
  if (soil > 0) parts.push(`Soil ${soil}`)
  if (wind > 0) parts.push(`Wind ${wind}`)
  return parts.join(' · ')
}
