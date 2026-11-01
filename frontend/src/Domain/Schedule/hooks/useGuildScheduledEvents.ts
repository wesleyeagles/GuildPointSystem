import { useMemo } from 'react'
import { HOME_GUILD, useRotation } from '@/Domain/Rotation/hooks/useRotation'
import { eventsForGuild, HEADER_SCHEDULED_EVENTS } from '@/Shared/utils/dailyEventSchedule'

/** Eventos do cabeçalho com PB HQ/Colônia só nos horários em que a nossa guilda tem boss. */
export function useGuildScheduledEvents() {
  const { data } = useRotation()
  return useMemo(() => eventsForGuild(HEADER_SCHEDULED_EVENTS, data?.slots, HOME_GUILD), [data])
}
