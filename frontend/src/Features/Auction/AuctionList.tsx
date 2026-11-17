import { Fragment, useCallback, useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import {
  serverNow,
  useAuctionAdminAction,
  useAuctions,
  useConfirmAuctionDelivery,
  useCreateAuction,
  useDeleteAuction,
  useParticipatingAuctions,
  useStartAuctionBatch,
} from '@/Domain/Auction/hooks/useAuction'
import { useDiscordBySiteMember, type DiscordMember } from '@/Domain/Discord/hooks/useDiscordMembers'
import { formatAuctionItems, isAuctionFinished } from '@/Domain/types/models'
import { relicIdFromGameCode, relicUpgradeTexts } from '@/Domain/Equipment/utils/relicEffects'
import { useAppToast } from '@/Shared/ui/components/AppToast/AppToast'
import { ConfirmDialog } from '@/Shared/ui/components/ConfirmDialog/ConfirmDialog'
import { useAuthContext } from '@/Features/Auth/contexts/AuthContext'
import { AuctionItemPicker } from '@/Features/Auction/components/AuctionItemPicker/AuctionItemPicker'
import { AuctionItemIcon } from '@/Features/Auction/components/AuctionItemIcon/AuctionItemIcon'
import { Button } from '@/Shared/ui/components/Button/Button'
import { Panel } from '@/Shared/ui/components/Panel/Panel'
import type { Auction } from '@/Domain/types/models'
import { formatStartsAt, formatTime, pausedSeconds, useCountdown } from '@/Features/Auction/hooks/useCountdown'
import { useAuctionDraftInput } from '@/Domain/Auction/hooks/useAuctionDraftInput'
import { ItemQuantity } from '@/Shared/ui/components/ItemQuantity/ItemQuantity'
import { useGameMail } from '@/Shared/hooks/useGameMail'
import { useServerNow } from '@/Shared/utils/useServerNow'
import { canResolveAuctionMailAttachment, resolveAuctionMailAttachment } from '@/Domain/Auction/auctionMail'
import './Auction.styles.scss'

const DURATIONS = [
  { value: 1, label: '1 min' },
  { value: 5, label: '5 min' },
  { value: 15, label: '15 min' },
  { value: 30, label: '30 min' },
  { value: 60, label: '1 h' },
  { value: 180, label: '3 h' },
  { value: 360, label: '6 h' },
  { value: 720, label: '12 h' },
  { value: 1440, label: '24 h' },
] as const

type ItemEntry = { itemId: number; quantity: number }

type StartMode = 'now' | 'at' | 'later'
type DeadlineDay = 'today' | 'tomorrow'

const MAX_LOTS = 30

/** Value for <input type="datetime-local"> in the browser's time zone. */
function toLocalInput(d: Date) {
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`
}

const emptyEntry = (): ItemEntry => ({ itemId: 0, quantity: 1 })

const auctionDate = new Intl.DateTimeFormat('pt-BR', {
  dateStyle: 'short',
  timeStyle: 'medium',
  timeZone: 'America/Sao_Paulo',
})

const brasiliaDay = new Intl.DateTimeFormat('sv-SE', { timeZone: 'America/Sao_Paulo' })

function at2150(now: number, day: DeadlineDay) {
  const date = brasiliaDay.format(now + (day === 'tomorrow' ? 86_400_000 : 0))
  return new Date(`${date}T21:50:00-03:00`)
}

function todayAt2150(now: number) {
  const end = at2150(now, 'today')
  return end.getTime() > now ? end.toISOString() : null
}

const STATUS_LABEL: Record<string, string> = {
  SCHEDULED: 'Em breve',
  PAUSED: 'Pausado',
  OPEN: 'Aberto',
  DOLE: 'DOLE',
  TIE_BREAK: 'Empate',
  CLOSED: 'Encerrado',
  CANCELLED: 'Cancelado',
}

function AuctionListTimer({ endsAt }: { endsAt: string }) {
  const remaining = useCountdown(endsAt)
  return <span className="auction-list__timer">{formatTime(remaining)}</span>
}

/** Countdown for running auctions, frozen clock when paused, start time when not started. */
function AuctionListTime({ auction }: { auction: Auction }) {
  if (auction.status === 'OPEN' || auction.status === 'DOLE') return <AuctionListTimer endsAt={auction.endsAt} />
  if (auction.status === 'PAUSED') {
    return <span className="auction-list__timer">{formatTime(pausedSeconds(auction.pausedRemainingMs))}</span>
  }
  if (auction.status === 'SCHEDULED') {
    return (
      <span className="auction-list__timer auction-list__timer--start">
        {auction.startsAt ? formatStartsAt(auction.startsAt) : 'Início manual'}
      </span>
    )
  }
  return null
}

/** Site profile link, with the Discord name (in the member's role color) in parentheses. */
function MemberLink({ id, nickname, discord }: { id: number; nickname: string; discord?: DiscordMember }) {
  return (
    <Link to={`/profile/${id}`} className="auction-list__member">
      {nickname}
      {discord && (
        <>
          {' ('}
          <span style={discord.color ? { color: discord.color } : undefined}>{discord.name}</span>
          {')'}
        </>
      )}
    </Link>
  )
}

/** Active: who is leading (or how many are tied). Finished: the winner. */
function AuctionSubtitle({ auction, discordBySite }: { auction: Auction; discordBySite: Map<number, DiscordMember> }) {
  const finished = isAuctionFinished(auction.status)
  if (auction.status === 'SCHEDULED') {
    return <small>{auction.startsAt ? `Começa ${formatStartsAt(auction.startsAt)}` : 'Aguardando a administração iniciar'}</small>
  }
  if (!finished && auction.tied && auction.tiedCount > 1) {
    if (auction.tiedCount > 3 || auction.tiedMemberIds.length !== auction.tiedCount) {
      return <small>{auction.tiedCount} empatados</small>
    }
    return (
      <small>
        Empatados:{' '}
        {auction.tiedMemberIds.map((tiedId, i) => (
          <Fragment key={tiedId}>
            {i > 0 && ', '}
            <MemberLink
              id={tiedId}
              nickname={auction.tiedMemberNicknames[i]}
              discord={discordBySite.get(tiedId)}
            />
          </Fragment>
        ))}
      </small>
    )
  }
  const id = finished ? auction.winnerId : auction.leaderId
  const nickname = finished ? auction.winnerNickname : auction.leaderNickname
  if (id == null || !nickname) return finished ? null : <small>Sem lances</small>
  return (
    <small>
      {finished ? 'Vencedor: ' : 'Vencendo: '}
      <MemberLink id={id} nickname={nickname} discord={discordBySite.get(id)} />
    </small>
  )
}

/** Com o maior lance (sozinho ou empatado). */
function isLeading(auction: Auction, memberId: number | undefined) {
  if (memberId == null) return false
  return auction.leaderId === memberId || (auction.tiedMemberIds ?? []).includes(memberId)
}

/** Abas acima de "Em andamento": todos e um por leilão em que o membro deu lance (coroa = ganhando). */
function AuctionTabs({ auctions, memberId, selected, onSelect, total }: {
  auctions: Auction[]
  memberId: number | undefined
  selected: number | 'all'
  onSelect: (tab: number | 'all') => void
  total: number
}) {
  if (auctions.length === 0) return null
  return (
    <div className="auction-tabs" role="tablist" aria-label="Leilões em que estou participando">
      <button type="button" role="tab" aria-selected={selected === 'all'}
        className={`auction-tabs__tab${selected === 'all' ? ' is-active' : ''}`} onClick={() => onSelect('all')}>
        Todos <span className="auction-tabs__count">{total}</span>
      </button>
      {auctions.map((auction) => {
        const leading = isLeading(auction, memberId)
        const name = formatAuctionItems(auction.items) || `Leilão #${auction.id}`
        return (
          <button key={auction.id} type="button" role="tab" aria-selected={selected === auction.id}
            title={leading ? 'Você está ganhando este leilão' : 'Seu lance foi superado'}
            className={`auction-tabs__tab${selected === auction.id ? ' is-active' : ''}${leading ? ' is-leading' : ' is-outbid'}`}
            onClick={() => onSelect(auction.id)}>
            {leading
              ? <span className="auction-tabs__crown" role="img" aria-label="Ganhando">👑</span>
              : <span className="auction-tabs__outbid" aria-label="Superado">▼</span>}
            <AuctionItemIcon item={auction.items[0]} size={22} />
            <span className="auction-tabs__name">#{String(auction.id).padStart(3, '0')} {name}</span>
            <span className="auction-tabs__bid">{auction.currentBid}</span>
          </button>
        )
      })}
    </div>
  )
}

