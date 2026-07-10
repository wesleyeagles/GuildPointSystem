export interface RouletteSegment {
  id: string | number
  label: string
}

export interface RouletteProps {
  segments: RouletteSegment[]
  winnerIndex: number
  seed?: number | null
  spinning?: boolean
  onComplete?: (winnerIndex: number) => void
}

export const MIN_SPIN_MS = 10000
