import { describe, expect, it } from 'vitest'
import {
  formatHmsCountdown,
  getMsUntilNextDailyEvent,
  getMsUntilNextIntervalEvent,
  getNextDailyOccurrence,
  getNextIntervalOccurrence,
} from './dailyEventSchedule'

describe('dailyEventSchedule', () => {
  it('getNextDailyOccurrence returns later today when time not passed', () => {
    const now = new Date(2026, 2, 26, 5, 30, 0)
    const next = getNextDailyOccurrence(6, 0, now)
    expect(next.getHours()).toBe(6)
    expect(next.getDate()).toBe(26)
  })

  it('getNextDailyOccurrence rolls to tomorrow after event time', () => {
    const now = new Date(2026, 2, 26, 6, 0, 0)
    const next = getNextDailyOccurrence(6, 0, now)
    expect(next.getDate()).toBe(27)
    expect(next.getHours()).toBe(6)
  })

  it('getMsUntilNextDailyEvent is ~24h right at start', () => {
    const now = new Date(2026, 2, 26, 6, 0, 0)
    const ms = getMsUntilNextDailyEvent(6, 0, now)
    expect(ms).toBe(24 * 60 * 60 * 1000)
  })

  it('formatHmsCountdown pads segments', () => {
    expect(formatHmsCountdown((2 * 3600 + 5 * 60 + 7) * 1000)).toBe('02:05:07')
  })

  it('formatHmsCountdown shows days when over 24h', () => {
    expect(formatHmsCountdown((2 * 86400 + 3 * 3600) * 1000)).toBe('2d 03:00:00')
  })

  const majorAnchor = new Date(2026, 8, 27, 19, 0, 0, 0)

  it('interval: before anchor counts to anchor', () => {
    const now = new Date(2026, 8, 26, 20, 0, 0, 0)
    const next = getNextIntervalOccurrence(majorAnchor, 4, now)
    expect(next.getTime()).toBe(majorAnchor.getTime())
  })

  it('interval: at anchor start, next is +4 days', () => {
    const now = new Date(majorAnchor)
    const next = getNextIntervalOccurrence(majorAnchor, 4, now)
    expect(next.getDate()).toBe(1)
    expect(next.getMonth()).toBe(9)
    expect(next.getHours()).toBe(19)
  })

  it('interval: ms until next after anchor', () => {
    const now = new Date(2026, 8, 28, 12, 0, 0, 0)
    const ms = getMsUntilNextIntervalEvent(majorAnchor, 4, now)
    const next = new Date(now.getTime() + ms)
    expect(next.getDate()).toBe(1)
    expect(next.getMonth()).toBe(9)
  })
})
