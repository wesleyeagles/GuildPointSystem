/** Horários no fuso local do navegador. */

const MS_PER_DAY = 24 * 60 * 60 * 1000

export function getNextDailyOccurrence(hour: number, minute: number, now: Date): Date {
  const next = new Date(now)
  next.setSeconds(0, 0)
  next.setHours(hour, minute, 0, 0)
  if (now.getTime() >= next.getTime()) {
    next.setDate(next.getDate() + 1)
  }
  return next
}

export function getMsUntilNextDailyEvent(hour: number, minute: number, now: Date): number {
  const next = getNextDailyOccurrence(hour, minute, now)
  return Math.max(0, next.getTime() - now.getTime())
}

/** Próxima ocorrência em anchor + k·intervalDays (k inteiro), após `now`. */
export function getNextIntervalOccurrence(anchor: Date, intervalDays: number, now: Date): Date {
  const intervalMs = intervalDays * MS_PER_DAY
  const anchorMs = anchor.getTime()
  if (now.getTime() < anchorMs) {
    return new Date(anchorMs)
  }
  const elapsed = now.getTime() - anchorMs
  const periods = Math.floor(elapsed / intervalMs) + 1
  return new Date(anchorMs + periods * intervalMs)
}

export function getMsUntilNextIntervalEvent(anchor: Date, intervalDays: number, now: Date): number {
  const next = getNextIntervalOccurrence(anchor, intervalDays, now)
  return Math.max(0, next.getTime() - now.getTime())
}

function pad2(n: number) {
  return String(n).padStart(2, '0')
}

/** HH:MM:SS ou Nd HH:MM:SS quando passa de 24h. */
export function formatHmsCountdown(totalMs: number): string {
  const totalSec = Math.floor(totalMs / 1000)
  const days = Math.floor(totalSec / 86400)
  const h = Math.floor((totalSec % 86400) / 3600)
  const m = Math.floor((totalSec % 3600) / 60)
  const s = totalSec % 60
  const time = `${pad2(h)}:${pad2(m)}:${pad2(s)}`
  return days > 0 ? `${days}d ${time}` : time
}

export type DailyScheduledEvent = {
  kind: 'daily'
  id: string
  label: string
  hour: number
  minute: number
}

export type IntervalScheduledEvent = {
  kind: 'interval'
  id: string
  label: string
  intervalDays: number
  /** Uma ocorrência conhecida do ciclo (hora já embutida no Date). */
  anchor: Date
}

export type ScheduledEvent = DailyScheduledEvent | IntervalScheduledEvent

export function getMsUntilScheduledEvent(event: ScheduledEvent, now: Date): number {
  if (event.kind === 'daily') {
    return getMsUntilNextDailyEvent(event.hour, event.minute, now)
  }
  return getMsUntilNextIntervalEvent(event.anchor, event.intervalDays, now)
}

/** Próximo PB Major: domingo 27/09/2026 19:00, depois a cada 4 dias. */
const PBS_MAJOR_ANCHOR = new Date(2026, 8, 27, 19, 0, 0, 0)

export const HEADER_SCHEDULED_EVENTS: ScheduledEvent[] = [
  { kind: 'daily', id: 'cw1', label: 'CW1', hour: 6, minute: 0 },
  { kind: 'daily', id: 'cw2', label: 'CW2', hour: 14, minute: 0 },
  { kind: 'daily', id: 'cw3', label: 'CW3', hour: 22, minute: 0 },
  { kind: 'daily', id: 'pbs-elan', label: 'PB Elan', hour: 19, minute: 0 },
  {
    kind: 'interval',
    id: 'pbs-major',
    label: 'PB Major',
    intervalDays: 4,
    anchor: PBS_MAJOR_ANCHOR,
  },
]