function AuctionListSection({
  title,
  auctions,
  emptyText,
  onCopy,
  onDelete,
  deletingId,
  onStart,
  startingId,
  actions,
  deliveryActions,
}: {
  title: string
  auctions: Auction[]
  onCopy?: (auction: Auction) => void
  emptyText?: string
  /** Shown for finished and not-yet-started auctions, to Líder/Administrador. */
  onDelete?: (auction: Auction) => void
  deletingId?: number | null
  /** Opens a not-yet-started auction now (Líder/Administrador). */
  onStart?: (auction: Auction) => void
  startingId?: number | null
  actions?: React.ReactNode
  deliveryActions?: (auction: Auction) => React.ReactNode
}) {
  const discordBySite = useDiscordBySiteMember()

  if (auctions.length === 0 && !emptyText) return null

  return (
    <Panel title={title} code={`${auctions.length} LOT`} actions={actions} flush>
      {auctions.length === 0 ? (
        <p className="auction-page__empty">{emptyText}</p>
      ) : (
        <ul className="auction-list">
          {auctions.map((auction) => (
            <li
              key={auction.id}
              className={`auction-list__item${isAuctionFinished(auction.status) ? ' auction-list__item--closed' : ''}`}
            >
              <span className="auction-list__lot">#{String(auction.id).padStart(3, '0')}</span>
              <AuctionItemIcon item={auction.items[0]} />
              <div className="auction-list__info">
                {auction.items.length ? auction.items.map((item) => (
                  <span className="auction-list__item-heading" key={item.itemId}>
                    <strong className="auction-list__item-name">{item.itemName}</strong>
                    <ItemQuantity quantity={item.quantity} />
                  </span>
                )) : <strong>{`Leilão #${auction.id}`}</strong>}
                <AuctionRelicEffects auction={auction} />
                <AuctionSubtitle auction={auction} discordBySite={discordBySite} />
              </div>
              <span
                className={`auction-list__status auction-list__status--${auction.status.toLowerCase()}`}
              >
                {auction.deliveredAt ? 'Enviado' : STATUS_LABEL[auction.status] ?? auction.status}
              </span>
              <AuctionListTime auction={auction} />
              <span className="auction-list__bid">{auction.currentBid}</span>
              <div className="auction-list__actions">
                <Link to={`/auctions/${auction.id}`}>
                  <Button variant="secondary" size="sm">
                    Ver
                  </Button>
                </Link>
                {deliveryActions?.(auction)}
                {onStart && auction.status === 'SCHEDULED' && (
                  <Button size="sm" loading={startingId === auction.id} onClick={() => onStart(auction)}>
                    Iniciar
                  </Button>
                )}
                {onCopy && (auction.status === 'OPEN' || auction.status === 'SCHEDULED') && (
                  <Button type="button" variant="secondary" size="sm" onClick={() => onCopy(auction)}>
                    {auction.status === 'SCHEDULED' ? 'Duplicar' : 'Copiar leilão'}
                  </Button>
                )}
                {onDelete && (isAuctionFinished(auction.status) || auction.status === 'SCHEDULED') && (
                  <Button
                    variant="danger"
                    size="sm"
                    loading={deletingId === auction.id}
                    onClick={() => onDelete(auction)}
                  >
                    Excluir
                  </Button>
                )}
              </div>
            </li>
          ))}
        </ul>
      )}
    </Panel>
  )
}

