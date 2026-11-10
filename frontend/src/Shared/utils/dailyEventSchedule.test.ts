import { describe, expect, it } from 'vitest'
import {
  eventsForGuild,
  formatHmsCountdown,
  getScheduledOccurrenceStart,
  HEADER_SCHEDULED_EVENTS,
  getMsUntilNextDailyEvent,
  getMsUntilNextIntervalEvent,
  getNextDailyOccurrence,
  getNextIntervalOccurrence,
} from './dailyEventSchedule'

describe('dailyEventSchedule', () => {
  it('getNextDailyOccurrence returns later today when time not passed', () => {
    const now = new Date('2026-03-26T05:30:00-03:00')
    const next = getNextDailyOccurrence(6, 0, now)
    expect(next.toISOString()).toBe('2026-03-26T09:00:00.000Z')
  })

  it('getNextDailyOccurrence rolls to tomorrow after event time', () => {
    const now = new Date('2026-03-26T06:00:00-03:00')
    const next = getNextDailyOccurrence(6, 0, now)
    expect(next.toISOString()).toBe('2026-03-27T09:00:00.000Z')
  })

  it('getMsUntilNextDailyEvent is ~24h right at start', () => {
    const now = new Date('2026-03-26T06:00:00-03:00')
    const ms = getMsUntilNextDailyEvent(6, 0, now)
    expect(ms).toBe(24 * 60 * 60 * 1000)
  })

  it('getNextDailyOccurrence handles month rollover in another zone (CW3 Moscow 22:00)', () => {
    // 31/10 14:41 Brasília = 31/10 20:41 Moscou; CW3 é hoje 22:00 Moscou.
    const now = new Date('2026-10-31T14:41:33-03:00')
    const next = getNextDailyOccurrence(22, 0, now, 'Europe/Moscow')
    expect(next.toISOString()).toBe('2026-10-31T19:00:00.000Z')
  })

  it('getNextDailyOccurrence rolls into next month (PB 02:00 Brasília)', () => {
    const now = new Date('2026-10-31T14:41:33-03:00')
    const next = getNextDailyOccurrence(2, 0, now, 'America/Sao_Paulo')
    expect(next.toISOString()).toBe('2026-11-01T05:00:00.000Z')
  })

  it('getNextDailyOccurrence rolls into next year', () => {
    const now = new Date('2026-12-31T23:30:00-03:00')
    const next = getNextDailyOccurrence(6, 0, now, 'Europe/Moscow')
    expect(next.toISOString()).toBe('2027-01-01T03:00:00.000Z')
  })

  it('formatHmsCountdown pads segments', () => {
    expect(formatHmsCountdown((2 * 3600 + 5 * 60 + 7) * 1000)).toBe('02:05:07')
  })

  it('formatHmsCountdown shows days when over 24h', () => {
    expect(formatHmsCountdown((2 * 86400 + 3 * 3600) * 1000)).toBe('2d 03:00:00')
  })

  const majorAnchor = new Date('2026-09-27T19:00:00-03:00')

  it('interval: before anchor counts to anchor', () => {
    const now = new Date('2026-09-26T20:00:00-03:00')
    const next = getNextIntervalOccurrence(majorAnchor, 4, now)
    expect(next.getTime()).toBe(majorAnchor.getTime())
  })

  it('interval: at anchor start, next is +4 days', () => {
    const now = new Date(majorAnchor)
    const next = getNextIntervalOccurrence(majorAnchor, 4, now)
    expect(next.toISOString()).toBe('2026-10-01T22:00:00.000Z')
  })

  it('interval: ms until next after anchor', () => {
    const now = new Date('2026-09-28T12:00:00-03:00')
    const ms = getMsUntilNextIntervalEvent(majorAnchor, 4, now)
    const next = new Date(now.getTime() + ms)
    expect(next.toISOString()).toBe('2026-10-01T22:00:00.000Z')
  })

  describe('eventsForGuild', () => {
    const slots = [
      { time: '02:00', qg: [], outpost: [{ guild: 'Blacklist', bosses: ['Blood King Twizer'] }] },
      { time: '14:00', qg: [{ guild: 'Blacklist', bosses: ['Ratmoth'] }], outpost: [] },
      { time: '18:00', qg: [{ guild: 'Quit', bosses: ['Splinter'] }], outpost: [{ guild: 'Quit', bosses: ['Rex'] }] },
      { time: '22:00', qg: [{ guild: 'Blacklist', bosses: ['Warbeast'] }], outpost: [] },
    ]
    const byId = (events: ReturnType<typeof eventsForGuild>, id: string) => events.find((e) => e.id === id)

    it('PB HQ pula o horário sem boss nosso e vai para o próximo PB nosso', () => {
      const events = eventsForGuild(HEADER_SCHEDULED_EVENTS, slots, 'Blacklist')
      // 15h: o PB HQ das 18h é da Quit, então o nosso é o das 22h.
      const next = getScheduledOccurrenceStart(byId(events, 'pb-hq')!, new Date('2026-10-31T15:00:00-03:00'))
      expect(next.toISOString()).toBe('2026-11-01T01:00:00.000Z')
      expect(byId(events, 'pb-colonia')).toMatchObject({ hours: [2] })
    })

    it('mantém Middle e CW, e some com o PB sem nenhum horário nosso', () => {
      const events = eventsForGuild(HEADER_SCHEDULED_EVENTS, [slots[1], slots[3]], 'Blacklist')
      expect(byId(events, 'pb-colonia')).toBeUndefined()
      expect(byId(events, 'pb-middle')).toEqual(byId(HEADER_SCHEDULED_EVENTS, 'pb-middle'))
      expect(byId(events, 'cw1')).toBeDefined()
    })

    it('sem rotação carregada, todos os horários continuam valendo', () => {
      expect(eventsForGuild(HEADER_SCHEDULED_EVENTS, undefined, 'Blacklist')).toBe(HEADER_SCHEDULED_EVENTS)
    })
  })

  describe('weekly PBs', () => {
    const byId = (id: string) => HEADER_SCHEDULED_EVENTS.find((e) => e.id === id)
    const elan = byId('pb-elan')!
    const major = byId('pb-major')!

    it('PB Elan: Mon/Tue/Thu/Sat 14:00 Brasília', () => {
      // terça 10/11/2026 11:30 → hoje 14:00
      expect(getScheduledOccurrenceStart(elan, new Date('2026-11-10T11:30:00-03:00')).toISOString()).toBe('2026-11-10T17:00:00.000Z')
      // terça 15:00 → quinta 12/11 14:00 (pula quarta)
      expect(getScheduledOccurrenceStart(elan, new Date('2026-11-10T15:00:00-03:00')).toISOString()).toBe('2026-11-12T17:00:00.000Z')
    })

    it('PB Major: Tue/Sat 14:00 Brasília', () => {
      // terça 15:00 → sábado 14/11 14:00
      expect(getScheduledOccurrenceStart(major, new Date('2026-11-10T15:00:00-03:00')).toISOString()).toBe('2026-11-14T17:00:00.000Z')
    })
  })
})
