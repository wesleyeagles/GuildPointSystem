import { mediaUrl } from '@/Shared/utils/mediaUrl'
import './MemberClassIcon.styles.scss'

interface MemberClassIconProps {
  classImageUrl?: string | null
  classLabel?: string | null
  size?: 'sm' | 'md'
}

export function MemberClassIcon({
  classImageUrl,
  classLabel,
  size = 'sm',
}: MemberClassIconProps) {
  if (!classImageUrl || !classLabel) return null

  return (
    <span
      className={`member-class-icon member-class-icon--${size}`}
      aria-label={classLabel}
    >
      <img src={mediaUrl(classImageUrl)} alt="" className="member-class-icon__image" />
      <span className="member-class-icon__tooltip" role="tooltip">
        {classLabel}
      </span>
    </span>
  )
}
