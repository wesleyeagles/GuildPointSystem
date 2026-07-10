import { useState } from 'react'
import { Link } from 'react-router-dom'
import { useMemberRanking } from '@/Domain/Member/hooks/useMembers'
import { useActiveEvents, useClaimEvent } from '@/Domain/Event/hooks/useEvents'
import { Button } from '@/Shared/ui/components/Button/Button'
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
  const [passwords, setPasswords] = useState<Record<number, string>>({})
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
    await claimEvent.mutateAsync({ id: eventId, password })
    setPasswords((p) => ({ ...p, [eventId]: '' }))
  }

  if (isLoading) return <p>Carregando ranking...</p>

  return (
    <div className="dashboard">
      {/* ── Left: Ranking ─────────────────────────────────────────────────── */}
      <section className="dashboard__col dashboard__col--ranking">
        <h2>Ranking</h2>

        <div className="podium">
          {[1, 0, 2].map((rankIdx) => {
            const member = top3[rankIdx]
            if (!member) return null
            const place = rankIdx + 1
            return (
              <div key={member.id} className={`podium__slot podium__slot--${place}`}>
                <span className="podium__place">{place}º</span>
                <Link to={`/profile/${member.id}`} className="podium__name podium__name--link">
                  {member.nickname}
                </Link>
                <span className="podium__points">{member.points} pts</span>
              </div>
            )
          })}
        </div>

        {rest.length > 0 && (
          <div className="ranking-rest">
            <ul className="ranking-list">
              {pagedRest.map((m, i) => (
                <li key={m.id}>
                  <span>{rankPage * RANK_PER_PAGE + i + 4}º</span>
                  <Link to={`/profile/${m.id}`} className="ranking-list__name">
                    {m.nickname}
                  </Link>
                  <span>{m.points} pts</span>
                </li>
              ))}
            </ul>
            <Pagination page={rankPage} total={rankTotalPages} onChange={setRankPage} />
          </div>
        )}
      </section>

      {/* ── Right: Active Events ──────────────────────────────────────────── */}
      <section className="dashboard__col dashboard__col--events">
        <h2>Eventos Ativos</h2>

        {unclaimedEvents.length === 0 ? (
          <p className="dashboard__empty">Nenhum evento ativo no momento.</p>
        ) : (
          <div className="events-paged">
            <ul className="event-list">
              {pagedEvents.map((event) => (
                <li key={event.id} className="event-list__item">
                  <div className="event-list__info">
                    <strong>{event.objectiveName}</strong>
                    <span className="event-list__pts">{event.points} pts</span>
                    <small>Expira: {new Date(event.expiresAt).toLocaleString()}</small>
                  </div>
                  <div className="event-list__claim">
                    <input
                      type="password"
                      maxLength={4}
                      placeholder="Senha"
                      value={passwords[event.id] ?? ''}
                      onChange={(e) =>
                        setPasswords((p) => ({ ...p, [event.id]: e.target.value }))
                      }
                    />
                    <Button size="sm" onClick={() => handleClaim(event.id)} loading={claimEvent.isPending}>
                      Resgatar
                    </Button>
                  </div>
                </li>
              ))}
            </ul>
            <Pagination page={eventPage} total={eventTotalPages} onChange={setEventPage} />
          </div>
        )}
      </section>
    </div>
  )
}
