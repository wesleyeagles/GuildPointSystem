import type { ReactNode } from 'react'
import type { PartyLfgEntry } from '@/Features/Party/Party.types'
import { formatWaiting, sortByWaiting } from '@/Features/Party/utils/partyWait'
import { setPartyDragData } from '@/Features/Party/utils/partyDrag'
import { MemberClassIcon } from '@/Shared/ui/components/MemberClassIcon/MemberClassIcon'
import { PartyAfkControl } from './PartyAfkControl'
import { useServerNow } from '@/Shared/utils/useServerNow'
import { SCHEDULE_TIME_ZONE } from '@/Shared/utils/scheduleTimeZone'
import { SellLock } from '@/Features/Dashboard/components/MemberContextMenu'
import { sellBlockedUntil } from '@/Features/Dashboard/utils/sellBlock'

// Timers tick here (server clock, once a second) so only the waiting list re-renders, not the page.

export function WaitingTime({ since }: { since: string }) {
  const now = useServerNow()
  return <>{formatWaiting(now - new Date(since).getTime())}</>
}

export function PartyLfgList({
  entries,
  viewerId,
  canDrag,
  actions,
  queueActions,
  canManageAfk = false,
  search = '',
}: {
  entries: PartyLfgEntry[]
  viewerId?: number
  canDrag: boolean
  /** Extra buttons at the end of a card (e.g. admin "remove from the list"). */
  actions?: (entry: PartyLfgEntry) => ReactNode
  queueActions?: (entry: PartyLfgEntry) => ReactNode
  canManageAfk?: boolean
  /** Shows only nicknames containing this text; cards keep their queue order. */
  search?: string
}) {
  const now = useServerNow()
  const term = search.trim().toLocaleLowerCase('pt-BR')
  const ordered = sortByWaiting(entries)
    .filter((entry) => !term || entry.nickname.toLocaleLowerCase('pt-BR').includes(term))

  if (ordered.length === 0) {
    return <p className="party-page__empty party-page__empty--panel">Nenhum membro encontrado.</p>
  }

  return (
    <ol className="party-lfg-grid">
      {ordered.map((entry) => {
        const draggable = canDrag && entry.memberId !== viewerId
        const isMe = entry.memberId === viewerId
        const sellBlock = sellBlockedUntil(entry)
        return (
          <li
            key={entry.memberId}
            className={[
              'party-lfg-item',
              draggable ? 'party-lfg-item--draggable' : '',
              isMe ? 'party-lfg-item--me' : '',
            ]
              .filter(Boolean)
              .join(' ')}
            draggable={draggable}
            onDragStart={(e) => {
              if (!draggable) return
              setPartyDragData(e, { memberId: entry.memberId, fromPartyId: null, nickname: entry.nickname })
            }}
          >
            {/* Top: icon, nick and Lv/EXP on the left, remove on the right. */}
            <div className="party-lfg-item__main">
              {entry.classImageUrl ? (
                <MemberClassIcon classImageUrl={entry.classImageUrl} classLabel={entry.className ?? ''} size="md" />
              ) : (
                <span className="party-card__roster-fallback party-card__roster-fallback--muted party-lfg-item__fallback">?</span>
              )}
              <div className="party-lfg-item__who">
                <span className="party-lfg-item__nick">
                  <strong className="party-lfg-item__name" title={entry.nickname}>
                    {entry.nickname}
                    {isMe ? ' (você)' : ''}
                  </strong>
                  {sellBlock && <SellLock className="party-lfg-item__lock" until={sellBlock} />}
                </span>
                <span className="party-lfg-item__level" title={`Lv ${entry.level}${entry.expPercent != null ? ` · ${entry.expPercent.toFixed(2)}% XP` : ''}`}>
                  Lv {entry.level}
                  {entry.expPercent != null ? ` · ${entry.expPercent.toFixed(2)}%` : ''}
                </span>
              </div>
              {actions && (
                <div className="party-lfg-item__actions" draggable={false} onDragStart={(e) => e.stopPropagation()}>
                  {actions(entry)}
                </div>
              )}
            </div>
            {/* Bottom: queue buttons on the left; points, "no answer" mark and the wait clock on the right. */}
            <div className="party-lfg-item__footer">
              {queueActions && (
                <div className="party-lfg-item__tools" draggable={false} onDragStart={(e) => e.stopPropagation()}>
                  {queueActions(entry)}
                </div>
              )}
              <span className="party-lfg-item__badges">
                {(entry.priorityPoints ?? 0) > 0 && <span className="party-lfg-item__points"
                  title="Prioridade da fila. Zera ao entrar em uma PT.">{entry.priorityPoints} pts</span>}
                {entry.lastUnansweredAt && <span className="party-lfg-item__unanswered" role="img"
                  aria-label="Chamado, não respondeu"
                  title={`Chamado, não respondeu · ${new Date(entry.lastUnansweredAt).toLocaleString('pt-BR', { timeZone: SCHEDULE_TIME_ZONE })}`} />}
              </span>
              <span
                className="party-lfg-item__wait"
                title={`Na lista desde ${new Date(entry.createdAt).toLocaleTimeString('pt-BR', { timeZone: SCHEDULE_TIME_ZONE })}`}
              >
                {formatWaiting(now - new Date(entry.createdAt).getTime())}
              </span>
            </div>
            {entry.afkSince && <PartyAfkControl memberId={entry.memberId} nickname={entry.nickname} queued
              canControl={canManageAfk || entry.memberId === viewerId} />}
          </li>
        )
      })}
    </ol>
  )
}
