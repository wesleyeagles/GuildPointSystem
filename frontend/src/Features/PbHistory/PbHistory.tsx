import { useState } from 'react'
import { Navigate } from 'react-router-dom'
import {
  PB_KIND_LABEL,
  useIgnorePbCandidate,
  usePbAttendance,
  usePbKills,
  usePbMapBounds,
  usePbOverview,
  useRemovePbBoss,
  useSavePbBoss,
  type PbBossKind,
  type PbKill,
  type PbOverview,
} from '@/Domain/PbHistory/hooks/usePbHistory'
import { mapLabel } from '@/Domain/Location/gameMaps'
import { gameMapPoint } from '@/Domain/Location/gameMapPoint'
import { publicAssetUrl } from '@/Shared/utils/publicAssetUrl'
import { Button } from '@/Shared/ui/components/Button/Button'
import { Panel } from '@/Shared/ui/components/Panel/Panel'
import { useAppToast } from '@/Shared/ui/components/AppToast/AppToast'
import './PbHistory.styles.scss'

function when(iso: string) {
  return new Date(iso).toLocaleString('pt-BR', {
    timeZone: 'America/Sao_Paulo', day: '2-digit', month: '2-digit', year: '2-digit', hour: '2-digit', minute: '2-digit',
  })
}

function duration(fromIso: string, toIso: string) {
  const s = Math.max(0, Math.round((Date.parse(toIso) - Date.parse(fromIso)) / 1000))
  const m = Math.floor(s / 60)
  return m >= 60 ? `${Math.floor(m / 60)} h ${m % 60} min` : `${m} min ${String(s % 60).padStart(2, '0')} s`
}

const bossName = (k: { boss?: string; name?: string; label: string | null }) => k.label ?? k.boss ?? k.name ?? ''

const TABS = [
  { key: 'kills', label: 'Histórico' },
  { key: 'attendance', label: 'Frequência' },
  { key: 'bosses', label: 'Bosses' },
] as const

/**
 * Histórico de PB: a DLL de cada membro vê o boss na lista de monstros do jogo e avisa o site enquanto ele está
 * vivo e quando morre. Presente = viu o boss vivo e estava perto na morte. Só Administrador+ (menu de admin).
 */
export function PbHistoryPage() {
  const { data: overview, isLoading } = usePbOverview()
  const [tab, setTab] = useState<(typeof TABS)[number]['key']>('kills')
  if (isLoading) return <p className="pb-history__muted">Carregando…</p>
  if (!overview?.canView) return <Navigate to="/" replace />
  return (
    <div className="pb-history">
      <div className="pb-history__tabs" role="tablist">
        {TABS.map((t) => (
          <button
            key={t.key}
            type="button"
            role="tab"
            aria-selected={tab === t.key}
            className={`pb-history__tab${tab === t.key ? ' pb-history__tab--active' : ''}`}
            onClick={() => setTab(t.key)}
          >
            {t.label}
            {t.key === 'bosses' && overview.bosses.length === 0 && ' (configure)'}
          </button>
        ))}
      </div>
      {tab === 'kills' && <KillsPanel overview={overview} />}
      {tab === 'attendance' && <AttendancePanel />}
      {tab === 'bosses' && <BossesPanel overview={overview} />}
    </div>
  )
}

