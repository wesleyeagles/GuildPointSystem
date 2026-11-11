import { useState, type FormEvent } from 'react'
import { useSearchParams } from 'react-router-dom'
import { useLocationHistory, type LocationQuery } from '@/Domain/LocationHistory/hooks/useLocationHistory'
import { getZonedParts, SCHEDULE_TIME_ZONE, zonedLocalToUtc } from '@/Shared/utils/scheduleTimeZone'
import { Button } from '@/Shared/ui/components/Button/Button'
import { Panel } from '@/Shared/ui/components/Panel/Panel'
import './LocationHistory.styles.scss'

const BRT = SCHEDULE_TIME_ZONE
const pad = (n: number) => String(n).padStart(2, '0')
const time = (iso: string) => new Date(iso).toLocaleTimeString('pt-BR', { timeZone: BRT })

function idle(seconds: number | null) {
  if (seconds === null) return '—'
  if (seconds < 60) return `${seconds} s`
  const m = Math.floor(seconds / 60)
  return m >= 60 ? `${Math.floor(m / 60)} h ${pad(m % 60)} min` : `${m} min ${pad(seconds % 60)} s`
}

function build(date: string, hour: string, nickname: string, minutes: number): LocationQuery | null {
  const [y, mo, d] = date.split('-').map(Number)
  const [h, mi] = hour.split(':').map(Number)
  if (!y || !mo || !d || Number.isNaN(h) || Number.isNaN(mi)) return null
  return { at: zonedLocalToUtc(y, mo, d, h, mi, BRT).toISOString(), nickname, minutes }
}

/**
 * Onde cada membro estava num horário, pelo histórico de 3 dias dos envios da DLL. Página sem link no site
 * (só pela URL /onde-estava), de Administrador+. Sem nick: o último local de cada um até o horário; com nick:
 * todos os envios dele em volta do horário.
 */
export function LocationHistoryPage() {
  const [params, setParams] = useSearchParams()
  const now = getZonedParts(new Date(), BRT)
  const [date, setDate] = useState(params.get('data') ?? `${now.year}-${pad(now.month)}-${pad(now.day)}`)
  const [hour, setHour] = useState(params.get('hora') ?? `${pad(now.hour)}:${pad(now.minute)}`)
  const [nickname, setNickname] = useState(params.get('nick') ?? '')
  const [minutes, setMinutes] = useState(Number(params.get('min') ?? 10))
  const [query, setQuery] = useState<LocationQuery | null>(() =>
    params.get('hora') ? build(date, hour, nickname, minutes) : null)
  const { data, isFetching, error } = useLocationHistory(query)

  function submit(e: FormEvent) {
    e.preventDefault()
    const next = build(date, hour, nickname, minutes)
    if (!next) return
    setQuery(next)
    const search: Record<string, string> = { data: date, hora: hour, min: String(minutes) }
    if (nickname.trim()) search.nick = nickname.trim()
    setParams(search, { replace: true })
  }

  // Com nick: destaca o último envio até o horário (onde ele estava naquela hora).
  const marked = new Set<number>()
  if (query?.nickname.trim() && data) {
    const target = Date.parse(query.at)
    const lastByMember = new Map<number, number>()
    data.forEach((r, i) => { if (Date.parse(r.at) <= target) lastByMember.set(r.memberId, i) })
    lastByMember.forEach((i) => marked.add(i))
  }

  return (
    <div className="location-history">
      <Panel title="Onde estava" code="ADM">
        <form className="location-history__form" onSubmit={submit}>
          <label>Dia<input type="date" value={date} onChange={(e) => setDate(e.target.value)} required /></label>
          <label>Hora (Brasília)<input type="time" value={hour} onChange={(e) => setHour(e.target.value)} required /></label>
          <label>Membro<input value={nickname} onChange={(e) => setNickname(e.target.value)} placeholder="Todos" /></label>
          <label>
            Janela
            <select value={minutes} onChange={(e) => setMinutes(Number(e.target.value))} disabled={!nickname.trim()}>
              {[5, 10, 30, 60].map((m) => <option key={m} value={m}>± {m} min</option>)}
            </select>
          </label>
          <Button type="submit">Buscar</Button>
        </form>
        <p className="location-history__muted">
          {nickname.trim()
            ? 'Todos os envios da DLL desse membro em volta do horário. Em destaque: onde ele estava no horário.'
            : 'Último local de cada membro até o horário (envio de no máximo 3 min antes). Guardado por 3 dias.'}
        </p>
        {error ? <p className="location-history__warn">{(error as Error).message}</p>
          : isFetching ? <p className="location-history__muted">Carregando…</p>
          : data && data.length === 0 ? <p className="location-history__muted">Nenhum envio da DLL nesse horário.</p>
          : data && (
            <table className="location-history__table">
              <thead>
                <tr>
                  <th>Horário</th><th>Membro</th><th>Personagem</th><th>Mapa</th><th>Região</th>
                  <th>Coord.</th><th>Parado</th><th>DLL</th>
                </tr>
              </thead>
              <tbody>
                {data.map((r, i) => (
                  <tr key={`${r.memberId}-${r.at}-${i}`} className={marked.has(i) ? 'location-history__mark' : undefined}>
                    <td>{time(r.at)}</td>
                    <td>{r.nickname}</td>
                    <td>{r.character ?? '—'}</td>
                    <td>{r.map ?? '—'}</td>
                    <td>{r.region ?? '—'}</td>
                    <td>{r.x !== null && r.y !== null ? `${r.x}, ${r.y}` : '—'}</td>
                    <td>{idle(r.idleSeconds)}</td>
                    <td>{r.dllVersion ?? '—'}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
      </Panel>
    </div>
  )
}
