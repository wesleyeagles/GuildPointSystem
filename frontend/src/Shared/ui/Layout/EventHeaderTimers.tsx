import { formatHmsCountdown, getScheduledOccurrenceStart } from '@/Shared/utils/dailyEventSchedule'
import { useGuildScheduledEvents } from '@/Domain/Schedule/hooks/useGuildScheduledEvents'
import { SCHEDULE_TIME_ZONE, SERVER_TIME_ZONE } from '@/Shared/utils/scheduleTimeZone'

interface EventHeaderTimersProps {
  now: Date
}

function formatHm(date: Date, timeZone: string) {
  return date.toLocaleTimeString('pt-BR', { timeZone, hour: '2-digit', minute: '2-digit', hour12: false })
}

export function EventHeaderTimers({ now }: EventHeaderTimersProps) {
  const events = useGuildScheduledEvents()
  return (
    <div className="layout__event-timers" aria-label="Contagem regressiva de eventos">
      {events.map((event) => {
        const next = getScheduledOccurrenceStart(event, now)
        const display = formatHmsCountdown(Math.max(0, next.getTime() - now.getTime()))
        const title = `Próximo ${event.label}: ${formatHm(next, SERVER_TIME_ZONE)} servidor · ${formatHm(next, SCHEDULE_TIME_ZONE)} Brasília`
        return (
          <div key={event.id} className="layout__event-timer" role="timer" title={title}>
            <span className="layout__event-timer-label">{event.label}</span>
            <span className="layout__event-timer-value">{display}</span>
          </div>
        )
      })}
    </div>
  )
}
