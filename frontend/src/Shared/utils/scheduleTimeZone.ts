/** Fuso dos eventos de servidor (alinhado ao backend). */
export const SCHEDULE_TIME_ZONE = 'America/Sao_Paulo'

/** Fuso do servidor Cerberus (Moscou, UTC+3): 00:00 lá = 18:00 em Brasília. */
export const SERVER_TIME_ZONE = 'Europe/Moscow'

interface ZonedParts {
  year: number
  month: number
  day: number
  hour: number
  minute: number
  second: number
}

export function getZonedParts(date: Date, timeZone: string): ZonedParts {
  const fmt = new Intl.DateTimeFormat('en-US', {
    timeZone,
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hourCycle: 'h23',
  })
  const parts = fmt.formatToParts(date)
  const pick = (type: Intl.DateTimeFormatPartTypes) =>
    Number(parts.find((p) => p.type === type)?.value ?? '0')
  return {
    year: pick('year'),
    month: pick('month'),
    day: pick('day'),
    hour: pick('hour'),
    minute: pick('minute'),
    second: pick('second'),
  }
}

/** Instante UTC para um horário civil em `timeZone`. */
export function zonedLocalToUtc(
  year: number,
  month: number,
  day: number,
  hour: number,
  minute: number,
  timeZone: string,
): Date {
  // Compara datas completas (não só o dia do mês) para não quebrar na virada de mês/ano.
  const target = Date.UTC(year, month - 1, day, hour, minute, 0)
  let guess = new Date(target)
  for (let i = 0; i < 4; i++) {
    const z = getZonedParts(guess, timeZone)
    const diffMs = target - Date.UTC(z.year, z.month - 1, z.day, z.hour, z.minute, 0)
    if (diffMs === 0) {
      return guess
    }
    guess = new Date(guess.getTime() + diffMs)
  }
  return guess
}
