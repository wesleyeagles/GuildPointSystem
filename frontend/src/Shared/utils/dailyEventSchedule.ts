import {
  getZonedParts,
  SCHEDULE_TIME_ZONE,
  SERVER_TIME_ZONE,
  zonedLocalToUtc,
} from '@/Shared/utils/scheduleTimeZone'

const MS_PER_DAY = 24 * 60 * 60 * 1000
const TZ = SCHEDULE_TIME_ZONE

export function getNextDailyOccurrence(
  hour: number,
  minute: number,
  now: Date,
  timeZone: string = TZ,
): Date {
  const z = getZonedParts(now, timeZone)
  let candidate = zonedLocalToUtc(z.year, z.month, z.day, hour, minute, timeZone)
  if (now.getTime() >= candidate.getTime()) {
    const tomorrow = new Date(candidate.getTime() + MS_PER_DAY)
    const tz = getZonedParts(tomorrow, timeZone)
    candidate = zonedLocalToUtc(tz.year, tz.month, tz.day, hour, minute, timeZone)
  }
  return candidate
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
  /** Fuso em que hour/minute são definidos; padrão Brasília. */
  timeZone?: string
}

/** Área da rotação de bosses de um PB (QG = PB HQ, outpost = PB Colônia). */
export type RotationArea = 'qg' | 'outpost'

/** Vários horários fixos por dia; conta para o próximo deles. */
export type MultiDailyScheduledEvent = {
  kind: 'multiDaily'
  id: string
  label: string
  hours: number[]
  minute: number
  timeZone?: string
  /** Com área, só contam os horários em que a guilda tem boss nela (ver {@link eventsForGuild}). */
  area?: RotationArea
}

export type IntervalScheduledEvent = {
  kind: 'interval'
  id: string
  label: string
  intervalDays: number
  /** Uma ocorrência conhecida do ciclo (hora já embutida no Date). */
  anchor: Date
}

export type ScheduledEvent = DailyScheduledEvent | MultiDailyScheduledEvent | IntervalScheduledEvent

export function getScheduledOccurrenceStart(event: ScheduledEvent, now: Date): Date {
  if (event.kind === 'daily') {
    return getNextDailyOccurrence(event.hour, event.minute, now, event.timeZone)
  }
  if (event.kind === 'multiDaily') {
    return event.hours
      .map((hour) => getNextDailyOccurrence(hour, event.minute, now, event.timeZone))
      .reduce((earliest, d) => (d.getTime() < earliest.getTime() ? d : earliest))
  }
  return getNextIntervalOccurrence(event.anchor, event.intervalDays, now)
}

export function getMsUntilScheduledEvent(event: ScheduledEvent, now: Date): number {
  const next = getScheduledOccurrenceStart(event, now)
  return Math.max(0, next.getTime() - now.getTime())
}

export const TEN_MINUTE_WARNING_MS = 10 * 60 * 1000
/** CW avisa 15 min antes; os outros eventos, 10 min (como no servidor). */
export const CW_WARNING_MS = 15 * 60 * 1000
export const warningMs = (eventId: string) => (eventId.startsWith('cw') ? CW_WARNING_MS : TEN_MINUTE_WARNING_MS)

function devTestScheduleEvent(): IntervalScheduledEvent | null {
  if (!import.meta.env.DEV) return null
  const leadSeconds = 12
  return {
    kind: 'interval',
    id: 'test-alert',
    label: 'TESTE',
    intervalDays: 10_000,
    anchor: new Date(Date.now() + 10 * 60 * 1000 + leadSeconds * 1000),
  }
}

const devTestEvent = devTestScheduleEvent()

export const HEADER_SCHEDULED_EVENTS: ScheduledEvent[] = [
  ...(devTestEvent ? [devTestEvent] : []),
  // CW no horário do servidor: 06:00 / 14:00 / 22:00 (= 00:00 / 08:00 / 16:00 Brasília).
  { kind: 'daily', id: 'cw1', label: 'CW1', hour: 6, minute: 0, timeZone: SERVER_TIME_ZONE },
  { kind: 'daily', id: 'cw2', label: 'CW2', hour: 14, minute: 0, timeZone: SERVER_TIME_ZONE },
  { kind: 'daily', id: 'cw3', label: 'CW3', hour: 22, minute: 0, timeZone: SERVER_TIME_ZONE },
  // PBs no horário de Brasília.
  {
    kind: 'multiDaily',
    id: 'pb-hq',
    label: 'PB HQ',
    hours: [2, 6, 10, 14, 18, 22],
    minute: 0,
    timeZone: SCHEDULE_TIME_ZONE,
    area: 'qg',
  },
  {
    kind: 'multiDaily',
    id: 'pb-colonia',
    label: 'PB Colônia',
    hours: [2, 10, 18],
    minute: 0,
    timeZone: SCHEDULE_TIME_ZONE,
    area: 'outpost',
  },
  {
    kind: 'multiDaily',
    id: 'pb-middle',
    label: 'PB Middle',
    // 00:00 / 12:00 Moscou.
    hours: [6, 18],
    minute: 0,
    timeZone: SCHEDULE_TIME_ZONE,
  },
]

/** O mínimo da rotação de bosses que os contadores precisam (horário de Brasília "HH:mm"). */
export interface RotationSlotHours {
  time: string
  qg: { guild: string; bosses: string[] }[]
  outpost: { guild: string; bosses: string[] }[]
}

/**
 * Os eventos como a guilda os vê: PB HQ/Colônia ficam só com os horários em que ela tem boss na
 * rotação (pulam para o próximo PB nosso) e somem se não sobrar nenhum. Sem rotação carregada,
 * todos os horários continuam valendo. Mesmo critério do servidor (GameEventDefinitions.forGuild).
 */
export function eventsForGuild(
  events: ScheduledEvent[],
  slots: RotationSlotHours[] | undefined,
  guild: string,
): ScheduledEvent[] {
  if (!slots) return events
  const ours = (area: RotationArea) =>
    new Set(
      slots
        .filter((slot) => slot.time.endsWith(':00'))
        .filter((slot) => slot[area].some((a) => a.guild === guild && a.bosses.length > 0))
        .map((slot) => Number(slot.time.slice(0, 2))),
    )
  return events.flatMap((event) => {
    if (event.kind !== 'multiDaily' || !event.area) return [event]
    const allowed = ours(event.area)
    const hours = event.hours.filter((hour) => allowed.has(hour))
    return hours.length > 0 ? [{ ...event, hours }] : []
  })
}
