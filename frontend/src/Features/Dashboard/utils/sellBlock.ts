import type { Member } from '@/Domain/types/models'
import { serverNow } from '@/Shared/utils/serverClock'

/** Today in Brasília as yyyy-mm-dd (same calendar the sale block uses). */
export const todayBrt = () => new Date(serverNow()).toLocaleDateString('en-CA', { timeZone: 'America/Sao_Paulo' })

/** yyyy-mm-dd → dd/mm/yyyy. */
export const formatDay = (day: string) => day.split('-').reverse().join('/')

/** yyyy-mm-dd `days` after `day` (calendar math in UTC so DST never skips a day). */
export const addDays = (day: string, days: number) => {
  const d = new Date(`${day}T00:00:00Z`)
  d.setUTCDate(d.getUTCDate() + days)
  return d.toISOString().slice(0, 10)
}

/** Blocked through `blockedForSellUntil` (inclusive); checked here too so the lock leaves at midnight. */
export const sellBlockedUntil = (m: Member): string | null =>
  m.blockedForSellUntil && m.blockedForSellUntil >= todayBrt() ? m.blockedForSellUntil : null
