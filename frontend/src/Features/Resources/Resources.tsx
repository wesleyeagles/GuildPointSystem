import { useMemo, useState } from 'react'
import { Navigate } from 'react-router-dom'
import {
  isDeadFlash,
  isFresh,
  mapPoint,
  TIER_LABEL,
  useResourceOverview,
  useResourceSpawns,
  type ResourceMap,
  type ResourceMapScale,
  type ResourceSpawn,
} from '@/Domain/Resource/hooks/useResources'
import { mapLabel } from '@/Domain/Location/gameMaps'
import { Panel } from '@/Shared/ui/components/Panel/Panel'
import { publicAssetUrl } from '@/Shared/utils/publicAssetUrl'
import { useServerNow } from '@/Shared/utils/useServerNow'
import { ResourceSettings } from './components/ResourceSettings'
import './Resources.styles.scss'

/** Grau -> chave das cores (iguais às bolinhas do mapa no jogo); null = desconhecido. */
const tierKey = (tier: number | null) => (tier && tier >= 1 && tier <= 3 ? String(tier) : 'u')
const TIERS: { key: string; label: string }[] = [
  { key: '1', label: TIER_LABEL[1] },
  { key: '2', label: TIER_LABEL[2] },
  { key: '3', label: TIER_LABEL[3] },
  { key: 'u', label: 'Desconhecido' },
]
const RADIUS: Record<string, number> = { '1': 2.2, '2': 3.6, '3': 5.5, u: 3.6 }
const PERIODS = [
  { key: 'all', label: 'Tudo', ms: 0 },
  { key: '1d', label: '24 horas', ms: 86_400_000 },
  { key: '7d', label: '7 dias', ms: 7 * 86_400_000 },
  { key: '30d', label: '30 dias', ms: 30 * 86_400_000 },
]
const RECENT = 40

function when(iso: string) {
  return new Date(iso).toLocaleString('pt-BR', {
    timeZone: 'America/Sao_Paulo', day: '2-digit', month: '2-digit', year: '2-digit', hour: '2-digit', minute: '2-digit',
  })
}

/**
 * Mapa de recursos: onde os membros (pela DLL) viram flores, minério e cactos nascerem, com o horário.
 * Só Administrador+ (menu de admin); a lista de liberados vale para as bolinhas no mapa do jogo.
 */
export function ResourcesPage() {
  const { data: overview, isLoading } = useResourceOverview()
  const [mapCode, setMapCode] = useState(11)
  if (isLoading) return <p className="resources__muted">Carregando…</p>
  if (!overview?.canView) return <Navigate to="/" replace />
  const map = overview.maps.find((m) => m.code === mapCode) ?? overview.maps[0]
  return (
    <div className="resources">
      <div className="resources__tabs" role="tablist">
        {overview.maps.map((m) => (
          <button
            key={m.code}
            type="button"
            role="tab"
            aria-selected={m.code === map.code}
            className={`resources__tab${m.code === map.code ? ' resources__tab--active' : ''}`}
            onClick={() => setMapCode(m.code)}
          >
            {mapLabel(m.name)}
          </button>
        ))}
      </div>
      <ResourceMapPanel key={map.code} map={map} />
      {overview.canConfigure && <ResourceSettings overview={overview} />}
    </div>
  )
}

