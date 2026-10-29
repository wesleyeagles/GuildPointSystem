import { describe, expect, it } from 'vitest'
import type { RotationSlot } from '@/Domain/Rotation/hooks/useRotation'
import { formatCountdown, upcomingBossSlots } from './nextBosses'

const slots: RotationSlot[] = [
  { time: '00:30', qg: [{ guild: 'Blacklist', bosses: ['Warbeast'] }], outpost: [] },
  { time: '14:00', qg: [{ guild: 'Quit', bosses: ['Splinter'] }], outpost: [{ guild: 'Blacklist', bosses: ['Great Lava', 'Pitboss'] }] },
  { time: '16:00', qg: [{ guild: 'Energy', bosses: ['Hammer'] }], outpost: [] },
  { time: '20:00', qg: [{ guild: 'Blacklist', bosses: ['Lord'] }], outpost: [{ guild: 'Blacklist', bosses: ['Crag'] }] },
]

/** Brasília is UTC-3 all year (no DST since 2019). */
const brt = (hhmmss: string) => new Date(`2026-10-29T${hhmmss}-03:00`)
const times = (now: Date) => upcomingBossSlots(slots, 'Blacklist', now).map((s) => s.time)

describe('upcomingBossSlots', () => {
  it("lists only the guild's slots, from the next one", () => {
    expect(times(brt('13:00:00'))).toEqual(['14:00', '20:00', '00:30'])
    const [next] = upcomingBossSlots(slots, 'Blacklist', brt('13:00:00'))
    expect(next).toEqual({ time: '14:00', qg: [], outpost: ['Great Lava', 'Pitboss'], secondsUntil: 3600 })
  })

  it('keeps a slot as next for 5 minutes after it starts', () => {
    expect(upcomingBossSlots(slots, 'Blacklist', brt('14:04:59'))[0]).toMatchObject({ time: '14:00', secondsUntil: -299 })
    expect(times(brt('14:05:00'))[0]).toBe('20:00')
  })

  it('wraps past midnight', () => {
    expect(times(brt('23:00:00'))).toEqual(['00:30', '14:00', '20:00'])
    expect(upcomingBossSlots(slots, 'Blacklist', brt('20:10:00'))[0]).toMatchObject({ time: '00:30', secondsUntil: 4 * 3600 + 20 * 60 })
  })

  it('uses Brasília time regardless of the instant’s UTC representation', () => {
    // 17:00 UTC = 14:00 Brasília.
    expect(upcomingBossSlots(slots, 'Blacklist', new Date('2026-10-29T17:00:00Z'))[0]).toMatchObject({ time: '14:00', secondsUntil: 0 })
  })

  it('is empty when the guild has no slot', () => {
    expect(upcomingBossSlots(slots, 'Yonkou', brt('12:00:00'))).toEqual([])
  })
})

describe('formatCountdown', () => {
  it('shows mm:ss under an hour and hours + minutes above', () => {
    expect(formatCountdown(65)).toBe('01:05')
    expect(formatCountdown(3600 + 5 * 60 + 9)).toBe('1h 05m')
  })
})
