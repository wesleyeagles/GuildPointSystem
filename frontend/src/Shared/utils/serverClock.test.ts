import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

const HOUR = 3600_000
const SERVER = new Date('2026-10-29T17:00:00.000Z').getTime()

async function loadClock() {
  vi.resetModules()
  return import('./serverClock')
}

describe('serverClock', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    // PC clock one hour ahead of the server (e.g. Windows still applying the old Brazilian DST).
    vi.setSystemTime(SERVER + HOUR)
  })
  afterEach(() => vi.useRealTimers())

  it('follows the server from the HTTP Date header', async () => {
    const { serverNow, trackServerDateHeader } = await loadClock()
    expect(serverNow()).toBe(SERVER + HOUR)
    trackServerDateHeader(new Date(SERVER).toUTCString())
    expect(Math.abs(serverNow() - SERVER)).toBeLessThan(1000)
  })

  it('keeps a precise serverTime unless the header is clearly off', async () => {
    const { serverNow, trackServerDateHeader, trackServerTime } = await loadClock()
    trackServerTime(new Date(SERVER + 123).toISOString())
    trackServerDateHeader(new Date(SERVER).toUTCString())
    expect(serverNow()).toBe(SERVER + 123)
  })

  it('ignores missing or invalid headers', async () => {
    const { serverNow, trackServerDateHeader } = await loadClock()
    trackServerDateHeader(null)
    trackServerDateHeader('not a date')
    expect(serverNow()).toBe(SERVER + HOUR)
  })
})
