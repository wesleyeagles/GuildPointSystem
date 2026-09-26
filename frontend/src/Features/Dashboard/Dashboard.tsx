import { useState } from 'react'
import { Link } from 'react-router-dom'
import { useMemberRanking } from '@/Domain/Member/hooks/useMembers'
import { useActiveEvents, useClaimEvent } from '@/Domain/Event/hooks/useEvents'
import { Button } from '@/Shared/ui/components/Button/Button'
import { MemberClassIcon } from '@/Shared/ui/components/MemberClassIcon/MemberClassIcon'
import { Panel } from '@/Shared/ui/components/Panel/Panel'
import { useAppToast } from '@/Shared/ui/components/AppToast/AppToast'
import './Dashboard.styles.scss'

const RANK_PER_PAGE = 14
const EVENT_PER_PAGE = 4

interface PaginationProps {
  page: number
  total: number
  onChange: (page: number) => void
}

function Pagination({ page, total, onChange }: PaginationProps) {
  if (total <= 1) return null
  return (
    <div className="dash-pagination">
      <button
        className="dash-pagination__btn"
        onClick={() => onChange(Math.max(0, page - 1))}
        disabled={page === 0}
      >
        ‹
      </button>
      <span className="dash-pagination__info">
        {page + 1} <span className="dash-pagination__sep">/</span> {total}
      </span>
      <button
        className="dash-pagination__btn"
        onClick={() => onChange(Math.min(total - 1, page + 1))}
        disabled={page === total - 1}
      >
        ›
      </button>
    </div>
  )
}

