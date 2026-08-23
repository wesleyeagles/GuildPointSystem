import type { CSSProperties } from 'react'

export const DEFAULT_SPRITE_COLS = 32

export const SPRITE_SHEET_COLS: Record<string, number> = {
  '/sprites/ringseamulets.png': 32,
  '/sprites/helmet.png': 128,
  '/sprites/upper.png': 128,
  '/sprites/lower.png': 128,
  '/sprites/gloves.png': 128,
  '/sprites/shoes.png': 128,
}

export function spriteColsForSheet(spriteSheet: string, explicitCols?: number): number {
  if (explicitCols != null && explicitCols > 0) return explicitCols
  return SPRITE_SHEET_COLS[spriteSheet] ?? DEFAULT_SPRITE_COLS
}

export function spriteBackgroundStyle(
  spriteSheet: string,
  iconId: number,
  size = 64,
  spriteCols?: number
): CSSProperties {
  const cols = spriteColsForSheet(spriteSheet, spriteCols)
  const col = iconId % cols
  const row = Math.floor(iconId / cols)
  const sheetSize = cols * size

  return {
    width: size,
    height: size,
    backgroundImage: `url(${spriteSheet})`,
    backgroundPosition: `-${col * size}px -${row * size}px`,
    backgroundSize: `${sheetSize}px ${sheetSize}px`,
    backgroundRepeat: 'no-repeat',
    flexShrink: 0,
  }
}
