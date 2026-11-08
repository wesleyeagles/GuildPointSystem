import { useState } from 'react'
import { useCwAttendance, type Cw } from '@/Domain/CwAttendance/hooks/useCwAttendance'
import { Panel } from '@/Shared/ui/components/Panel/Panel'
import './CwAttendance.styles.scss'

const DAYS = [1, 3, 7, 14, 30] as const
const BRT = 'America/Sao_Paulo'

const hour = (iso: string) => new Date(iso).toLocaleTimeString('pt-BR', { timeZone: BRT, hour: '2-digit', minute: '2-digit' })
const day = (iso: string) => new Date(iso).toLocaleDateString('pt-BR', { timeZone: BRT, day: '2-digit', month: '2-digit' })

function minutes(seconds: number) {
  const m = Math.round(seconds / 60)
  return m >= 60 ? `${Math.floor(m / 60)} h ${String(m % 60).padStart(2, '0')} min` : `${m} min`
}

/**
 * Presença em CW: quanto tempo cada membro ficou em Crag Mind Field durante cada CW, pelo envio de status da DLL
 * (a cada ~1 min). O tempo AFK (parado na mesma coordenada há mais de 3 min, sem EXP) não conta: aparece à parte.
 * Só Administrador+ (menu de admin).
 */
export function CwAttendancePage() {
  const [days, setDays] = useState<number>(7)
  const { data, isLoading } = useCwAttendance(days)
  return (
    <div className="cw-attendance">
      <div className="cw-attendance__filters">
        <select value={days} onChange={(e) => setDays(Number(e.target.value))} aria-label="Período">
          {DAYS.map((d) => <option key={d} value={d}>{d === 1 ? 'Hoje' : `Últimos ${d} dias`}</option>)}
        </select>
      </div>
      <p className="cw-attendance__muted">
        Tempo em Crag Mind Field durante cada CW (CW1 00h–02h, CW2 08h–11h, CW3 16h–18h, Brasília), pela DLL de cada
        membro. "Em Crag" não conta o tempo AFK (parado no mesmo lugar há mais de 3 min sem ganhar EXP), que aparece à
        parte. Só aparece quem estava com a DLL aberta.
      </p>
      {isLoading || !data ? <p className="cw-attendance__muted">Carregando…</p> : data.cws.length === 0 ? (
        <p className="cw-attendance__muted">Ninguém foi visto em Crag Mind Field numa CW nesse período.</p>
      ) : (
        data.cws.map((cw) => <CwPanel key={`${cw.date}-${cw.slot}`} cw={cw} />)
      )}
    </div>
  )
}

function CwPanel({ cw }: { cw: Cw }) {
  const length = (Date.parse(cw.endsAt) - Date.parse(cw.startsAt)) / 1000
  return (
    <Panel
      title={`${cw.label} · ${day(cw.startsAt)} · ${hour(cw.startsAt)}–${hour(cw.endsAt)}`}
      code={`${cw.members.length} membro${cw.members.length === 1 ? '' : 's'}`}
    >
      <table className="cw-attendance__table">
        <thead>
          <tr><th>Membro</th><th>Em Crag (sem AFK)</th><th>AFK</th><th>Primeira vez</th><th>Última vez</th></tr>
        </thead>
        <tbody>
          {cw.members.map((m) => (
            <tr key={m.memberId}>
              <td>
                {m.nickname}
                {m.character && m.character !== m.nickname && <span className="cw-attendance__muted"> ({m.character})</span>}
              </td>
              <td>
                <span className="cw-attendance__bar" aria-hidden>
                  <span style={{ width: `${Math.min(100, (m.activeSeconds * 100) / length)}%` }} />
                </span>
                {minutes(m.activeSeconds)}
              </td>
              <td className={m.afkSeconds > 0 ? 'cw-attendance__afk' : 'cw-attendance__muted'}>{minutes(m.afkSeconds)}</td>
              <td>{hour(m.firstSeenAt)}</td>
              <td>{hour(m.lastSeenAt)}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </Panel>
  )
}
