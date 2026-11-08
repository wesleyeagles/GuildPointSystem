import { mapPoint, TIER_LABEL, type ResourceMapScale } from '@/Domain/Resource/hooks/useResources'
import type { SpawnSpot } from '@/Domain/Resource/spawnSpots'

const tierKey = (tier: number | null) => (tier && tier >= 1 && tier <= 4 ? String(tier) : 'u')
const LIST = 40

function when(ms: number) {
  return new Date(ms).toLocaleString('pt-BR', {
    timeZone: 'America/Sao_Paulo', day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit',
  })
}

function span(ms: number) {
  const m = Math.max(0, Math.round(ms / 60_000))
  if (m < 1) return 'menos de 1 min'
  return m < 60 ? `${m} min` : `${Math.floor(m / 60)} h ${String(m % 60).padStart(2, '0')} min`
}

/** Próximo provável em texto: "em 12 min" ou "a qualquer momento"; null sem intervalo. */
function nextText(spot: SpawnSpot, now: number) {
  if (spot.nextAt == null) return null
  return spot.nextAt > now ? `${when(spot.nextAt)} (em ${span(spot.nextAt - now)})` : 'A qualquer momento'
}

/** Raio do círculo no mapa: cresce com os nascimentos, sem cobrir o mapa. */
const spotRadius = (count: number) => Math.min(14, 3 + Math.sqrt(count) * 1.6)

/** Círculos dos pontos de respawn (os mais ativos por cima) com o número de nascimentos. */
export function SpotLayer({ view, spots, marked, onHover, onSelect }: {
  view: ResourceMapScale
  spots: SpawnSpot[]
  marked: SpawnSpot | null
  onHover: (spot: SpawnSpot | null) => void
  onSelect: (spot: SpawnSpot) => void
}) {
  const ordered = [...spots].sort((a, b) => a.count - b.count)
  return (
    <>
      {ordered.map((s) => {
        const p = mapPoint(view, s.x, s.z)
        if (!p) return null
        const r = spotRadius(s.count)
        return (
          <g
            key={s.key}
            className={`resources__spot resources__spot--t${tierKey(s.tier)}${marked?.key === s.key ? ' resources__spot--hover' : ''}`}
            onMouseEnter={() => onHover(s)}
            onMouseLeave={() => onHover(null)}
            onClick={() => onSelect(s)}
          >
            <circle cx={p.px} cy={p.py} r={r} />
            {s.count > 1 && r >= 6 && <text x={p.px} y={p.py} dy="0.35em">{s.count}</text>}
            <title>{`${s.tier ? TIER_LABEL[s.tier] : 'Grau desconhecido'} · ${s.count} nascimento(s) · último ${when(s.lastAt)}`}</title>
          </g>
        )
      })}
      {marked && (() => {
        const p = mapPoint(view, marked.x, marked.z)
        if (!p) return null
        const r = spotRadius(marked.count)
        return (
          <g className="resources__marker" pointerEvents="none">
            <circle cx={p.px} cy={p.py} r={r + 5} />
            <circle cx={p.px} cy={p.py} r={r + 9} className="resources__marker-wave" />
          </g>
        )
      })()}
    </>
  )
}

/** Lista dos pontos (mais nascimentos primeiro) ou detalhes do ponto escolhido. */
export function SpotSide({ spots, selected, now, onHover, onSelect }: {
  spots: SpawnSpot[]
  selected: SpawnSpot | null
  now: number
  onHover: (spot: SpawnSpot | null) => void
  onSelect: (spot: SpawnSpot | null) => void
}) {
  if (selected) {
    const recent = [...selected.spawns].reverse().slice(0, 10)
    const next = nextText(selected, now)
    return (
      <>
        <dl className="resources__detail">
          <dt>Grau</dt><dd>{selected.tier ? TIER_LABEL[selected.tier] : 'Desconhecido'}</dd>
          <dt>Nascimentos</dt><dd>{selected.count}</dd>
          <dt>Último</dt><dd>{when(selected.lastAt)} (há {span(now - selected.lastAt)}){selected.alive ? ' · vivo' : ''}</dd>
          <dt>Intervalo típico</dt><dd>{selected.medianGapMs != null ? span(selected.medianGapMs) : '— (só 1 achado)'}</dd>
          <dt>Próximo provável</dt><dd>{next ?? '—'}</dd>
          <dt>Posição</dt><dd>{selected.x}, {selected.z}</dd>
        </dl>
        <h4 className="resources__subtitle resources__subtitle--small">Últimos nascimentos aqui</h4>
        <ul className="resources__recent resources__recent--plain">
          {recent.map((s) => (
            <li key={s.id}>
              <span className="resources__recent-name">{s.name}</span>
              <time>{when(Date.parse(s.foundAt))}</time>
            </li>
          ))}
        </ul>
        <button type="button" className="resources__back" onClick={() => onSelect(null)}>← Voltar à lista</button>
      </>
    )
  }
  if (spots.length === 0) return <p className="resources__muted">Nenhum ponto de respawn nesse filtro.</p>
  const ranked = [...spots].sort((a, b) => b.count - a.count || b.lastAt - a.lastAt).slice(0, LIST)
  return (
    <ul className="resources__recent">
      {ranked.map((s) => (
        <li key={s.key}>
          <button
            type="button"
            onMouseEnter={() => onHover(s)}
            onMouseLeave={() => onHover(null)}
            onFocus={() => onHover(s)}
            onBlur={() => onHover(null)}
            onClick={() => { onHover(null); onSelect(s) }}
            title="Ver detalhes do ponto"
          >
            <span className={`resources__swatch resources__swatch--t${tierKey(s.tier)}`} />
            <span className="resources__recent-name">{s.count}× · {s.medianGapMs != null ? `a cada ~${span(s.medianGapMs)}` : 'só 1 achado'}</span>
            <time>{when(s.lastAt)}</time>
          </button>
        </li>
      ))}
    </ul>
  )
}
