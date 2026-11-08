import { useState } from 'react'
import { useCwAttendance, useEndCw, type Cw, type CwAttendance } from '@/Domain/CwAttendance/hooks/useCwAttendance'
import { getZonedParts, SCHEDULE_TIME_ZONE, zonedLocalToUtc } from '@/Shared/utils/scheduleTimeZone'
import { Button } from '@/Shared/ui/components/Button/Button'
import { Panel } from '@/Shared/ui/components/Panel/Panel'
import { useAppToast } from '@/Shared/ui/components/AppToast/AppToast'
import './CwAttendance.styles.scss'

const DAYS = [1, 3, 7, 14, 30] as const
const BRT = SCHEDULE_TIME_ZONE

const hour = (iso: string) => new Date(iso).toLocaleTimeString('pt-BR', { timeZone: BRT, hour: '2-digit', minute: '2-digit' })
const day = (iso: string) => new Date(iso).toLocaleDateString('pt-BR', { timeZone: BRT, day: '2-digit', month: '2-digit' })

function minutes(seconds: number) {
  const m = Math.round(seconds / 60)
  return m >= 60 ? `${Math.floor(m / 60)} h ${String(m % 60).padStart(2, '0')} min` : `${m} min`
}

/**
 * Presença em CW: quanto tempo cada membro ficou em Crag Mind Field durante cada CW, pela DLL.
 * A CW acaba quando a DLL de alguém vê "Due to the result of Chip War" no chat; 2 min depois, quem ficou
 * 60% da CW em Crag sem AFK recebe "CW" (ou "CW Major" em dia de PB Major). 10 min antes de cada CW, quem
 * está em Crag recebe 2 pontos. Todos os membros veem; só Administrador+ marca o fim de uma CW sem aviso.
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
      {isLoading || !data ? <p className="cw-attendance__muted">Carregando…</p> : data.cws.length === 0 ? (
        <p className="cw-attendance__muted">Nenhuma CW com membros em Crag Mind Field nesse período.</p>
      ) : (
        data.cws.map((cw) => <CwPanel key={`${cw.date}-${cw.slot}`} cw={cw} data={data} days={days} />)
      )}
    </div>
  )
}

/** O fim só pode ser marcado 5 min depois do início (como no servidor). */
const canEnd = (cw: Cw) => Date.now() >= Date.parse(cw.startsAt) + 5 * 60_000

function status(cw: Cw) {
  if (cw.endedAt) {
    const length = minutes((Date.parse(cw.endedAt) - Date.parse(cw.startsAt)) / 1000)
    const by = cw.endSource === 'ADMIN' ? 'marcado por Admin' : 'mensagem do jogo'
    return `${hour(cw.startsAt)}–${hour(cw.endedAt)} (${length}, ${by})`
  }
  if (cw.closed) return `${hour(cw.startsAt)} · sem aviso de fim (aguardando um Admin)`
  if (Date.now() < Date.parse(cw.startsAt)) return `começa às ${hour(cw.startsAt)}`
  return `${hour(cw.startsAt)} · em andamento (nenhuma DLL avisou o fim)`
}

function CwPanel({ cw, data, days }: { cw: Cw; data: CwAttendance; days: number }) {
  const awarded = cw.members.filter((m) => m.awarded).length
  const prize = cw.objectivePoints != null ? `${cw.objectiveName} (${cw.objectivePoints} pts)` : cw.objectiveName
  return (
    <Panel
      title={`${cw.label} · ${day(cw.startsAt)} · ${status(cw)}`}
      code={`${cw.members.length} em Crag${cw.awardedAt ? ` · ${awarded} pontuado${awarded === 1 ? '' : 's'}` : ''}`}
    >
      <p className="cw-attendance__line">
        Vale <strong>{prize}</strong>{cw.major && ' (dia de PB Major)'}.{' '}
        {cw.objectiveMissing && (
          <span className="cw-attendance__warn">
            Não existe objetivo "{cw.objectiveName}" na aba Objetivos: ninguém recebe até ele ser criado.
          </span>
        )}
        {cw.awardedAt && ` Pontuada às ${hour(cw.awardedAt)}.`}
      </p>
      {cw.preBonus.length > 0 && (
        <p className="cw-attendance__line cw-attendance__muted">
          Bônus de 10 min antes ({data.preBonusPoints} pts): {cw.preBonus.join(', ')}
        </p>
      )}
      {/* Admin marca o fim também com a CW "em andamento": se nenhuma DLL avisou, ela seguiria até o fim da janela. */}
      {data.canManage && !cw.awardedAt && canEnd(cw) && <EndForm cw={cw} days={days} />}
      {cw.members.length > 0 && (
        <table className="cw-attendance__table">
          <thead>
            <tr><th>Membro</th><th>Em Crag (sem AFK)</th><th>% da CW</th><th>AFK</th><th>Primeira vez</th><th>Última vez</th></tr>
          </thead>
          <tbody>
            {cw.members.map((m) => (
              <tr key={m.memberId}>
                <td>
                  {m.nickname}
                  {m.character && m.character !== m.nickname && <span className="cw-attendance__muted"> ({m.character})</span>}
                </td>
                <td>{minutes(m.activeSeconds)}</td>
                <td>
                  <span className="cw-attendance__bar" aria-hidden>
                    <span
                      className={m.percent >= data.thresholdPercent ? 'cw-attendance__bar--ok' : undefined}
                      style={{ width: `${m.percent}%` }}
                    />
                  </span>
                  {m.percent}%
                  {m.awarded && <span className="cw-attendance__ok"> ✓ pontuado</span>}
                </td>
                <td className={m.afkSeconds > 0 ? 'cw-attendance__warn' : 'cw-attendance__muted'}>{minutes(m.afkSeconds)}</td>
                <td>{hour(m.firstSeenAt)}</td>
                <td>{hour(m.lastSeenAt)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </Panel>
  )
}

/** Marca (ou corrige) o fim da CW, em horário de Brasília, antes de pontuar. */
function EndForm({ cw, days }: { cw: Cw; days: number }) {
  const end = useEndCw(days)
  const { showToast } = useAppToast()
  const [time, setTime] = useState(cw.endedAt ? hour(cw.endedAt) : '')
  const submit = () => {
    const [h, m] = time.split(':').map(Number)
    const start = getZonedParts(new Date(cw.startsAt), BRT)
    const endedAt = zonedLocalToUtc(start.year, start.month, start.day, h, m, BRT).toISOString()
    end.mutate({ date: cw.date, slot: cw.slot, endedAt }, {
      onSuccess: () => showToast(`${cw.label}: fim às ${time}. Os pontos entram em até 2 min.`, 'success'),
      onError: (e) => showToast(e instanceof Error ? e.message : 'Não foi possível salvar.', 'error'),
    })
  }
  return (
    <form className="cw-attendance__end" onSubmit={(e) => { e.preventDefault(); submit() }}>
      <label>
        Fim da CW (Brasília){' '}
        <input type="time" value={time} onChange={(e) => setTime(e.target.value)} required />
      </label>
      <Button type="submit" disabled={!time || end.isPending}>{cw.endedAt ? 'Corrigir fim' : 'Marcar fim e pontuar'}</Button>
    </form>
  )
}
