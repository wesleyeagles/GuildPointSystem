import { spriteBackgroundStyle } from '@/Domain/Catalog/utils/spriteUtils'

interface SpriteIconProps {
  spriteSheet?: string
  iconId: number
  size?: number
  spriteCols?: number
  className?: string
}

export function SpriteIcon({
  spriteSheet = '/sprites/ringseamulets.png',
  iconId,
  size = 64,
  spriteCols,
  className,
}: SpriteIconProps) {
  return (
    <span
      className={className}
      style={spriteBackgroundStyle(spriteSheet, iconId, size, spriteCols)}
      aria-hidden="true"
    />
  )
}
