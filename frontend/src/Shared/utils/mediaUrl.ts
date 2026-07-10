const MEDIA_BASE = 'http://localhost:8080'

export function mediaUrl(path: string | null | undefined): string {
  if (!path) return ''
  if (path.startsWith('http://') || path.startsWith('https://')) return path
  return `${MEDIA_BASE}${path.startsWith('/') ? path : `/${path}`}`
}
