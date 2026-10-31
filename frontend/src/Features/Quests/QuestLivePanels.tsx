import { useEffect, useState, type ReactNode } from 'react'
import type { ExpectedQuest, QuestHistory } from '@/Domain/Quest/types'
import { useQuestVisibility } from '@/Domain/Quest/hooks/useQuests'
import { useAuthContext } from '@/Features/Auth/contexts/AuthContext'
import { Panel } from '@/Shared/ui/components/Panel/Panel'
import { Button } from '@/Shared/ui/components/Button/Button'

export function QuestPlayerPanel({ data }: { data: QuestHistory | undefined }) {
  const [now, setNow] = useState(Date.now)
  useEffect(() => { const timer = setInterval(() => setNow(Date.now()), 5000); return () => clearInterval(timer) }, [])
  const player = data?.player
  const sample = player?.sample
  // O CerberusBags envia a cada 60s; considera "conectado" ate 90s apos a ultima leitura.
  const fresh = !!player && !player.revoked && sample?.status === 'OBSERVED' && now - Date.parse(player.receivedAt) < 90_000
  return <Panel className="quests__character" aria-label="Personagem identificado">
    <div className="quests__identity">
      <span className="quests__avatar" aria-hidden="true">{sample?.name.charAt(0).toUpperCase() || '—'}</span>
      <div><span className="quests__eyebrow">Personagem</span><h2>{sample?.name || 'Conecte seu personagem'}</h2>
        <span className={`quests__connection${fresh ? ' is-live' : ''}`} role="status">{fresh ? 'Monitor conectado' : sample ? 'Sem leitura recente' : 'Monitor desconectado'}</span>
      </div>
    </div>
    {sample && <div className="quests__stats"><div className="quests__level"><span>Nível</span><strong>{sample.level}</strong></div>
      <div className="quests__xp"><div><span>XP</span><strong>{sample.xpPercent == null ? 'Indisponível' : sample.xpPercent.toFixed(2) + '%'}</strong></div>
        {sample.xpPercent != null && <progress aria-label="Progresso de XP" max={100} value={sample.xpPercent} />}
      </div></div>}
    <details className="quests__character-info"><summary>Conexão</summary>
      {sample && <p>Última leitura: {new Date(sample.sampledAt).toLocaleString('pt-BR', { timeZone: 'America/Sao_Paulo' })} (Brasília).</p>}
      <p>Identidade observada no cliente, vinculada ao cadastro no pareamento.</p>
      <a href="#quest-monitor-pairing">Gerenciar monitor</a>
    </details>
  </Panel>
}

const difficulties = ['Easy', 'Easy+', 'Medium', 'Medium+', 'Hard', 'Hard+', 'Outras']
const extraRewards: Record<string, string> = { Q070944: '1 caixa com 10 chocolates', Q070947: '1 caixa com 2 poções do caos' }
const extraCodes = new Set(Object.keys(extraRewards))
const emblemGroups = [['Easy', 'Easy+'], ['Medium', 'Medium+'], ['Hard', 'Hard+']]
// O jogo libera menos quests por dia do que o NPC oferece: só 2 das 3 Easy+.
const dailyLimits = [{ codes: new Set(['Q071401', 'Q071402', 'Q071403']), limit: 2 }]
const isDone = (row: ExpectedQuest) => row.completionSignals > 0
function progress(rows: ExpectedQuest[]) {
  let completed = rows.filter(isDone).length
  let total = rows.length
  for (const { codes, limit } of dailyLimits) {
    const members = rows.filter(row => codes.has(row.definition.quest.code))
    const done = members.filter(isDone).length
    completed -= done - Math.min(done, limit)
    total -= members.length - Math.min(members.length, limit)
  }
  return { completed, total }
}
function dailyLimitReached(row: ExpectedQuest, all: ExpectedQuest[]) {
  if (isDone(row)) return null
  const group = dailyLimits.find(({ codes }) => codes.has(row.definition.quest.code))
  if (!group) return null
  return all.filter(r => group.codes.has(r.definition.quest.code) && isDone(r)).length >= group.limit ? group.limit : null
}
function difficulty(title: string) {
  const match = title.match(/\b(Easy|Medium|Hard)\+?(?=\s|$)/i)?.[0].toLowerCase()
  return difficulties.find(value => value.toLowerCase() === match) ?? 'Outras'
}
function shortName(title: string, code: string, index: number) {
  if (code === 'Q070944') return 'Chocolate Reward'
  if (code === 'Q070947') return 'Chaos Potion Reward'
  if (/Cartella Quest Reward/i.test(title)) return `${difficulty(title)} · Quest ${title.match(/Reward\s+(\d+)/i)?.[1] ?? index + 1}`
  return title.replace(/\[24[^\]]*\]|\(EXP(?:\s+\d+%)?\)|\[Party\]|\[Group\]|\[\d+(?:-\d+)?\]/gi, '').replace(/^(\[(?!\d+\/\d+\])[^\]]+\]\s*)+/, '').replace(/\s+/g, ' ').trim()
}
function rewardText(row: ExpectedQuest) {
  if (extraRewards[row.definition.quest.code]) return extraRewards[row.definition.quest.code]
  const parts = row.experiencePercent == null ? [] : [`${row.experiencePercent.toLocaleString('pt-BR', { maximumFractionDigits: 4 })}% XP`]
  parts.push(...(row.definition.rewards ?? []).map(item => {
    const name = item.name.replace('Small Quest Reward Box', 'caixa de recompensa')
      .replace('Green Cartella Emblem(st.1.1)', 'Emblema verde')
      .replace('Yellow Cartella Emblem(st.1.1)', 'Emblema amarelo')
      .replace('Red Cartella Emblem(st.1.1)', 'Emblema vermelho')
    return `${item.quantity}× ${name}`
  }))
  return parts.join(' · ') || 'Recompensa não informada'
}