/** What each item in the lot gives: the base bonus of relics, the Special Effects of everything else. */
function AuctionRelicEffects({ auction }: { auction: Auction }) {
  const texts = auction.items.flatMap((item) => {
    const id = relicIdFromGameCode(item.gameIcon?.gameCode)
    return id === null ? item.effects ?? [] : relicUpgradeTexts(id)[0] ?? []
  })
  if (texts.length === 0) return null
  return <span className="auction-list__effects">{texts.join(' · ')}</span>
}

const lotOf = (auction: Auction) => `#${String(auction.id).padStart(3, '0')}`

export function AuctionListPage() {
  const { data: auctions = [], isLoading } = useAuctions()
  const createAuction = useCreateAuction()
  const deleteAuction = useDeleteAuction()
  const startAuction = useAuctionAdminAction('start')
  const startBatch = useStartAuctionBatch()
  const { showToast } = useAppToast()
  const { hasRole, user } = useAuthContext()
  const isAdmin = hasRole('ADMINISTRADOR')
  const confirmDelivery = useConfirmAuctionDelivery()
  const { canPrepareMail, preparingMail, prepareMail } = useGameMail(isAdmin, 'boi')
  const [pendingDeliveryId, setPendingDeliveryId] = useState<number | null>(null)
  const pendingDelivery = isAdmin ? auctions.find(({ id, status, winnerNickname, deliveredAt }) =>
    id === pendingDeliveryId && status === 'CLOSED' && winnerNickname && !deliveredAt) : undefined
  const { data: participatingIds = [] } = useParticipatingAuctions()
  const [tab, setTab] = useState<number | 'all'>('all')

  const [durationMinutes, setDurationMinutes] = useState(15)
  const [copiedEnd, setCopiedEnd] = useState<string | null>(null)
  const [deadlineDay, setDeadlineDay] = useState<DeadlineDay | null>()
  const now = useServerNow()
  const canCloseToday = todayAt2150(now) !== null
  const shownDeadlineDay = deadlineDay === undefined ? (canCloseToday ? 'today' : 'tomorrow') : deadlineDay
  const durationField = useRef<HTMLSelectElement>(null)
  const [startMode, setStartMode] = useState<StartMode>('now')
  const [startsAtInput, setStartsAtInput] = useState('')
  const [copiedStart, setCopiedStart] = useState<string | null>(null)
  const [entries, setEntries] = useState<ItemEntry[]>([emptyEntry()])
  const [lotCount, setLotCount] = useState('1')
  const [isCreating, setIsCreating] = useState(false)
  const [creationSummary, setCreationSummary] = useState('')
  const creating = useRef(false)
  const [pendingDelete, setPendingDelete] = useState<Auction | null>(null)
  const closeDelete = useCallback(() => setPendingDelete(null), [])

  const draftInput = useAuctionDraftInput(isAdmin, isCreating, (itemId) => {
    setEntries((previous) => {
      const matching = previous.findIndex((entry) => entry.itemId === itemId)
      const index = matching >= 0 ? matching : previous.findIndex((entry) => entry.itemId === 0)
      if (index < 0) return [...previous, { itemId, quantity: 1 }]
      return previous.map((entry, i) => i === index
        ? { itemId, quantity: matching >= 0 ? Math.max(0, entry.quantity) + 1 : 1 }
        : entry)
    })
  })

  const updateEntry = (index: number, patch: Partial<ItemEntry>) => {
    setEntries((prev) => prev.map((entry, i) => (i === index ? { ...entry, ...patch } : entry)))
  }

  const addEntry = () => setEntries((prev) => [...prev, emptyEntry()])

  const removeEntry = (index: number) => {
    setEntries((prev) => (prev.length <= 1 ? prev : prev.filter((_, i) => i !== index)))
  }

  const copyAuction = (auction: Auction) => {
    if (creating.current) return
    const scheduled = auction.status === 'SCHEDULED'
    if (!scheduled && Date.parse(auction.endsAt) <= serverNow()) {
      showToast('O horário de encerramento desse leilão já passou.', 'error')
      return
    }
    setDurationMinutes(auction.durationMinutes)
    const futureStart = scheduled && auction.startsAt && Date.parse(auction.startsAt) > serverNow()
      ? toLocalInput(new Date(auction.startsAt))
      : ''
    setStartMode(scheduled ? (futureStart ? 'at' : 'later') : 'now')
    setStartsAtInput(futureStart)
    setCopiedStart(futureStart ? auction.startsAt : null)
    setCopiedEnd(scheduled ? null : auction.endsAt)
    setDeadlineDay(null)
    setEntries(auction.items.length
      ? auction.items.map(({ itemId, quantity }) => ({ itemId, quantity }))
      : [emptyEntry()])
    durationField.current?.focus()
    durationField.current?.scrollIntoView?.({ block: 'center' })
  }

  const handleCreate = async (e: React.FormEvent) => {
    e.preventDefault()
    const validItems = entries.filter((entry) => entry.itemId > 0 && entry.quantity >= 1)
    const count = Number(lotCount)
    if (creating.current || validItems.length === 0 || !Number.isInteger(count) || count < 1 || count > MAX_LOTS) return
    const now = serverNow()
    const selectedDay = deadlineDay === undefined ? (todayAt2150(now) ? 'today' : 'tomorrow') : deadlineDay
    const endsAt = selectedDay ? at2150(now, selectedDay).toISOString() : copiedEnd
    if (endsAt && Date.parse(endsAt) <= now) {
      showToast('O horário de encerramento já passou. Escolha outra duração.', 'error')
      return
    }

    let startsAt: string | undefined
    if (startMode === 'at') {
      const when = new Date(copiedStart ?? startsAtInput)
      if (!startsAtInput || Number.isNaN(when.getTime()) || when.getTime() <= serverNow() + 30_000) {
        showToast('Escolha um horário de início com mais de 30 segundos de antecedência.', 'error')
        return
      }
      startsAt = when.toISOString()
    }

    const request = {
      durationMinutes,
      ...(copiedEnd ? { endsAt: copiedEnd } : endsAt ? { targetEndsAt: endsAt } : {}),
      items: validItems.map(({ itemId, quantity }) => ({ itemId, quantity })),
      startsAt,
      startLater: startMode === 'later' || undefined,
    }
    creating.current = true
    setIsCreating(true)
    setCreationSummary(count > 1 ? `Criando 0 de ${count} leilões…` : '')
    draftInput.suspend()
    let created = 0
    const uncheckedDeadlines: string[] = []
    const deadlineWarning = () => uncheckedDeadlines.length
      ? ` Confira o cronômetro dos lotes ${uncheckedDeadlines.join(', ')}. O ajuste de prazo ficou incompleto.`
      : ''
    try {
      while (created < count) {
        if (startsAt && Date.parse(startsAt) <= serverNow() + 30_000) {
          throw new Error('O início agendado está próximo demais.')
        }
        const result = await createAuction.mutateAsync(request)
        created++
        if (count > 1) setCreationSummary(`Criando leilões: ${created} de ${count} confirmados…`)
        if (selectedDay && !result.deadlineReached) {
          uncheckedDeadlines.push(lotOf(result.auction))
        }
      }
    } catch (err) {
      const message = err instanceof Error ? err.message : 'Não foi possível criar o leilão.'
      if (count > 1) {
        const summary = `${created} de ${count} leilões confirmados. ${message}${deadlineWarning()} Confira a lista antes de repetir.`
        setCreationSummary(summary)
        showToast(summary, 'error')
        setLotCount(String(Math.max(1, count - created)))
      } else {
        showToast(message, 'error')
      }
      return
    } finally {
      creating.current = false
      setIsCreating(false)
    }
    if (count > 1 || uncheckedDeadlines.length) {
      const summary = `${count === 1 ? '1 leilão criado.' : `${count} leilões criados.`}${deadlineWarning()}`
      setCreationSummary(summary)
      showToast(summary, uncheckedDeadlines.length ? 'error' : 'success')
    } else if (startMode === 'at') showToast(`Leilão agendado para ${formatStartsAt(startsAt!)}.`, 'success')
    else if (startMode === 'later') showToast('Leilão criado em "Em breve". Inicie quando quiser.', 'success')
    setCopiedEnd(null)
    setDeadlineDay(undefined)
    setDurationMinutes(15)
    setStartMode('now')
    setStartsAtInput('')
    setCopiedStart(null)
    setLotCount('1')
    setEntries([emptyEntry()])
  }

  const handleStart = async (auction: Auction) => {
    try {
      await startAuction.mutateAsync(auction.id)
      showToast(`Leilão ${lotOf(auction)} iniciado.`, 'success')
    } catch (err) {
      showToast(err instanceof Error ? err.message : 'Não foi possível iniciar o leilão.', 'error')
    }
  }

  if (isLoading) return <p>Carregando leilões...</p>

  const active = auctions.filter((a) => !isAuctionFinished(a.status) && a.status !== 'SCHEDULED')
  const mine = active.filter((a) => participatingIds.includes(a.id))
  // Aba de um leilão que acabou (ou que saiu da lista): volta para "Todos".
  const selectedTab = tab !== 'all' && mine.some((a) => a.id === tab) ? tab : 'all'
  const shownActive = selectedTab === 'all' ? active : active.filter((a) => a.id === selectedTab)
  const upcoming = auctions.filter((a) => a.status === 'SCHEDULED')
  const closed = auctions.filter((a) => isAuctionFinished(a.status))

  const handleStartBatch = async () => {
    const endsAt = todayAt2150(serverNow())
    if (!endsAt) {
      showToast('O horário de 21h50 de hoje já passou.', 'error')
      return
    }
    try {
      const opened = await startBatch.mutateAsync({ auctionIds: upcoming.map(({ id }) => id), endsAt })
      showToast(`${opened.length} leilões iniciados até as 21h50 (Brasília).`, 'success')
    } catch (err) {
      showToast(err instanceof Error ? err.message : 'Não foi possível iniciar os leilões.', 'error')
    }
  }

  const handleDelete = async () => {
    if (!pendingDelete) return
    try {
      await deleteAuction.mutateAsync(pendingDelete.id)
      showToast(`Leilão ${lotOf(pendingDelete)} excluído.`, 'success')
    } catch (err) {
      showToast(err instanceof Error ? err.message : 'Não foi possível excluir o leilão.', 'error')
    } finally {
      setPendingDelete(null)
    }
  }

  return (
    <div className="auction-page">
      {isAdmin && (
        <Panel title="Abrir leilão" variant="amber" code="ADM">
          <form className="auction-form" onSubmit={handleCreate}>
            <div className="auction-form__row">
              <label className="auction-form__field">
                <span>Duração</span>
                <select
                  ref={durationField}
                  disabled={isCreating}
                  value={shownDeadlineDay ?? (copiedEnd ? 'copied' : durationMinutes)}
                  onChange={(e) => {
                    const value = e.target.value
                    if (value === 'copied') return
                    setCopiedEnd(null)
                    if (value === 'today' || value === 'tomorrow') {
                      setDeadlineDay(value)
                    } else {
                      setDeadlineDay(null)
                      setDurationMinutes(Number(value))
                    }
                  }}
                >
                  {copiedEnd && (
                    <option value="copied">Até {auctionDate.format(new Date(copiedEnd))} (Brasília)</option>
                  )}
                  {startMode === 'now' && (
                    <option value="today" disabled={!canCloseToday}>Até 21h50 de hoje (Brasília)</option>
                  )}
                  {startMode === 'now' && (
                    <option value="tomorrow">Até amanhã às 21h50 (Brasília)</option>
                  )}
                  {DURATIONS.map((d) => (
                    <option key={d.value} value={d.value}>
                      {d.label}
                    </option>
                  ))}
                </select>
              </label>
              <label className="auction-form__field">
                <span>Início</span>
                <select value={startMode} disabled={isCreating} onChange={(e) => {
                  setCopiedEnd(null)
                  setCopiedStart(null)
                  setDeadlineDay(e.target.value === 'now' ? undefined : null)
                  setStartMode(e.target.value as StartMode)
                }}>
                  <option value="now">Agora</option>
                  <option value="at">Em um horário</option>
                  <option value="later">Em breve (iniciar depois)</option>
                </select>
              </label>
              {startMode === 'at' && (
                <label className="auction-form__field">
                  <span>Começa às</span>
                  <input
                    type="datetime-local"
                    disabled={isCreating}
                    value={startsAtInput}
                    min={toLocalInput(new Date())}
                    onChange={(e) => {
                      setCopiedStart(null)
                      setStartsAtInput(e.target.value)
                    }}
                    required
                  />
                </label>
              )}
            </div>

            <fieldset className="auction-form__items" disabled={isCreating}>
              <legend>Itens do leilão</legend>
              <Button
                type="button"
                variant="secondary"
                size="sm"
                disabled={isCreating}
                onClick={draftInput.active ? draftInput.stop : draftInput.start}
              >
                {draftInput.active ? 'Desconectar RF-ui' : 'Usar como destino na RF-ui'}
              </Button>
              {draftInput.message && <p role="status">{draftInput.message}</p>}
              {entries.map((entry, index) => {
                const otherIds = entries
                  .filter((_, i) => i !== index)
                  .map((e) => e.itemId)
                  .filter((id) => id > 0)
                return (
                <div key={index} className="auction-form__item-row">
                  <AuctionItemPicker
                    itemId={entry.itemId}
                    onChange={(id) => updateEntry(index, { itemId: id })}
                    disabledIds={otherIds}
                  />
                  <input
                    type="number"
                    min={1}
                    value={entry.quantity}
                    onChange={(e) => updateEntry(index, { quantity: Number(e.target.value) })}
                    aria-label="Quantidade"
                    required
                  />
                  {entries.length > 1 && (
                    <Button type="button" variant="secondary" size="sm" onClick={() => removeEntry(index)}>
                      Remover
                    </Button>
                  )}
                </div>
                )
              })}
              <Button type="button" variant="secondary" size="sm" onClick={addEntry}>
                + Adicionar item
              </Button>
            </fieldset>

            <div className="auction-form__row auction-form__submit">
              <label className="auction-form__field auction-form__lot-count">
                <span>Número de leilões</span>
                <input type="number" min={1} max={MAX_LOTS} step={1} required value={lotCount}
                  onChange={(e) => setLotCount(e.target.value)} disabled={isCreating} />
              </label>
              <Button type="submit" loading={isCreating} disabled={!entries.some((e) => e.itemId > 0)}>
                {Number(lotCount) > 1 ? `Criar ${lotCount} leilões`
                  : startMode === 'now' ? 'Criar Leilão' : startMode === 'at' ? 'Agendar leilão' : 'Criar sem iniciar'}
              </Button>
            </div>
            {creationSummary && <p role="status">{creationSummary}</p>}
          </form>
        </Panel>
      )}

      <AuctionTabs auctions={mine} memberId={user?.memberId} selected={selectedTab} onSelect={setTab} total={active.length} />
      <AuctionListSection title="Em andamento" auctions={shownActive} emptyText="Nenhum leilão aberto."
        onCopy={isAdmin && !isCreating ? copyAuction : undefined} />
      <AuctionListSection
        title="Em breve"
        auctions={upcoming}
        actions={isAdmin && upcoming.length > 0 && canCloseToday ? (
          <Button size="sm" loading={startBatch.isPending} disabled={startAuction.isPending || deleteAuction.isPending}
            onClick={handleStartBatch}>
            Iniciar todos até 21h50
          </Button>
        ) : undefined}
        onCopy={isAdmin && !isCreating ? copyAuction : undefined}
        onStart={isAdmin && !startBatch.isPending ? handleStart : undefined}
        startingId={startAuction.isPending ? startAuction.variables : null}
        onDelete={isAdmin && !startBatch.isPending ? setPendingDelete : undefined}
        deletingId={deleteAuction.isPending ? deleteAuction.variables : null}
      />
      <AuctionListSection
        title="Encerrados"
        auctions={closed}
        deliveryActions={isAdmin ? (auction) => {
          const winner = auction.winnerNickname
          if (auction.status !== 'CLOSED' || !winner || auction.deliveredAt) return null
          const canAttach = canResolveAuctionMailAttachment(auction)
          return <>
            {canPrepareMail && (
              <Button variant="secondary" size="sm" disabled={preparingMail}
                title="Abre o rascunho no jogo para envio manual"
                onClick={() => void prepareMail(winner, canAttach ? () => resolveAuctionMailAttachment(auction) : undefined, async () => {
                  await confirmDelivery.mutateAsync(auction.id)
                  showToast('Envio confirmado pelo jogo.', 'success')
                })}>
                {canAttach ? 'Enviar item' : 'Preparar carta'}
              </Button>
            )}
            <Button size="sm" onClick={() => setPendingDeliveryId(auction.id)}>Confirmar envio</Button>
          </>
        } : undefined}
        onDelete={isAdmin ? setPendingDelete : undefined}
        deletingId={deleteAuction.isPending ? deleteAuction.variables : null}
      />

      <ConfirmDialog
        open={!!pendingDelivery}
        title={pendingDelivery ? `Confirmar envio do leilão ${lotOf(pendingDelivery)}` : ''}
        code="ADM"
        variant="primary"
        confirmLabel="Marcar como enviado"
        loading={confirmDelivery.isPending}
        onConfirm={async () => {
          if (!pendingDelivery) return
          try {
            await confirmDelivery.mutateAsync(pendingDelivery.id)
            setPendingDeliveryId(null)
            showToast('Envio do leilão confirmado.', 'success')
          } catch (err) {
            showToast(err instanceof Error ? err.message : 'Não foi possível confirmar o envio.', 'error')
          }
        }}
        onCancel={() => setPendingDeliveryId(null)}
      >
        <p>Confirme após enviar todos os itens do lote para <strong>{pendingDelivery?.winnerNickname}</strong> no jogo.</p>
      </ConfirmDialog>

      <ConfirmDialog
        open={pendingDelete !== null}
        title={`Excluir leilão ${pendingDelete ? lotOf(pendingDelete) : ''}`}
        code="ADM"
        confirmLabel="Excluir leilão"
        loading={deleteAuction.isPending}
        onConfirm={handleDelete}
        onCancel={closeDelete}
      >
        {pendingDelete && (
          <>
            <p>
              <strong>{formatAuctionItems(pendingDelete.items)}</strong> — o leilão, os
              lances e o chat serão apagados. Essa ação não pode ser desfeita.
            </p>
            <p className="confirm-dialog__note">
              Os pontos não são alterados
              {pendingDelete.winnerNickname
                ? `: ${pendingDelete.winnerNickname} continua debitado em ${pendingDelete.currentBid} pts.`
                : '.'}
            </p>
          </>
        )}
      </ConfirmDialog>
    </div>
  )
}
