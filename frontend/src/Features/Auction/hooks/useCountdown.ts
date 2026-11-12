import { useEffect, useState } from 'react'
import { serverNow } from '@/Domain/Auction/hooks/useAuction'

/** Seconds left until `endsAt`, on the server clock. */
export function useCountdown(endsAt: string) {
  const [remaining, setRemaining] = useState(0)
  useEffect(() => {
    if (!endsAt) return
    const tick = () => {
      setRemaining(Math.max(0, Math.ceil((new Date(endsAt).getTime() - serverNow()) / 1000)))
    }
    tick()
    const timer = setInterval(tick, 200)
    return () => clearInterval(timer)
  }, [endsAt])
  return remaining
}

/** "12/11 20:00" in the viewer's time zone (same zone as the start-time input). */
export function formatStartsAt(iso: string) {
  return new Date(iso).toLocaleString('pt-BR', {
    day: '2-digit',
    month: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  })
}

/** Clock shown for a PAUSED auction (frozen). */
export function pausedSeconds(pausedRemainingMs: number | null) {
  return Math.ceil((pausedRemainingMs ?? 0) / 1000)
}

export function formatTime(seconds: number) {
  if (seconds <= 0) return '0:00'
  const h = Math.floor(seconds / 3600)
  const m = Math.floor((seconds % 3600) / 60)
  const s = (seconds % 60).toString().padStart(2, '0')
  if (h > 0) return `${h}:${m.toString().padStart(2, '0')}:${s}`
  return `${m}:${s}`
}
