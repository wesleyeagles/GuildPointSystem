import { useState } from 'react'
import { publicAssetUrl } from '@/Shared/utils/publicAssetUrl'
import './MemberClassIcon.styles.scss'

interface MemberClassIconProps {
  classImageUrl?: string | null
  classLabel?: string | null
  size?: 'sm' | 'md'
}

function classInitials(label: string): string {
  return label
    .split(/\s+/)
    .map((word) => word[0] ?? '')
    .join('')
    .slice(0, 2)
    .toUpperCase()
}

export function MemberClassIcon({
  classImageUrl,
  classLabel,
  size = 'sm',
}: MemberClassIconProps) {
  const [imageFailed, setImageFailed] = useState(false)

  if (!classImageUrl || !classLabel) return null

  return (
    <span
      className={`member-class-icon member-class-icon--${size}`}
      aria-label={classLabel}
    >
      {imageFailed ? (
        <span className="member-class-icon__fallback" aria-hidden="true">
          {classInitials(classLabel)}
        </span>
      ) : (
        <img
          src={publicAssetUrl(classImageUrl)}
          alt=""
          className="member-class-icon__image"
          onError={() => setImageFailed(true)}
        />
      )}
      <span className="member-class-icon__tooltip" role="tooltip">
        {classLabel}
      </span>
    </span>
  )
}
