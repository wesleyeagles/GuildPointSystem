import { SCHEDULE_TIME_ZONE } from '@/Shared/utils/scheduleTimeZone'

const dayKey = (d: Date) => d.toLocaleDateString('pt-BR', { timeZone: SCHEDULE_TIME_ZONE })

/** Hoje: só a hora; outro dia: dia/mês e hora (Brasília, como o resto do site). */
export function formatPointsTime(iso: string, now = new Date()): string {
  const d = new Date(iso)
  const time = d.toLocaleTimeString('pt-BR', { timeZone: SCHEDULE_TIME_ZONE, hour: '2-digit', minute: '2-digit', hour12: false })
  if (dayKey(d) === dayKey(now)) return time
  const day = d.toLocaleDateString('pt-BR', { timeZone: SCHEDULE_TIME_ZONE, day: '2-digit', month: '2-digit' })
  return `${day} ${time}`
}

/**
 * Reparte as linhas em páginas [início, fim) que cabem em `available` px (com `gap` entre elas).
 * Toda página tem ao menos uma linha. Sem medidas (ou sem altura), tudo numa página só.
 */
export function splitPages(heights: number[], available: number, gap: number, count: number): [number, number][] {
  if (count === 0) return []
  if (available <= 0 || heights.length !== count) return [[0, count]]
  const pages: [number, number][] = []
  let start = 0
  let used = 0
  heights.forEach((height, i) => {
    const needed = i === start ? height : used + gap + height
    if (i > start && needed > available) {
      pages.push([start, i])
      start = i
      used = height
    } else {
      used = needed
    }
  })
  pages.push([start, count])
  return pages
}
