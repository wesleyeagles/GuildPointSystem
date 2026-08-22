import type { CSSProperties } from 'react'

const SPRITE_COLS = 32

export function spriteBackgroundStyle(
  spriteSheet: string,
  iconId: number,
  size = 64
): CSSProperties {
  const col = iconId % SPRITE_COLS
  const row = Math.floor(iconId / SPRITE_COLS)
  const sheetSize = SPRITE_COLS * size

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
