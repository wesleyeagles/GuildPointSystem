function trimTrailingSlash(url: string): string {
  return url.replace(/\/$/, '')
}

export function getApiBaseUrl(): string {
  return import.meta.env.VITE_API_BASE_URL ?? '/api'
}

export function getWsBaseUrl(): string {
  const base = import.meta.env.VITE_WS_BASE_URL
  if (base) return `${trimTrailingSlash(base)}/ws`
  if (typeof window !== 'undefined') return `${window.location.origin}/ws`
  return 'http://localhost:8080/ws'
}

export function getMediaBaseUrl(): string {
  const media = import.meta.env.VITE_MEDIA_BASE_URL
  if (media) return trimTrailingSlash(media)
  const api = import.meta.env.VITE_API_BASE_URL
  if (api) return trimTrailingSlash(api.replace(/\/api$/, ''))
  if (typeof window !== 'undefined') return window.location.origin
  return 'http://localhost:8080'
}
