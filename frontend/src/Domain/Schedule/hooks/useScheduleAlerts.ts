import { useEffect } from 'react'
import {
  HEADER_SCHEDULED_EVENTS,
  TEN_MINUTE_WARNING_MS,
  getScheduledOccurrenceStart,
} from '@/Shared/utils/dailyEventSchedule'
import { subscribeTopic } from '@/Shared/websocket/socketClient'

export interface ScheduleTenMinuteAlert {
  type: 'SCHEDULE_TEN_MINUTE'
  eventId: string
  label: string
  minutesRemaining: number
  startsAt: string
}

function parseAlert(body: string): ScheduleTenMinuteAlert | null {
  try {
    const parsed = JSON.parse(body) as unknown
    if (!parsed || typeof parsed !== 'object') {
      return null
    }
    const raw = parsed as Record<string, unknown>
    const type = raw.type ?? raw.eventType
    if (type !== 'SCHEDULE_TEN_MINUTE') {
      return null
    }
    const eventId = raw.eventId
    const label = raw.label
    const minutesRemaining = raw.minutesRemaining
    const startsAt = raw.startsAt
    if (
      typeof eventId !== 'string' ||
      typeof label !== 'string' ||
      typeof minutesRemaining !== 'number' ||
      typeof startsAt !== 'string'
    ) {
      return null
    }
    return {
      type: 'SCHEDULE_TEN_MINUTE',
      eventId,
      label,
      minutesRemaining,
      startsAt,
    }
  } catch {
    return null
  }
}

export function useScheduleAlertSubscription(onAlert: (alert: ScheduleTenMinuteAlert) => void) {
  useEffect(() => {
    let cancelled = false
    let unsubscribe: (() => void) | undefined

    subscribeTopic('/topic/schedule', (message) => {
      const alert = parseAlert(message.body)
      if (alert) onAlert(alert)
    }).then((fn) => {
      if (cancelled) fn()
      else unsubscribe = fn
    })

    return () => {
      cancelled = true
      unsubscribe?.()
    }
  }, [onAlert])
}

/** Fallback local: mesmo critério do servidor (primeiro tick com ≤10 min). */
export function useLocalScheduleAlertPolling(onAlert: (alert: ScheduleTenMinuteAlert) => void) {
  useEffect(() => {
    const tick = () => {
      const now = new Date()
      for (const event of HEADER_SCHEDULED_EVENTS) {
        const start = getScheduledOccurrenceStart(event, now)
        const msUntil = start.getTime() - now.getTime()
        if (msUntil > TEN_MINUTE_WARNING_MS || msUntil <= 0) {
          continue
        }
        onAlert({
          type: 'SCHEDULE_TEN_MINUTE',
          eventId: event.id,
          label: event.label,
          minutesRemaining: 10,
          startsAt: start.toISOString(),
        })
      }
    }

    tick()
    const id = window.setInterval(tick, 1000)
    return () => window.clearInterval(id)
  }, [onAlert])
}
