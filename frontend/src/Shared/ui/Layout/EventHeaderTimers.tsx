import {
  formatHmsCountdown,
  getMsUntilScheduledEvent,
  HEADER_SCHEDULED_EVENTS,
} from '@/Shared/utils/dailyEventSchedule'

interface EventHeaderTimersProps {
  now: Date
}

export function EventHeaderTimers({ now }: EventHeaderTimersProps) {
  return (
    <div className="layout__event-timers" aria-label="Contagem regressiva de eventos">
      {HEADER_SCHEDULED_EVENTS.map((event) => {
        const remainingMs = getMsUntilScheduledEvent(event, now)
        const display = formatHmsCountdown(remainingMs)
        return (
          <div key={event.id} className="layout__event-timer" role="timer">
            <span className="layout__event-timer-label">{event.label}</span>
            <span className="layout__event-timer-value" title={`Próximo: ${event.label}`}>
              {display}
            </span>
          </div>
        )
      })}
    </div>
  )
}
