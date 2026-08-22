export const CIVIL_MASK_ALL = '11111000'
export const CIVIL_MASK_ACCRETIA = '00001000'
export const CIVIL_MASK_CORA = '00110000'
export const CIVIL_MASK_BELLATO = '11000000'

const CIVIL_MASK_LABELS: Record<string, string[]> = {
  [CIVIL_MASK_ALL]: ['Bellato', 'Cora', 'Accretia'],
  [CIVIL_MASK_ACCRETIA]: ['Accretia'],
  [CIVIL_MASK_CORA]: ['Cora'],
  [CIVIL_MASK_BELLATO]: ['Bellato'],
}

export function civilMaskToRaces(civilMask: string | null | undefined): string[] {
  if (!civilMask) return []
  return CIVIL_MASK_LABELS[civilMask] ?? [civilMask]
}

export function civilMaskLabel(civilMask: string | null | undefined): string {
  const races = civilMaskToRaces(civilMask)
  if (races.length === 3) return 'Todas as Raças'
  return races.join(', ')
}

export const CIVIL_MASK_OPTIONS = [
  { value: CIVIL_MASK_ALL, label: 'Todas as Raças' },
  { value: CIVIL_MASK_BELLATO, label: 'Bellato' },
  { value: CIVIL_MASK_CORA, label: 'Cora' },
  { value: CIVIL_MASK_ACCRETIA, label: 'Accretia' },
]
