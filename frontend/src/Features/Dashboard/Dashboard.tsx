import { useCallback, useEffect, useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import { useFitRows } from '@/Shared/utils/useFitRows'
import { useMatchBottom } from '@/Shared/utils/useMatchBottom'
import { Pagination } from '@/Features/Dashboard/components/Pagination'
import { RecentPointsPanel } from '@/Features/Dashboard/components/RecentPointsPanel'
import {
  MemberContextMenu,
  SellLock,
  type MemberMenuState,
} from '@/Features/Dashboard/components/MemberContextMenu'
import { sellBlockedUntil } from '@/Features/Dashboard/utils/sellBlock'
import { CerberusCountdown } from '@/Features/ServerInfo/components/CerberusCountdown'
import { useCerberusLaunched } from '@/Features/ServerInfo/hooks/useCerberusLaunched'
import { useMemberRanking } from '@/Domain/Member/hooks/useMembers'
import type { Member } from '@/Domain/types/models'
import { useAuthContext } from '@/Features/Auth/contexts/AuthContext'
import {
  useCharacterRanking,
  useCharacterStatusSubscription,
} from '@/Domain/CharacterStatus/hooks/useCharacterStatus'
import { useEquipment } from '@/Domain/Equipment/hooks/useEquipment'
import { currentLocation } from '@/Domain/Location/gameMaps'
import { useDiscordMembers, type DiscordMember } from '@/Domain/Discord/hooks/useDiscordMembers'
import { discordProfileUrl, openInDiscordApp } from '@/Shared/utils/discordLink'
import { Panel } from '@/Shared/ui/components/Panel/Panel'
import './Dashboard.styles.scss'

type SortKey = 'points' | 'gameLevel' | 'gameExp' | 'gearScore'
/** Natural ranking row (46px + 1px border; stretched to fill the viewport) and pagination block. */
const RANK_ROW_HEIGHT = 47
const PAGINATION_HEIGHT = 46

/** Foto do Discord (ou o avatar do perfil); sem nenhuma, a inicial do nick. A classe fica no tooltip. */
function MemberAvatar({ member, discord, className }: { member: Member; discord?: DiscordMember; className: string }) {
  const url = discord?.avatarUrl || member.avatarUrl
  return url ? (
    <img className={className} src={url} alt="" loading="lazy" title={member.className ?? undefined} />
  ) : (
    <span className={`${className} ${className}--initial`} title={member.className ?? undefined} aria-hidden="true">
      {member.nickname.charAt(0).toUpperCase()}
    </span>
  )
}

/** "(Discord name)" in the member's Discord role color; opens their profile in the Discord app. */
function DiscordTag({ member, className }: { member?: DiscordMember; className: string }) {
  if (!member) return null
  return (
    <a
      href={discordProfileUrl(member.id)}
      target="_blank"
      rel="noopener noreferrer"
      className={className}
      style={member.color ? { color: member.color } : undefined}
      title={`Abrir ${member.name} no Discord`}
      onClick={(e) => openInDiscordApp(e, member.id)}
    >
      ({member.name})
    </a>
  )
}

type SellBlockFilter = 'all' | 'blocked' | 'unblocked'

/** Relógio de 30 s: some o local de quem parou de enviar sem esperar outro evento. */
function useMinuteClock() {
  const [now, setNow] = useState(() => Date.now())
  useEffect(() => {
    const id = window.setInterval(() => setNow(Date.now()), 30_000)
    return () => window.clearInterval(id)
  }, [])
  return now
}

export function DashboardPage() {
  const { data: ranking = [], isLoading } = useMemberRanking()
  const { data: characterStatus = [] } = useCharacterRanking()
  useCharacterStatusSubscription() // atualiza o ranking sem recarregar a página
  // Status de jogo (level/EXP) por membro, enviado pelo cliente CerberusBags.
  // Com varios personagens na conta, vale o que enviou por ultimo (o que esta sendo jogado).
  const gameStatusByMember = new Map<number, (typeof characterStatus)[number]>()
  for (const s of characterStatus) {
    const current = gameStatusByMember.get(s.memberId)
    if (!current || Date.parse(s.updatedAt) > Date.parse(current.updatedAt)) gameStatusByMember.set(s.memberId, s)
  }
  // Mapa e região onde o personagem está agora (envio da DLL nos últimos 3 min).
  const now = useMinuteClock()
  const locationOf = (memberId: number) => currentLocation(gameStatusByMember.get(memberId), now)
  // GearScore de cada membro (soma dos equipamentos atuais); 0 = sem equipamento pontuado.
  const { data: equipment = [] } = useEquipment()
  const gearScoreByMember = new Map<number, number>()
  for (const e of equipment) if (e.gearScore?.total) gearScoreByMember.set(e.memberId, e.gearScore.total)
  const cerberusLaunched = useCerberusLaunched()
  const { data: discord } = useDiscordMembers()
  const discordBySiteId = new Map<number, DiscordMember>()
  discord?.groups.forEach((g) =>
    g.members.forEach((m) => m.siteMemberId && discordBySiteId.set(m.siteMemberId, m)),
  )
  const { hasRole, user } = useAuthContext()
  const canManageMembers = hasRole('ADMINISTRADOR') // Líder ranks above Administrador
  // Right-click on a member: Discord shortcuts for everyone; Discord link and sale block for staff.
  const [memberMenu, setMemberMenu] = useState<MemberMenuState | null>(null)
  const openMemberMenu = (e: React.MouseEvent, member: Member) => {
    e.preventDefault()
    setMemberMenu({ member, x: e.clientX, y: e.clientY })
  }
  const closeMemberMenu = useCallback(() => setMemberMenu(null), [])
  const [rankPage, setRankPage] = useState(0)
  // Ordenação do ranking: por pontos (padrão), level, EXP do jogo ou GearScore.
  const [sortKey, setSortKey] = useState<SortKey>('points')
  const [sortDir, setSortDir] = useState<'desc' | 'asc'>('desc')
  const [rankSearch, setRankSearch] = useState('')
  const [sellFilter, setSellFilter] = useState<SellBlockFilter>('all')

  const sortValue = (memberId: number, points: number): number | null => {
    if (sortKey === 'points') return points
    if (sortKey === 'gearScore') return gearScoreByMember.get(memberId) ?? null
    const s = gameStatusByMember.get(memberId)
    if (!s) return null // sem status de jogo vai sempre para o fim
    return sortKey === 'gameLevel' ? s.level : s.expPercent
  }
  const toggleSort = (key: SortKey) => {
    if (sortKey === key) setSortDir((d) => (d === 'desc' ? 'asc' : 'desc'))
    else {
      setSortKey(key)
      setSortDir('desc')
    }
    setRankPage(0)
  }
  const sortArrow = (key: SortKey) =>
    sortKey === key ? (sortDir === 'desc' ? ' ▼' : ' ▲') : ''

  // Side column ends exactly where the ranking panel ends; últimos pontos fills it.
  const sideRef = useRef<HTMLDivElement>(null)
  const rankingRef = useRef<HTMLDivElement>(null)
  const sideHeight = useMatchBottom(sideRef, rankingRef, !isLoading)

  const sortedRanking = [...ranking].sort((a, b) => {
    const av = sortValue(a.id, a.points)
    const bv = sortValue(b.id, b.points)
    if (av === null && bv === null) return 0
    if (av === null) return 1 // sem dado por último, independente da direção
    if (bv === null) return -1
    let diff = bv - av // base: maior primeiro
    // Mesmo level: desempata pela EXP (quem tem mais % fica na frente).
    if (diff === 0 && sortKey === 'gameLevel') {
      diff = gameStatusByMember.get(b.id)!.expPercent - gameStatusByMember.get(a.id)!.expPercent
    }
    return sortDir === 'desc' ? diff : -diff
  })
  const top3 = sortedRanking.slice(0, 3)
  // Busca por nick (ou nome no Discord) e filtro de bloqueio de venda: mostram só quem bate, na
  // ordem do ranking, sem pódio.
  const searchTerm = rankSearch.trim().toLocaleLowerCase('pt-BR')
  const filtering = !!searchTerm || sellFilter !== 'all'
  const ranked = sortedRanking.map((member) => ({ member }))
  const rest = filtering
    ? ranked.filter(
        ({ member }) =>
          (sellFilter === 'all' || (sellFilter === 'blocked') === !!sellBlockedUntil(member)) &&
          (!searchTerm ||
            [member.nickname, discordBySiteId.get(member.id)?.name].some((name) =>
              name?.toLocaleLowerCase('pt-BR').includes(searchTerm),
            )),
      )
    : ranked.slice(3)

  // Rows per page follow the viewport height so the dashboard never scrolls; extra rows paginate.
  const rankListRef = useRef<HTMLUListElement>(null)
  const rankPaginationRef = useRef<HTMLDivElement>(null)
  const { perPage: rankPerPage, rowHeight: rankRowHeight } = useFitRows({
    listRef: rankListRef,
    paginationRef: rankPaginationRef,
    totalRows: rest.length,
    fallbackRowHeight: RANK_ROW_HEIGHT,
    fallbackPaginationHeight: PAGINATION_HEIGHT,
  })
  const rankTotalPages = Math.max(1, Math.ceil(rest.length / rankPerPage))
  // A taller window can leave the current page past the end — clamp instead of showing nothing.
  const currentRankPage = Math.min(rankPage, rankTotalPages - 1)
  const pagedRest = rest.slice(currentRankPage * rankPerPage, (currentRankPage + 1) * rankPerPage)
  // Last page of several: pad with blank rows so the table keeps its height and the
  // pagination stays put.
  const emptyRankRows = rankTotalPages > 1 ? Math.max(0, rankPerPage - pagedRest.length) : 0

  if (isLoading) return <p>Carregando ranking...</p>

  return (
    <div className="dashboard">
      {/* Gone once the server opens; the columns move up into its space. */}
      {!cerberusLaunched && (
        <div className="dashboard__cerberus">
          <CerberusCountdown compact />
        </div>
      )}

      {/* ── Left: Ranking ─────────────────────────────────────────────────── */}
      <div className="dashboard__col dashboard__col--ranking" ref={rankingRef}>
      <Panel
        title="Ranking"
        code={`${ranking.length} MBR`}
        actions={
          <div className="ranking-filters">
            <select
              className="ranking-filter"
              aria-label="Filtrar por bloqueio de venda"
              value={sellFilter}
              onChange={(e) => {
                setSellFilter(e.target.value as SellBlockFilter)
                setRankPage(0)
              }}
            >
              <option value="all">Todas as contas</option>
              <option value="blocked">Bloqueadas p/ venda</option>
              <option value="unblocked">Não bloqueadas</option>
            </select>
            <input
              type="search"
              className="ranking-search"
              placeholder="Buscar membro"
              aria-label="Buscar membro no ranking"
              value={rankSearch}
              onChange={(e) => {
                setRankSearch(e.target.value)
                setRankPage(0)
              }}
            />
          </div>
        }
      >
        {!filtering && (
          <div className="podium">
            {top3.map((member, index) => {
              const place = index + 1
              const status = gameStatusByMember.get(member.id)
              const gearScore = gearScoreByMember.get(member.id)
              const isMe = member.id === user?.memberId
              return (
                <div
                  key={member.id}
                  className={`podium__card podium__card--${place}${isMe ? ' podium__card--me' : ''}`}
                  onContextMenu={(e) => openMemberMenu(e, member)}
                >
                  <span className="podium__class" title={member.className ?? undefined}>
                    <MemberAvatar member={member} discord={discordBySiteId.get(member.id)} className="podium__avatar" />
                    {sellBlockedUntil(member) && <SellLock className="podium__lock" until={sellBlockedUntil(member)!} />}
                  </span>
                  <div className="podium__info">
                    <span className="podium__rank">
                      #{place}
                      {isMe && <span className="podium__me">Você</span>}
                    </span>
                    <Link to={`/profile/${member.id}`} className="podium__name">
                      {member.nickname}
                    </Link>
                    <span
                      className="podium__meta"
                      title={status ? `EXP ${status.expPercent.toFixed(2)}%` : undefined}
                    >
                      {status ? `Lv ${status.level}` : 'Lv —'} · GS {gearScore != null ? gearScore.toLocaleString('pt-BR') : '—'}
                    </span>
                    {locationOf(member.id) && (
                      <span className="podium__where" title="Mapa e região no jogo agora">
                        {locationOf(member.id)}
                      </span>
                    )}
                    <span className="podium__points">{member.points.toLocaleString('pt-BR')}</span>
                  </div>
                </div>
              )
            })}
          </div>
        )}

        {filtering && rest.length === 0 && (
          <p className="dashboard__empty">Nenhum membro encontrado.</p>
        )}

        {rest.length > 0 && (
          <div className="ranking-rest">
            <div className="ranking-list__head">
              <span className="ranking-list__head-icon" />
              <span>Membro</span>
              <span
                className={`ranking-list__sort${sortKey === 'gameLevel' ? ' ranking-list__sort--active' : ''}`}
                title="Level do personagem no jogo — clique para ordenar"
                onClick={() => toggleSort('gameLevel')}
              >
                Lv{sortArrow('gameLevel')}
              </span>
              <span
                className={`ranking-list__sort${sortKey === 'gameExp' ? ' ranking-list__sort--active' : ''}`}
                title="Experiência no jogo — clique para ordenar"
                onClick={() => toggleSort('gameExp')}
              >
                EXP{sortArrow('gameExp')}
              </span>
              <span
                className={`ranking-list__sort${sortKey === 'gearScore' ? ' ranking-list__sort--active' : ''}`}
                title="GearScore dos equipamentos atuais — clique para ordenar"
                onClick={() => toggleSort('gearScore')}
              >
                GS{sortArrow('gearScore')}
              </span>
              <span
                className={`ranking-list__sort${sortKey === 'points' ? ' ranking-list__sort--active' : ''}`}
                title="Pontos da guilda — clique para ordenar"
                onClick={() => toggleSort('points')}
              >
                Pontos{sortArrow('points')}
              </span>
            </div>
            <ul
              className="ranking-list"
              ref={rankListRef}
              style={rankRowHeight ? ({ '--rank-row-h': `${rankRowHeight}px` } as React.CSSProperties) : undefined}
            >
              {pagedRest.map(({ member: m }) => (
                <li key={m.id} onContextMenu={(e) => openMemberMenu(e, m)}>
                  <MemberAvatar member={m} discord={discordBySiteId.get(m.id)} className="ranking-list__avatar" />
                  <span className="ranking-list__member">
                    <span className="ranking-list__who">
                      <Link to={`/profile/${m.id}`} className="ranking-list__name">
                        {m.nickname}
                      </Link>
                      {sellBlockedUntil(m) && <SellLock className="ranking-list__lock" until={sellBlockedUntil(m)!} />}
                      <DiscordTag member={discordBySiteId.get(m.id)} className="ranking-list__discord" />
                    </span>
                    {locationOf(m.id) && (
                      <span className="ranking-list__where" title="Mapa e região no jogo agora">
                        {locationOf(m.id)}
                      </span>
                    )}
                  </span>
                  <span className="ranking-list__game-lv">
                    {gameStatusByMember.get(m.id)?.level ?? '—'}
                  </span>
                  <span className="ranking-list__game-exp">
                    {gameStatusByMember.has(m.id)
                      ? `${gameStatusByMember.get(m.id)!.expPercent.toFixed(2)}%`
                      : '—'}
                  </span>
                  <span className="ranking-list__gs">{gearScoreByMember.get(m.id) ?? '—'}</span>
                  <span className="ranking-list__pts">{m.points}</span>
                </li>
              ))}
              {Array.from({ length: emptyRankRows }, (_, i) => (
                <li key={`empty-${i}`} className="ranking-list__row--empty" aria-hidden="true">
                  <span className="ranking-list__empty-icon" />
                  <span className="ranking-list__empty-name">—</span>
                </li>
              ))}
            </ul>
            <Pagination
              ref={rankPaginationRef}
              page={currentRankPage}
              total={rankTotalPages}
              onChange={setRankPage}
            />
          </div>
        )}
      </Panel>
      </div>

      {/* ── Right: últimos pontos, da altura do ranking (pagina quando não cabe) ─── */}
      <div className="dashboard__side" ref={sideRef} style={sideHeight ? { height: sideHeight } : undefined}>
        <RecentPointsPanel className="dashboard__col dashboard__col--points" />
      </div>

      {memberMenu && (
        <MemberContextMenu
          key={memberMenu.member.id}
          menu={memberMenu}
          discordMember={discordBySiteId.get(memberMenu.member.id)}
          discordMembers={discord?.groups.flatMap((g) => g.members) ?? []}
          canManage={canManageMembers}
          onClose={closeMemberMenu}
        />
      )}
    </div>
  )
}