export function QuestDailyList({ data, toolbar }: { data: QuestHistory | undefined; toolbar?: ReactNode }) {
  const { hasRole } = useAuthContext()
  const admin = hasRole('ADMINISTRADOR')
  const visibility = useQuestVisibility()
  const [npc, setNpc] = useState('')
  const [search, setSearch] = useState('')
  const [showHidden, setShowHidden] = useState(false)
  const [managing, setManaging] = useState(false)
  const [collapsed, setCollapsed] = useState(() => {
    const compact = window.matchMedia?.('(max-height: 820px), (max-width: 1450px)').matches
    const mobile = window.matchMedia?.('(max-width: 600px)').matches
    return new Map<string, boolean>(['Medium / Medium+', 'Hard / Hard+',
      ...(compact ? ['Easy / Easy+', 'EXP Quester(40+)', 'Extras'] : []),
      ...(mobile ? ['PartyEXPQuester(CashShop)', 'Extras'] : [])].map(name => [name, true]))
  })
  const rows = (data?.expected ?? []).filter(row => (showHidden || !row.hidden) && (!npc || row.definition.npcName === npc)
    && [row.definition.quest.code, row.definition.quest.title, ...row.definition.objectives, rewardText(row)].join(' ').toLowerCase().includes(search.toLowerCase()))
  const { completed, total } = progress(rows)
  const level = (data?.expected ?? []).find(row => row.rewardLevel != null)?.rewardLevel ?? data?.player?.sample.level
  const legacy = rows.filter(row => row.definition.minLevel == null && !extraCodes.has(row.definition.quest.code))
  const experienceNpcs = [...new Set(rows.filter(row => row.definition.minLevel != null).map(row => row.definition.npcName))]
  const extras = rows.filter(row => extraCodes.has(row.definition.quest.code))
  const other = legacy.filter(row => difficulty(row.definition.quest.title) === 'Outras')
  const groupNames = [...emblemGroups.map(group => group.join(' / ')), ...experienceNpcs, 'Outras', 'Extras']
  const renderQuest = (row: ExpectedQuest, index: number) => {
    const d = row.definition
    const group = difficulty(d.quest.title)
    const done = isDone(row)
    const limit = dailyLimitReached(row, data?.expected ?? [])
    const state = done ? 'Concluída' : limit ? 'Limite do dia atingido' : 'Sem confirmação'
    return <li className={done ? 'is-complete' : limit ? 'is-capped' : ''} key={d.quest.definitionHash}>
      <article className="quests__quest-card">
        <details className="quests__quest-detail"><summary aria-label={`${d.quest.title} · ${d.quest.code} · ${state}`}>
          <span className="quests__reward-slot" aria-hidden="true">
            {group !== 'Outras' ? <img src={`/icons/quests/${group.replace('+', '').toLowerCase()}.png`} width={20} height={20} alt="" /> : <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.3"><path d="M4 9h16v11H4zM3 5h18v4H3zM12 5v15" /></svg>}
          </span>
          <strong className="quests__quest-name">{shortName(d.quest.title, d.quest.code, index)}</strong>
          <input type="checkbox" checked={done} disabled aria-label={d.quest.title} />
        </summary>
          <div className="quests__quest-body"><span className="quests__code">{d.quest.code}</span><p>{d.npcName} · {d.npcCodes.join(', ')}</p>
            <p>{state}{row.rewardLevel != null ? ` · XP calculada para o nível ${row.rewardLevel}` : ''}</p>
            {row.hidden && <p>Ocultada</p>}
            {admin && managing && <Button size="sm" variant="ghost" disabled={visibility.isPending} onClick={() => visibility.mutate({ hash: d.quest.definitionHash, hidden: !row.hidden })}>{row.hidden ? 'Restaurar na lista' : 'Ocultar da lista'}</Button>}
          </div></details>
        <dl className="quests__quest-facts">
          <div><dt><abbr title="Objetivo">Obj.</abbr></dt><dd>{d.objectives.join('; ') || 'Objetivo não informado'}</dd></div>
          <div className="quests__reward-text" title={row.rewardLevel != null ? `XP do nível ${row.rewardLevel} → ${row.rewardLevel + 1}` : undefined}><dt><abbr title="Recompensa">Rec.</abbr></dt><dd>{rewardText(row)}</dd></div>
        </dl>
        {limit && <p className="quests__limit">Limite do dia atingido ({limit} de {limit})</p>}
      </article>
    </li>
  }
  const renderGroup = (name: string, entries: ExpectedQuest[], className = '') => {
    if (!entries.length) return null
    const count = progress(entries)
    if (name.includes('PartyEXP') && entries.length > 2) className += ' quests__group--dense'
    const crowdedParty = rows.filter(row => row.definition.npcName.includes('PartyEXP')).length > 2
    const open = !(collapsed.get(name) ?? (name === 'Extras' && crowdedParty)) || !!search
    const id = `quest-group-${name.replace(/[^a-z0-9]/gi, '-')}`
    return <section className={`quests__group ${className}`} key={name} aria-label={name}>
      <h3><button type="button" aria-expanded={open} aria-controls={id} onClick={() => setCollapsed(previous => new Map(previous).set(name, open))}>
        <span aria-hidden="true">{open ? '▾' : '▸'}</span><span>{name}</span><span className="quests__count">{count.completed}/{count.total}</span>
      </button></h3>
      <div id={id} hidden={!open}><ul>{entries.map(renderQuest)}</ul>
        {name === 'Easy / Easy+' && <p className="quests__limit">Easy+: limite de 2 conclusões por dia.</p>}
      </div>
    </section>
  }
  return <Panel className="quests__checklist" title="Suas quests" code={`CORA · ${completed} / ${total}`} actions={<><div className="quests__toolbar"><span className="quests__level-tag">Cora{level != null ? ` · Nível ${level}` : ''}</span>
      <label className="quests__search"><span className="quests__sr-only">Buscar quest</span><input value={search} onChange={e => setSearch(e.target.value)} placeholder="Buscar nome, objetivo ou recompensa" /></label>
      <Button size="sm" variant="ghost" onClick={() => { setSearch(''); setCollapsed(new Map(groupNames.map(name => [name, true]))) }}>Recolher grupos</Button>
    </div>{toolbar}</>}>
    <progress className="quests__sr-only" aria-label="Progresso da checklist" max={Math.max(1, total)} value={completed} />

    {visibility.error && <p role="alert">{visibility.error.message}</p>}
    {!rows.length && <p className="quests__empty">Nenhuma quest nesta seleção.</p>}
    <div className="quests__board">
      {!!legacy.length && <section className="quests__emblems" aria-label="Emblemas"><h2>Emblemas<span className="quests__count">{progress(legacy).completed}/{progress(legacy).total}</span></h2>
        {emblemGroups.map(group => renderGroup(group.join(' / '), legacy.filter(row => group.includes(difficulty(row.definition.quest.title)))))}
        {renderGroup('Outras', other)}
      </section>}
      <div className="quests__exp-column">{experienceNpcs.filter(name => !name.includes('PartyEXP')).map(name => renderGroup(name, rows.filter(row => row.definition.minLevel != null && row.definition.npcName === name), 'quests__npc-group'))}</div>
      <div className="quests__extra-column">{experienceNpcs.filter(name => name.includes('PartyEXP')).map(name => renderGroup(name, rows.filter(row => row.definition.minLevel != null && row.definition.npcName === name), 'quests__npc-group'))}
        {renderGroup('Extras', extras)}
      </div>
    </div>
    <footer className="quests__list-footer"><span>Reset 03h · Brasília</span>
      <details onToggle={e => setManaging(e.currentTarget.open)}><summary>{admin ? 'Gerenciar lista' : 'Filtrar lista'}</summary>
        <div className="quests__filters"><label>NPC <select value={npc} onChange={e => setNpc(e.target.value)}><option value="">Todos</option>{(data?.npcCoverage ?? []).map(n => <option key={n.name}>{n.name}</option>)}</select></label>
          {admin && <label className="quests__check"><input type="checkbox" checked={showHidden} onChange={e => setShowHidden(e.target.checked)} />Mostrar ocultadas</label>}
        </div>
        {(data?.npcCoverage ?? []).filter(n => n.questCount === 0).map(n => <p key={n.name}>{n.name}: nenhum vínculo de quest encontrado neste catálogo.</p>)}
        <p>Marcações do monitor local. Sem confirmação não significa que a quest não foi feita.</p>
      </details>
    </footer>
  </Panel>
}