function ResourceMapPanel({ map }: { map: ResourceMap }) {
  const { data: spawns = [], isLoading } = useResourceSpawns(map.code, true)
  const [shown, setShown] = useState<Record<string, boolean>>({ '1': true, '2': true, '3': true, u: true })
  const [period, setPeriod] = useState('all')
  const [hover, setHover] = useState<ResourceSpawn | null>(null)
  const now = useServerNow(5_000)

  const counts = useMemo(() => {
    const c: Record<string, number> = { '1': 0, '2': 0, '3': 0, u: 0 }
    for (const s of spawns) c[tierKey(s.tier)]++
    return c
  }, [spawns])

  const visible = useMemo(() => {
    const ms = PERIODS.find((p) => p.key === period)?.ms ?? 0
    const since = ms ? now - ms : 0
    return spawns.filter((s) => shown[tierKey(s.tier)] && (!since || Date.parse(s.foundAt) >= since))
  }, [spawns, shown, period, now])

  // Grau 1 por baixo e grau 3 por cima: os raros nunca ficam escondidos.
  const ordered = useMemo(() => {
    const rank = (s: ResourceSpawn) => (s.tier === 3 ? 3 : s.tier === 2 ? 2 : s.tier == null ? 1 : 0)
    return [...visible].sort((a, b) => rank(a) - rank(b) || a.id - b.id)
  }, [visible])
  const recent = useMemo(() => [...visible].sort((a, b) => b.id - a.id).slice(0, RECENT), [visible])
  const view = map.view

  return (
    <Panel title={mapLabel(map.name)} code={`${visible.length} de ${spawns.length}`} className="resources__panel">
      <div className="resources__filters">
        {TIERS.map((t) => (
          <label key={t.key} className={`resources__filter resources__filter--t${t.key}`}>
            <input
              type="checkbox"
              checked={shown[t.key]}
              onChange={(e) => setShown({ ...shown, [t.key]: e.target.checked })}
            />
            <span className="resources__swatch" />
            {t.label} <small>({counts[t.key]})</small>
          </label>
        ))}
        <select className="resources__period" value={period} onChange={(e) => setPeriod(e.target.value)} aria-label="Período">
          {PERIODS.map((p) => <option key={p.key} value={p.key}>{p.label}</option>)}
        </select>
      </div>

      <div className="resources__body">
        <div className="resources__map">
          <svg viewBox={`0 0 ${view?.areaW ?? 400} ${view?.areaH ?? 400}`} role="img" aria-label={`Mapa ${mapLabel(map.name)}`}>
            <image href={publicAssetUrl(`/resource-maps/${map.code}.png`)} x="0" y="0" width={view?.areaW ?? 400} height={view?.areaH ?? 400} />
            {view && ordered.map((s) => {
              const p = mapPoint(view, s.x, s.z)
              if (!p) return null
              const key = tierKey(s.tier)
              return (
                <circle
                  key={s.id}
                  className={`resources__dot resources__dot--t${key}${hover?.id === s.id ? ' resources__dot--hover' : ''}`}
                  cx={p.px}
                  cy={p.py}
                  r={RADIUS[key]}
                  onMouseEnter={() => setHover(s)}
                  onMouseLeave={() => setHover((h) => (h?.id === s.id ? null : h))}
                >
                  <title>{`${s.name} · ${s.tier ? TIER_LABEL[s.tier] : 'grau desconhecido'} · ${when(s.foundAt)}${s.foundBy ? ` · ${s.foundBy}` : ''}`}</title>
                </circle>
              )
            })}
            {view && <ResourcePulses view={view} spawns={visible} />}
          </svg>
          {!view && (
            <p className="resources__notice">
              A escala deste mapa ainda não chegou. Ela vem sozinha quando alguém com a DLL nova estiver neste mapa.
            </p>
          )}
          {isLoading && <p className="resources__notice">Carregando pontos…</p>}
        </div>

        <div className="resources__side">
          <h3 className="resources__subtitle">{hover ? 'Ponto' : 'Achados recentes'}</h3>
          {hover ? (
            <dl className="resources__detail">
              <dt>Mob</dt><dd>{hover.name}</dd>
              <dt>Grau</dt><dd>{hover.tier ? TIER_LABEL[hover.tier] : 'Desconhecido'}</dd>
              <dt>Achado</dt><dd>{when(hover.foundAt)}</dd>
              <dt>Por</dt><dd>{hover.foundBy ?? '—'}</dd>
              {hover.diedAt && <><dt>Morreu</dt><dd>{when(hover.diedAt)}</dd></>}
              <dt>Posição</dt><dd>{hover.x}, {hover.z}</dd>
            </dl>
          ) : recent.length === 0 ? (
            <p className="resources__muted">Nenhum recurso achado {period === 'all' ? 'ainda' : 'nesse período'}.</p>
          ) : (
            <ul className="resources__recent">
              {recent.map((s) => (
                <li key={s.id} onMouseEnter={() => setHover(s)} onMouseLeave={() => setHover(null)}>
                  <span className={`resources__swatch resources__swatch--t${tierKey(s.tier)}`} />
                  <span className="resources__recent-name">{s.name}</span>
                  <time>{when(s.foundAt)}</time>
                </li>
              ))}
            </ul>
          )}
        </div>
      </div>
    </Panel>
  )
}

/**
 * Por cima de tudo. Achado no último minuto e vivo: duas ondas saindo do ponto e o ponto piscando.
 * Morreu enquanto piscava: vermelho piscante por 5 s. Só esta camada acompanha o relógio de perto.
 */
function ResourcePulses({ view, spawns }: { view: ResourceMapScale; spawns: ResourceSpawn[] }) {
  const now = useServerNow(250)
  return (
    <>
      {spawns.map((s) => {
        const fresh = isFresh(s, now)
        const dead = !fresh && isDeadFlash(s, now)
        if (!fresh && !dead) return null
        const p = mapPoint(view, s.x, s.z)
        if (!p) return null
        const key = tierKey(s.tier)
        if (dead) {
          return (
            <g key={`dead-${s.id}`} className="resources__dead" pointerEvents="none">
              <circle className="resources__dead-ring" cx={p.px} cy={p.py} r={RADIUS[key] + 5} />
              <circle className="resources__dead-core" cx={p.px} cy={p.py} r={RADIUS[key] + 2} />
            </g>
          )
        }
        return (
          <g key={`fresh-${s.id}`} className={`resources__fresh resources__fresh--t${key}`} pointerEvents="none">
            <circle className="resources__wave" cx={p.px} cy={p.py} r={RADIUS[key] + 2} />
            <circle className="resources__wave resources__wave--late" cx={p.px} cy={p.py} r={RADIUS[key] + 2} />
            <circle className="resources__beat" cx={p.px} cy={p.py} r={RADIUS[key] + 1.5} />
          </g>
        )
      })}
    </>
  )
}