function KillsPanel({ overview }: { overview: PbOverview }) {
  const [boss, setBoss] = useState('')
  const { data, isLoading, fetchNextPage, hasNextPage, isFetchingNextPage } = usePbKills(boss, null)
  const kills = data?.pages.flat() ?? []
  return (
    <Panel title="PBs registrados" code={`${kills.length}${hasNextPage ? '+' : ''}`}>
      <div className="pb-history__filters">
        <select value={boss} onChange={(e) => setBoss(e.target.value)} aria-label="Filtrar por boss">
          <option value="">Todos os bosses</option>
          {overview.bosses.map((b) => <option key={b.name} value={b.name}>{bossName(b)}</option>)}
        </select>
      </div>
      {overview.bosses.length === 0 && (
        <p className="pb-history__muted">Nenhum boss acompanhado ainda: escolha os bosses na aba Bosses.</p>
      )}
      {isLoading ? <p className="pb-history__muted">Carregando…</p> : kills.length === 0 ? (
        <p className="pb-history__muted">Nenhum PB registrado ainda.</p>
      ) : (
        <ul className="pb-history__kills">
          {kills.map((k) => <KillRow key={k.id} kill={k} />)}
        </ul>
      )}
      {hasNextPage && (
        <div className="pb-history__actions">
          <Button size="sm" variant="secondary" loading={isFetchingNextPage} onClick={() => fetchNextPage()}>Mostrar mais</Button>
        </div>
      )}
    </Panel>
  )
}

/** Última posição em que o boss foi visto, nas coordenadas do mundo do jogo. */
function coordinates(kill: PbKill) {
  return `X ${kill.x} · Z ${kill.z}`
}

/** Mapa do jogo com a bolinha onde o boss foi visto por último (qualquer mapa com imagem e limites conhecidos). */
function BossMap({ kill }: { kill: PbKill }) {
  const { data: bounds } = usePbMapBounds()
  const live = bounds?.find((b) => b.map === kill.mapCode) ?? null
  const placement = gameMapPoint(kill.mapCode, kill.x, kill.z, live)
  if (!placement) return null
  const { geometry, px, py } = placement
  // A bolinha acompanha o tamanho da imagem (o mapa de Elan tem 1400 px; o de Sette, 400).
  const r = Math.max(geometry.width, geometry.height) / 70
  return (
    <figure className="pb-history__map">
      <svg viewBox={`0 0 ${geometry.width} ${geometry.height}`} role="img" aria-label={`${bossName(kill)} em ${geometry.name}`}>
        <image href={publicAssetUrl(geometry.image)} x="0" y="0" width={geometry.width} height={geometry.height}
          preserveAspectRatio="none" />
        <circle className="pb-history__map-pulse" cx={px} cy={py} r={r * 1.8} strokeWidth={r / 2.5} />
        <circle className="pb-history__map-dot" cx={px} cy={py} r={r} strokeWidth={r / 3.5} />
      </svg>
      <figcaption>{kill.mapName ? mapLabel(kill.mapName) : geometry.name} · {coordinates(kill)}</figcaption>
    </figure>
  )
}

function KillRow({ kill }: { kill: PbKill }) {
  const [open, setOpen] = useState(false)
  const present = kill.members.filter((m) => m.present)
  const seen = kill.members.filter((m) => !m.present)
  return (
    <li className={`pb-history__kill${kill.diedAt ? '' : ' pb-history__kill--open'}`}>
      <button type="button" className="pb-history__kill-head" onClick={() => setOpen(!open)} aria-expanded={open}>
        <span className="pb-history__kill-name">
          {bossName(kill)}
          <small className={`pb-history__kind pb-history__kind--${kill.kind.toLowerCase()}`}>{PB_KIND_LABEL[kill.kind]}</small>
        </span>
        <span className="pb-history__where">
          {kill.mapName ? mapLabel(kill.mapName) : '—'}
          <small>{coordinates(kill)}</small>
        </span>
        <time>{when(kill.diedAt ?? kill.firstSeenAt)}</time>
        <span>{kill.diedAt ? `Luta: ${duration(kill.firstSeenAt, kill.diedAt)}` : 'Sem morte registrada'}</span>
        <span className="pb-history__count">{present.length} presente{present.length === 1 ? '' : 's'}</span>
      </button>
      {open && (
        <div className="pb-history__kill-body">
          <dl className="pb-history__detail">
            <dt>Visto pela 1ª vez</dt><dd>{when(kill.firstSeenAt)}</dd>
            <dt>Onde</dt><dd>{kill.mapName ? mapLabel(kill.mapName) : '—'} · {coordinates(kill)}</dd>
            <dt>Morreu</dt><dd>{kill.diedAt ? when(kill.diedAt) : '—'}</dd>
            {!kill.diedAt && kill.minHp != null && <><dt>Menor HP visto</dt><dd>{kill.minHp}%</dd></>}
          </dl>
          <BossMap kill={kill} />
          <h4>Presentes na morte</h4>
          <Chips names={present.map((m) => m.nickname)} empty="Ninguém" />
          {seen.length > 0 && (
            <>
              <h4>Viram o boss, mas não estavam perto na morte</h4>
              <Chips names={seen.map((m) => m.nickname)} muted />
            </>
          )}
        </div>
      )}
    </li>
  )
}

