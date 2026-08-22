import { getMediaBaseUrl } from '@/Shared/utils/env'

export function mediaUrl(path: string | null | undefined): string {
  const MEDIA_BASE = getMediaBaseUrl()
  if (!path) return ''
  if (path.startsWith('http://') || path.startsWith('https://')) return path
  return `${MEDIA_BASE}${path.startsWith('/') ? path : `/${path}`}`
}
