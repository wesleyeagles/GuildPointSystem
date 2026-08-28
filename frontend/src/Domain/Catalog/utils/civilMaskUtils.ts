/** Canonical 8-char masks (RF Online Civil column, trailing zeros). */
const CIVIL_MASK_8_ALL = '11111000'
const CIVIL_MASK_8_ACCRETIA = '00001000'
const CIVIL_MASK_8_CORA = '00110000'
const CIVIL_MASK_8_BELLATO = '11000000'
const CIVIL_MASK_8_BELLATO_CORA = '11110000'

/** Values stored in DB after XLSX refresh (significant digits only). */
export const CIVIL_MASK_ALL = '11111'
export const CIVIL_MASK_ACCRETIA = '00001'
export const CIVIL_MASK_CORA = '00110'
export const CIVIL_MASK_BELLATO = '11000'
export const CIVIL_MASK_BELLATO_CORA = '11110'

const CIVIL_MASK_LABELS: Record<string, string[]> = {
  [CIVIL_MASK_8_ALL]: ['Bellato', 'Cora', 'Accretia'],
  [CIVIL_MASK_8_ACCRETIA]: ['Accretia'],
  [CIVIL_MASK_8_CORA]: ['Cora'],
  [CIVIL_MASK_8_BELLATO]: ['Bellato'],
  [CIVIL_MASK_8_BELLATO_CORA]: ['Bellato', 'Cora'],
}

export function normalizeCivilMask(civilMask: string): string {
  const trimmed = civilMask.trim()
  if (trimmed.length >= 8) {
    return trimmed.slice(0, 8).padEnd(8, '0')
  }
  return trimmed.padEnd(8, '0')
}

export function civilMaskToRaces(civilMask: string | null | undefined): string[] {
  if (!civilMask) return []
  const normalized = normalizeCivilMask(civilMask)
  return CIVIL_MASK_LABELS[normalized] ?? []
}

export function civilMaskLabel(civilMask: string | null | undefined): string {
  const races = civilMaskToRaces(civilMask)
  if (races.length === 0) return ''
  if (races.length === 3) return 'Todas as Raças'
  return races.join(', ')
}

export const CIVIL_MASK_OPTIONS = [
  { value: CIVIL_MASK_BELLATO, label: 'Bellato' },
  { value: CIVIL_MASK_CORA, label: 'Cora' },
  { value: CIVIL_MASK_BELLATO_CORA, label: 'Bellato + Cora' },
  { value: CIVIL_MASK_ACCRETIA, label: 'Accretia' },
]
