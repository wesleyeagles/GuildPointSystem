import type { Guild, RotationSlot } from '@/Domain/Rotation/hooks/useRotation'
import { getZonedParts, SCHEDULE_TIME_ZONE } from '@/Shared/utils/scheduleTimeZone'

/** A PB stays "next" this long after its time: the guild is often still killing that round's bosses. */
export const BOSS_GRACE_MINUTES = 5

export interface UpcomingBossSlot {
  /** Brasília HH:mm, as stored in the rotation. */
  time: string
  qg: string[]
  outpost: string[]
  /** Seconds until the slot time; negative while inside the grace window. */
  secondsUntil: number
}

/**
 * The guild's rotation slots ordered from the next one. Slot times are Brasília and repeat daily;
 * a slot keeps its place until {@link BOSS_GRACE_MINUTES} after it starts.
 */
export function upcomingBossSlots(
  slots: RotationSlot[],
  guild: Guild,
  now: Date,
): UpcomingBossSlot[] {
  const z = getZonedParts(now, SCHEDULE_TIME_ZONE)
  const nowSeconds = z.hour * 3600 + z.minute * 60 + z.second
  const day = 24 * 3600
  return slots
    .map((slot) => {
      const bossesOf = (area: 'qg' | 'outpost') =>
        slot[area].filter((a) => a.guild === guild).flatMap((a) => a.bosses)
      const [hour, minute] = slot.time.split(':').map(Number)
      let secondsUntil = hour * 3600 + minute * 60 - nowSeconds
      if (secondsUntil <= -BOSS_GRACE_MINUTES * 60) secondsUntil += day
      return { time: slot.time, qg: bossesOf('qg'), outpost: bossesOf('outpost'), secondsUntil }
    })
    .filter((s) => s.qg.length + s.outpost.length > 0)
    .sort((a, b) => a.secondsUntil - b.secondsUntil)
}

export function formatCountdown(seconds: number) {
  const h = Math.floor(seconds / 3600)
  const m = Math.floor((seconds % 3600) / 60)
  const s = seconds % 60
  const pad = (n: number) => String(n).padStart(2, '0')
  return h > 0 ? `${h}h ${pad(m)}m` : `${pad(m)}:${pad(s)}`
}