function Chips({ names, empty, muted }: { names: string[]; empty?: string; muted?: boolean }) {
  if (names.length === 0) return <em className="pb-history__muted">{empty}</em>
  return (
    <div className="pb-history__chips">
      {names.map((n) => <span key={n} className={`pb-history__chip${muted ? ' pb-history__chip--muted' : ''}`}>{n}</span>)}
    </div>
  )
}

const DAYS = [7, 30, 90]

function AttendancePanel() {
  const [days, setDays] = useState(30)
  const { data, isLoading } = usePbAttendance(days)
  return (
    <Panel title="Frequência nos PBs" code={data ? `${data.kills} PB${data.kills === 1 ? '' : 's'}` : undefined}>
      <div className="pb-history__filters">
        <select value={days} onChange={(e) => setDays(Number(e.target.value))} aria-label="Período">
          {DAYS.map((d) => <option key={d} value={d}>Últimos {d} dias</option>)}
        </select>
      </div>
      <p className="pb-history__muted">
        Conta só os PBs com morte registrada. "Presente" = viu o boss vivo e estava perto na morte; "Viu" = esteve por
        perto em algum momento da luta.
      </p>
      {isLoading || !data ? <p className="pb-history__muted">Carregando…</p> : data.rows.length === 0 ? (
        <p className="pb-history__muted">Nenhum PB nesse período.</p>
      ) : (
        <table className="pb-history__table">
          <thead><tr><th>Membro</th><th>Presente</th><th>Viu</th><th>% dos PBs</th></tr></thead>
          <tbody>
            {data.rows.map((r) => (
              <tr key={r.memberId}>
                <td>{r.nickname}</td>
                <td>{r.present}</td>
                <td>{r.seen}</td>
                <td>{data.kills ? Math.round((r.present * 100) / data.kills) : 0}%</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </Panel>
  )
}

function BossesPanel({ overview }: { overview: PbOverview }) {
  const { showToast } = useAppToast()
  const save = useSavePbBoss()
  const remove = useRemovePbBoss()
  const ignore = useIgnorePbCandidate()
  const [name, setName] = useState('')
  const [label, setLabel] = useState('')
  const [kind, setKind] = useState<PbBossKind>('ROTACAO')
  const [search, setSearch] = useState('')
  const fail = (e: unknown) => showToast(e instanceof Error ? e.message : 'Não foi possível salvar.', 'error')

  const add = (n: string, l: string | null, k: PbBossKind) => save.mutate({ name: n, label: l, kind: k }, {
    onSuccess: () => { showToast(`${n}: acompanhado.`, 'success'); setName(''); setLabel('') },
    onError: fail,
  })
  const term = search.trim().toLocaleLowerCase('pt-BR')
  const candidates = term ? overview.candidates.filter((c) => c.name.toLocaleLowerCase('pt-BR').includes(term)) : overview.candidates

  return (
    <>
      <Panel title="Bosses acompanhados" code={`${overview.bosses.length}`}>
        <p className="pb-history__muted">
          A DLL acompanha estes bosses em qualquer horário. O nome tem que ser igual ao do jogo, sem o nível
          ("Warbeast", não "[65]Warbeast"). O apelido é só para mostrar no site. Precisa da DLL nova.
        </p>
        {overview.bosses.length > 0 && (
          <table className="pb-history__table">
            <thead><tr><th>Nome no jogo</th><th>Apelido</th><th>Tipo</th><th>PBs</th><th>Último</th><th /></tr></thead>
            <tbody>
              {overview.bosses.map((b) => (
                <tr key={b.name}>
                  <td>{b.name}</td>
                  <td>{b.label ?? '—'}</td>
                  <td>
                    <select
                      value={b.kind}
                      aria-label={`Tipo de ${b.name}`}
                      disabled={save.isPending}
                      onChange={(e) => save.mutate({ name: b.name, label: b.label, kind: e.target.value as PbBossKind }, { onError: fail })}
                    >
                      <option value="ROTACAO">{PB_KIND_LABEL.ROTACAO}</option>
                      <option value="EXTRA">{PB_KIND_LABEL.EXTRA}</option>
                    </select>
                  </td>
                  <td>{b.kills}</td>
                  <td>{b.lastKillAt ? when(b.lastKillAt) : '—'}</td>
                  <td>
                    <Button size="sm" variant="ghost" disabled={remove.isPending}
                      onClick={() => remove.mutate(b.name, { onSuccess: () => showToast(`${b.name}: não é mais acompanhado.`, 'success'), onError: fail })}>
                      Remover
                    </Button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
        <form
          className="pb-history__form"
          onSubmit={(e) => { e.preventDefault(); if (name.trim()) add(name.trim(), label.trim() || null, kind) }}
        >
          <input placeholder="Nome no jogo" aria-label="Nome do boss no jogo" value={name} onChange={(e) => setName(e.target.value)} maxLength={80} />
          <input placeholder="Apelido (opcional)" aria-label="Apelido" value={label} onChange={(e) => setLabel(e.target.value)} maxLength={64} />
          <select value={kind} onChange={(e) => setKind(e.target.value as PbBossKind)} aria-label="Tipo">
            <option value="ROTACAO">{PB_KIND_LABEL.ROTACAO}</option>
            <option value="EXTRA">{PB_KIND_LABEL.EXTRA}</option>
          </select>
          <Button size="sm" type="submit" loading={save.isPending} disabled={!name.trim()}>Adicionar</Button>
        </form>
      </Panel>

      <Panel title="Monstros vistos nos horários de PB" code={`${overview.candidates.length}`}>
        <p className="pb-history__muted">
          Nos 20 minutos depois de cada horário de PB (2h, 6h, 10h, 14h, 18h e 22h) a DLL manda o nome de todo monstro
          que vê. Marque os que são bosses; "Ignorar" esconde monstros comuns. Os mais vistos ficam em cima.
        </p>
        <input className="pb-history__search" type="search" placeholder="Procurar nome" aria-label="Procurar monstro"
          value={search} onChange={(e) => setSearch(e.target.value)} />
        {candidates.length === 0 ? <p className="pb-history__muted">Nenhum monstro {term ? 'com esse nome' : 'ainda'}.</p> : (
          <table className="pb-history__table">
            <thead><tr><th>Nome no jogo</th><th>Mapa</th><th>Vezes</th><th>Último</th><th /></tr></thead>
            <tbody>
              {candidates.map((c) => (
                <tr key={c.name}>
                  <td>{c.name}</td>
                  <td>{c.mapName ? mapLabel(c.mapName) : '—'}</td>
                  <td>{c.seenCount}</td>
                  <td>{when(c.lastSeenAt)}</td>
                  <td className="pb-history__row-actions">
                    <Button size="sm" disabled={save.isPending} onClick={() => add(c.name, null, 'ROTACAO')}>PB da rotação</Button>
                    <Button size="sm" variant="secondary" disabled={save.isPending} onClick={() => add(c.name, null, 'EXTRA')}>Extra</Button>
                    <Button size="sm" variant="ghost" disabled={ignore.isPending} onClick={() => ignore.mutate(c.name, { onError: fail })}>Ignorar</Button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </Panel>
    </>
  )
}
