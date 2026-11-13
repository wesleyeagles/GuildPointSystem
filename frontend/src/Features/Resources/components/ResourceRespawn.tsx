import { useState } from 'react'
import { TIER_LABEL, useResourceStats, type ResourceTierStats } from '@/Domain/Resource/hooks/useResources'
import { Panel } from '@/Shared/ui/components/Panel/Panel'
import { useServerNow } from '@/Shared/utils/useServerNow'

const DAYS = [1, 7, 30]

function when(iso: string) {
  return new Date(iso).toLocaleString('pt-BR', {
    timeZone: 'America/Sao_Paulo', day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit',
  })
}

function span(seconds: number) {
  const s = Math.max(0, Math.round(seconds))
  if (s < 60) return `${s} s`
  const m = Math.round(s / 60)
  return m < 60 ? `${m} min` : `${Math.floor(m / 60)} h ${String(m % 60).padStart(2, '0')} min`
}

const number = (n: number) => n.toLocaleString('pt-BR', { maximumFractionDigits: n < 10 ? 2 : 0 })

/** Chance de ao menos 1 em 1 h com spawn aleatório (Poisson) na média do servidor. */
const chancePerHour = (perHour: number) => Math.round((1 - Math.exp(-perHour)) * 100)

/**
 * Previsão de respawn. O servidor sorteia horário e chance (média por hora num mapa: 500 grau 1, 50 grau 2 e
 * 0,33 grau 3; pode vir metade ou o dobro; sem ninguém matando, no máximo 80 no mapa). Não existe horário fixo
 * por ponto: aqui fica o que a DLL já viu (por hora, minuto da hora, intervalo) para achar algum padrão.
 */
export function ResourceRespawn({ map }: { map: number }) {
  const [days, setDays] = useState(7)
  const { data, isLoading } = useResourceStats(map, days)
  const now = useServerNow(30_000)
  return (
    <Panel title="Previsão de respawn" code={data ? `${data.observedHours} h observadas` : undefined}>
      <div className="resources__filters">
        <p className="resources__muted resources__respawn-note">
          O servidor sorteia a hora e a chance: em média, por hora neste mapa, 500 de grau 1, 50 de grau 2 e 0,33 de
          grau 3 (pode vir metade ou o dobro). Sem ninguém matando, o mapa enche em 80. Horas observadas = horas em que
          alguém com a DLL estava aqui achando recursos. O horário é de quando a DLL viu, não de quando nasceu.
        </p>
        <select className="resources__period" value={days} onChange={(e) => setDays(Number(e.target.value))} aria-label="Período da previsão">
          {DAYS.map((d) => <option key={d} value={d}>{d === 1 ? '24 horas' : `${d} dias`}</option>)}
        </select>
      </div>
      {isLoading || !data ? <p className="resources__muted">Carregando…</p> : (
        <div className="resources__respawn">
          {[...data.tiers].reverse().map((t) => <TierCard key={t.tier} stats={t} now={now} />)}
        </div>
      )}
    </Panel>
  )
}

function TierCard({ stats, now }: { stats: ResourceTierStats; now: number }) {
  const next = stats.nextLikelyAt ? Date.parse(stats.nextLikelyAt) : null
  const maxMinute = Math.max(1, ...stats.byMinute)
  const maxHour = Math.max(1, ...stats.byHour)
  return (
    <section className={`resources__respawn-card resources__respawn-card--t${stats.tier}`}>
      <h3><span className={`resources__swatch resources__swatch--t${stats.tier}`} /> {TIER_LABEL[stats.tier]}</h3>
      <dl className="resources__detail">
        <dt>Último achado</dt>
        <dd>{stats.lastSeenAt ? `${when(stats.lastSeenAt)} (há ${span((now - Date.parse(stats.lastSeenAt)) / 1000)})` : 'Nunca'}</dd>
        <dt>Achados</dt>
        <dd>
          {stats.total} · {number(stats.perObservedHour)}/h observada
          {stats.expectedPerHour != null && ` (servidor: ${number(stats.expectedPerHour)}/h)`}
        </dd>
        <dt>Intervalo típico</dt>
        <dd>{stats.medianGapSeconds != null ? span(stats.medianGapSeconds) : '—'}</dd>
        <dt>Próximo provável</dt>
        <dd>
          {next == null || !stats.nextLikelyAt ? '—' : next > now ? `${when(stats.nextLikelyAt)} (em ${span((next - now) / 1000)})` : 'A qualquer momento'}
        </dd>
        {stats.expectedPerHour != null && stats.expectedPerHour < 5 && (
          <><dt>Chance na próxima 1 h</dt><dd>~{chancePerHour(stats.expectedPerHour)}% (pela média do servidor)</dd></>
        )}
      </dl>
      <h4>Minuto da hora em que aparece</h4>
      <div className="resources__bars" role="img" aria-label={`Achados de ${TIER_LABEL[stats.tier]} por minuto da hora`}>
        {stats.byMinute.map((n, i) => (
          <span key={i} title={`:${String(i * 5).padStart(2, '0')}–:${String(i * 5 + 4).padStart(2, '0')} · ${n}`}
            style={{ height: `${(n / maxMinute) * 100}%` }} />
        ))}
      </div>
      <div className="resources__bars-axis"><span>:00</span><span>:30</span><span>:55</span></div>
      <h4>Hora do dia (Brasília)</h4>
      <div className="resources__bars" role="img" aria-label={`Achados de ${TIER_LABEL[stats.tier]} por hora do dia`}>
        {stats.byHour.map((n, i) => (
          <span key={i} title={`${String(i).padStart(2, '0')}h · ${n}`} style={{ height: `${(n / maxHour) * 100}%` }} />
        ))}
      </div>
      <div className="resources__bars-axis"><span>0h</span><span>12h</span><span>23h</span></div>
    </section>
  )
}
