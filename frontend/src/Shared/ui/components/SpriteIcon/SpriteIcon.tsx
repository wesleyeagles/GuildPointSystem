import { spriteBackgroundStyle } from '@/Domain/Catalog/utils/spriteUtils'

interface SpriteIconProps {
  spriteSheet?: string
  iconId: number
  size?: number
  className?: string
}

export function SpriteIcon({
  spriteSheet = '/sprites/ringseamulets.png',
  iconId,
  size = 64,
  className,
}: SpriteIconProps) {
  return (
    <span
      className={className}
      style={spriteBackgroundStyle(spriteSheet, iconId, size)}
      aria-hidden="true"
    />
  )
}
