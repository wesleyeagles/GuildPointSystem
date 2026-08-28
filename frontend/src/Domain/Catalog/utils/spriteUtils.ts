import type { CSSProperties } from 'react'

/** Ring/amulet sheet: 32 icons per row (2048px sheet). */
export const RING_SPRITE_COLS = 32

/** Armor sheets: 64 icons per row (4096px sheet, 64px per cell). */
export const ARMOR_SPRITE_COLS = 64

const RING_SHEET = '/sprites/ringseamulets.png'

const ARMOR_SHEETS = new Set([
  '/sprites/helmet.png',
  '/sprites/upper.png',
  '/sprites/lower.png',
  '/sprites/gloves.png',
  '/sprites/shoes.png',
  '/sprites/weapon.png',
])

export function spriteColsForSheet(spriteSheet: string): number {
  return ARMOR_SHEETS.has(spriteSheet) ? ARMOR_SPRITE_COLS : RING_SPRITE_COLS
}

/**
 * RF Online item icons: fixed columns per sheet, IconID is the linear index.
 * Same math as ring/amulet — only the column count differs (32 vs 64).
 */
export function spriteBackgroundStyle(
  spriteSheet: string,
  iconId: number,
  size = 64,
  spriteCols?: number
): CSSProperties {
  const cols =
    spriteSheet === RING_SHEET ? spriteCols ?? RING_SPRITE_COLS : spriteColsForSheet(spriteSheet)

  const col = iconId % cols
  const row = Math.floor(iconId / cols)
  const sheetSize = cols * size

  return {
    width: size,
    height: size,
    display: 'block',
    backgroundImage: `url(${spriteSheet})`,
    backgroundPosition: `${-col * size}px ${-row * size}px`,
    backgroundSize: `${sheetSize}px ${sheetSize}px`,
    backgroundRepeat: 'no-repeat',
    flexShrink: 0,
  }
}
