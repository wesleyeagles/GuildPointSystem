import type { Guild, RotationSlot } from '@/Domain/Rotation/hooks/useRotation'
import { SCHEDULE_TIME_ZONE } from '@/Shared/utils/scheduleTimeZone'

const brasiliaClock = new Intl.DateTimeFormat('en-GB', {
  timeZone: SCHEDULE_TIME_ZONE,
  hour: '2-digit',
  minute: '2-digit',
  hourCycle: 'h23',
})

export function brasiliaRotationTime(timestamp: number) {
  return brasiliaClock.format(timestamp)
}

export function activeRotationTime(slots: RotationSlot[], time: string) {
  let earliest: string | undefined
  let next: string | undefined
  for (const slot of slots) {
    if (!earliest || slot.time < earliest) earliest = slot.time
    if (slot.time >= time && (!next || slot.time < next)) next = slot.time
  }
  return next ?? earliest
}

export function rotationTime(time: string, offset: number) {
  const [hour, minute] = time.split(':').map(Number)
  const shifted = hour + offset
  return `${String(shifted % 24).padStart(2, '0')}:${String(minute).padStart(2, '0')}${shifted >= 24 ? ' +1' : ''}`
}
export function rotationTotals(slots: RotationSlot[], guild: Guild) {
  const count = (area: 'qg' | 'outpost') => slots.flatMap(s => s[area]).filter(a => a.guild === guild).reduce((n, a) => n + a.bosses.length, 0)
  return { qg: count('qg'), outpost: count('outpost') }
}