export function DashboardPage() {
  const { data: ranking = [], isLoading } = useMemberRanking()
  const { data: events = [] } = useActiveEvents()
  const claimEvent = useClaimEvent()
  const { showToast } = useAppToast()
  const [passwords, setPasswords] = useState<Record<number, string>>({})
  const [claimErrors, setClaimErrors] = useState<Record<number, string>>({})
  const [rankPage, setRankPage] = useState(0)
  const [eventPage, setEventPage] = useState(0)

  const top3 = ranking.slice(0, 3)
  const rest = ranking.slice(3)
  const rankTotalPages = Math.ceil(rest.length / RANK_PER_PAGE)
  const pagedRest = rest.slice(rankPage * RANK_PER_PAGE, (rankPage + 1) * RANK_PER_PAGE)

  const unclaimedEvents = events.filter((e) => !e.claimedByMe)
  const eventTotalPages = Math.ceil(unclaimedEvents.length / EVENT_PER_PAGE)
  const pagedEvents = unclaimedEvents.slice(
    eventPage * EVENT_PER_PAGE,
    (eventPage + 1) * EVENT_PER_PAGE,
  )

  const handleClaim = async (eventId: number) => {
    const password = passwords[eventId] ?? ''
    if (password.length !== 4) {
      setClaimErrors((prev) => ({
        ...prev,
        [eventId]: 'A senha deve ter exatamente 4 caracteres.',
      }))
      return
    }
    const event = events.find((e) => e.id === eventId)
    setClaimErrors((prev) => {
      const next = { ...prev }
      delete next[eventId]
      return next
    })

    try {
      await claimEvent.mutateAsync({ id: eventId, password })
      setPasswords((p) => ({ ...p, [eventId]: '' }))
      showToast(
        event
          ? `Evento "${event.objectiveName}" resgatado! +${event.points} pts`
          : 'Evento resgatado com sucesso!',
        'success',
      )
    } catch (err) {
      const message = err instanceof Error ? err.message : 'Erro ao resgatar evento'
      setClaimErrors((prev) => ({ ...prev, [eventId]: message }))
    }
  }

  if (isLoading) return <p>Carregando ranking...</p>

  return (
    <div className="dashboard">
      <Link to="/server-info" className="dashboard__cerberus-link">
        Informações do servidor Cerberus (tópicos traduzidos)
      </Link>

      {/* ── Left: Ranking ─────────────────────────────────────────────────── */}
      <Panel
        title="Ranking"
        code={`${ranking.length} MBR`}
        className="dashboard__col dashboard__col--ranking"
      >
        <div className="podium">
          {[1, 0, 2].map((rankIdx) => {
            const member = top3[rankIdx]
            if (!member) return null
            const place = rankIdx + 1
            return (
              <div key={member.id} className={`podium__slot podium__slot--${place}`}>
                <div className="podium__card">
                  <MemberClassIcon
                    classImageUrl={member.classImageUrl}
                    classLabel={member.className}
                    size="md"
                  />
                  <Link to={`/profile/${member.id}`} className="podium__name podium__name--link">
                    {member.nickname}
                  </Link>
                  <span className="podium__level">Lv {member.level ?? 1}</span>
                  <span className="podium__points">{member.points}</span>
                </div>
                <div className="podium__pedestal">
                  <span className="podium__place">{place}</span>
                </div>
              </div>
            )
          })}
        </div>

        {rest.length > 0 && (
          <div className="ranking-rest">
            <div className="ranking-list__head" aria-hidden="true">
              <span>#</span>
              <span className="ranking-list__head-icon" />
              <span>Membro</span>
              <span>Lv</span>
              <span>Pontos</span>
            </div>
            <ul className="ranking-list">
              {pagedRest.map((m, i) => (
                <li key={m.id}>
                  <span className="ranking-list__pos">
                    {String(rankPage * RANK_PER_PAGE + i + 4).padStart(2, '0')}
                  </span>
                  <MemberClassIcon
                    classImageUrl={m.classImageUrl}
                    classLabel={m.className}
                  />
                  <Link to={`/profile/${m.id}`} className="ranking-list__name">
                    {m.nickname}
                  </Link>
                  <span className="ranking-list__level">{m.level ?? 1}</span>
                  <span className="ranking-list__pts">{m.points}</span>
                </li>
              ))}
            </ul>
            <Pagination page={rankPage} total={rankTotalPages} onChange={setRankPage} />
          </div>
        )}
      </Panel>

      {/* ── Right: Active Events ──────────────────────────────────────────── */}
      <Panel
        title="Eventos Ativos"
        variant="amber"
        code={`${unclaimedEvents.length} OPS`}
        className="dashboard__col dashboard__col--events"
      >
        {unclaimedEvents.length === 0 ? (
          <p className="dashboard__empty">Nenhum evento ativo no momento.</p>
        ) : (
          <div className="events-paged">
            <ul className="event-list">
              {pagedEvents.map((event) => (
                <li key={event.id} className="event-list__item">
                  <div className="event-list__info">
                    <strong>{event.objectiveName}</strong>
                    <div className="event-list__meta">
                      <span className="event-list__pts">+{event.points} pts</span>
                      <small>
                        Expira{' '}
                        {new Date(event.expiresAt).toLocaleString('pt-BR', {
                          day: '2-digit',
                          month: '2-digit',
                          hour: '2-digit',
                          minute: '2-digit',
                        })}
                      </small>
                    </div>
                  </div>
                  <div className="event-list__claim-wrap">
                    <div className="event-list__claim">
                      <input
                        type="password"
                        maxLength={4}
                        placeholder="Senha"
                        value={passwords[event.id] ?? ''}
                        onChange={(e) => {
                          const value = e.target.value
                          setPasswords((p) => ({ ...p, [event.id]: value }))
                          if (claimErrors[event.id]) {
                            setClaimErrors((prev) => {
                              const next = { ...prev }
                              delete next[event.id]
                              return next
                            })
                          }
                        }}
                      />
                      <Button
                        size="sm"
                        onClick={() => handleClaim(event.id)}
                        loading={claimEvent.isPending && claimEvent.variables?.id === event.id}
                      >
                        Resgatar
                      </Button>
                    </div>
                    {claimErrors[event.id] && (
                      <p className="event-list__error">{claimErrors[event.id]}</p>
                    )}
                  </div>
                </li>
              ))}
            </ul>
            <Pagination page={eventPage} total={eventTotalPages} onChange={setEventPage} />
          </div>
        )}
      </Panel>
    </div>
  )
}
