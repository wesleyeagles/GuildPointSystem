import { useState } from 'react'
import './Calendar.styles.scss'

const WEEKDAYS = ['D', 'S', 'T', 'Q', 'Q', 'S', 'S']
const MONTHS = [
  'Janeiro', 'Fevereiro', 'Março', 'Abril', 'Maio', 'Junho',
  'Julho', 'Agosto', 'Setembro', 'Outubro', 'Novembro', 'Dezembro',
]

const pad = (n: number) => String(n).padStart(2, '0')
const toIso = (y: number, m: number, d: number) => `${y}-${pad(m + 1)}-${pad(d)}`

interface CalendarProps {
  /** Selected day as yyyy-mm-dd ('' = none). */
  value: string
  onChange: (day: string) => void
  /** First selectable day (yyyy-mm-dd). */
  min?: string
  /** "Today" highlight (yyyy-mm-dd); defaults to the browser's local date. */
  today?: string
}

/** Inline month calendar in the site's style; works with yyyy-mm-dd strings only (no time zones). */
export function Calendar({ value, onChange, min, today }: CalendarProps) {
  const now = new Date()
  const todayIso = today ?? toIso(now.getFullYear(), now.getMonth(), now.getDate())
  const start = (value || todayIso).split('-').map(Number)
  const [view, setView] = useState({ year: start[0], month: start[1] - 1 })

  const firstWeekday = new Date(view.year, view.month, 1).getDay()
  const daysInMonth = new Date(view.year, view.month + 1, 0).getDate()
  const cells: (number | null)[] = [
    ...Array.from({ length: firstWeekday }, () => null),
    ...Array.from({ length: daysInMonth }, (_, i) => i + 1),
  ]
  while (cells.length % 7) cells.push(null)

  const shift = (delta: number) =>
    setView(({ year, month }) => {
      const m = month + delta
      return { year: year + Math.floor(m / 12), month: ((m % 12) + 12) % 12 }
    })
  // Nothing selectable before `min`: don't page back past its month.
  const canGoBack = !min || toIso(view.year, view.month, 1) > min

  return (
    <div className="calendar">
      <div className="calendar__head">
        <button type="button" className="calendar__nav" onClick={() => shift(-1)} disabled={!canGoBack} aria-label="Mês anterior">
          ‹
        </button>
        <span className="calendar__month">
          {MONTHS[view.month]} <span className="calendar__year">{view.year}</span>
        </span>
        <button type="button" className="calendar__nav" onClick={() => shift(1)} aria-label="Próximo mês">
          ›
        </button>
      </div>
      <div className="calendar__grid" role="grid">
        {WEEKDAYS.map((w, i) => (
          <span key={i} className="calendar__weekday">
            {w}
          </span>
        ))}
        {cells.map((day, i) => {
          if (day === null) return <span key={i} />
          const iso = toIso(view.year, view.month, day)
          const disabled = !!min && iso < min
          const classes = [
            'calendar__day',
            iso === value && 'calendar__day--selected',
            iso === todayIso && 'calendar__day--today',
          ]
            .filter(Boolean)
            .join(' ')
          return (
            <button
              key={i}
              type="button"
              className={classes}
              disabled={disabled}
              aria-pressed={iso === value}
              onClick={() => onChange(iso)}
            >
              {day}
            </button>
          )
        })}
      </div>
    </div>
  )
}
