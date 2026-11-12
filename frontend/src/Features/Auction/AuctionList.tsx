import { Fragment, useCallback, useState } from 'react'
import { Link } from 'react-router-dom'
import {
  useAuctionAdminAction,
  useAuctions,
  useCreateAuction,
  useDeleteAuction,
} from '@/Domain/Auction/hooks/useAuction'
import { useDiscordBySiteMember, type DiscordMember } from '@/Domain/Discord/hooks/useDiscordMembers'
import { formatAuctionItems, isAuctionFinished } from '@/Domain/types/models'
import { useAppToast } from '@/Shared/ui/components/AppToast/AppToast'
import { ConfirmDialog } from '@/Shared/ui/components/ConfirmDialog/ConfirmDialog'
import { useAuthContext } from '@/Features/Auth/contexts/AuthContext'
import { AuctionItemPicker } from '@/Features/Auction/components/AuctionItemPicker/AuctionItemPicker'
import { AuctionItemIcon } from '@/Features/Auction/components/AuctionItemIcon/AuctionItemIcon'
import { Button } from '@/Shared/ui/components/Button/Button'
import { Panel } from '@/Shared/ui/components/Panel/Panel'
import type { Auction } from '@/Domain/types/models'
import { formatStartsAt, formatTime, pausedSeconds, useCountdown } from '@/Features/Auction/hooks/useCountdown'
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

/** Value for <input type="datetime-local"> in the browser's time zone. */
function toLocalInput(d: Date) {
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`
}

const emptyEntry = (): ItemEntry => ({ itemId: 0, quantity: 1 })

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

function AuctionListSection({
  title,
  auctions,
  emptyText,
  onDelete,
  deletingId,
  onStart,
  startingId,
}: {
  title: string
  auctions: Auction[]
  emptyText?: string
  /** Shown for finished and not-yet-started auctions, to Líder/Administrador. */
  onDelete?: (auction: Auction) => void
  deletingId?: number | null
  /** Opens a not-yet-started auction now (Líder/Administrador). */
  onStart?: (auction: Auction) => void
  startingId?: number | null
}) {
  const discordBySite = useDiscordBySiteMember()

  if (auctions.length === 0 && !emptyText) return null

  return (
    <Panel title={title} code={`${auctions.length} LOT`} flush>
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
                <strong>
                  {formatAuctionItems(auction.items) || `Leilão #${auction.id}`}
                </strong>
                <AuctionSubtitle auction={auction} discordBySite={discordBySite} />
              </div>
              <span
                className={`auction-list__status auction-list__status--${auction.status.toLowerCase()}`}
              >
                {STATUS_LABEL[auction.status] ?? auction.status}
              </span>
              <AuctionListTime auction={auction} />
              <span className="auction-list__bid">{auction.currentBid}</span>
              <div className="auction-list__actions">
                <Link to={`/auctions/${auction.id}`}>
                  <Button variant="secondary" size="sm">
                    Ver
                  </Button>
                </Link>
                {onStart && auction.status === 'SCHEDULED' && (
                  <Button size="sm" loading={startingId === auction.id} onClick={() => onStart(auction)}>
                    Iniciar
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

const lotOf = (auction: Auction) => `#${String(auction.id).padStart(3, '0')}`

export function AuctionListPage() {
  const { data: auctions = [], isLoading } = useAuctions()
  const createAuction = useCreateAuction()
  const deleteAuction = useDeleteAuction()
  const startAuction = useAuctionAdminAction('start')
  const { showToast } = useAppToast()
  const { hasRole } = useAuthContext()
  const isAdmin = hasRole('ADMINISTRADOR')

  const [durationMinutes, setDurationMinutes] = useState(15)
  const [startMode, setStartMode] = useState<StartMode>('now')
  const [startsAtInput, setStartsAtInput] = useState('')
  const [entries, setEntries] = useState<ItemEntry[]>([emptyEntry()])
  const [pendingDelete, setPendingDelete] = useState<Auction | null>(null)
  const closeDelete = useCallback(() => setPendingDelete(null), [])

  const updateEntry = (index: number, patch: Partial<ItemEntry>) => {
    setEntries((prev) => prev.map((entry, i) => (i === index ? { ...entry, ...patch } : entry)))
  }

  const addEntry = () => setEntries((prev) => [...prev, emptyEntry()])

  const removeEntry = (index: number) => {
    setEntries((prev) => (prev.length <= 1 ? prev : prev.filter((_, i) => i !== index)))
  }

  const handleCreate = async (e: React.FormEvent) => {
    e.preventDefault()
    const validItems = entries.filter((entry) => entry.itemId > 0 && entry.quantity >= 1)
    if (validItems.length === 0) return

    let startsAt: string | undefined
    if (startMode === 'at') {
      const when = new Date(startsAtInput)
      if (!startsAtInput || Number.isNaN(when.getTime()) || when.getTime() <= Date.now()) {
        showToast('Escolha um horário de início no futuro.', 'error')
        return
      }
      startsAt = when.toISOString()
    }

    try {
      await createAuction.mutateAsync({
        durationMinutes,
        items: validItems.map(({ itemId, quantity }) => ({ itemId, quantity })),
        startsAt,
        startLater: startMode === 'later' || undefined,
      })
    } catch (err) {
      showToast(err instanceof Error ? err.message : 'Não foi possível criar o leilão.', 'error')
      return
    }
    if (startMode === 'at') showToast(`Leilão agendado para ${formatStartsAt(startsAt!)}.`, 'success')
    if (startMode === 'later') showToast('Leilão criado em "Em breve". Inicie quando quiser.', 'success')
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
  const upcoming = auctions.filter((a) => a.status === 'SCHEDULED')
  const closed = auctions.filter((a) => isAuctionFinished(a.status))

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
                  value={durationMinutes}
                  onChange={(e) => setDurationMinutes(Number(e.target.value))}
                >
                  {DURATIONS.map((d) => (
                    <option key={d.value} value={d.value}>
                      {d.label}
                    </option>
                  ))}
                </select>
              </label>
              <label className="auction-form__field">
                <span>Início</span>
                <select value={startMode} onChange={(e) => setStartMode(e.target.value as StartMode)}>
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
                    value={startsAtInput}
                    min={toLocalInput(new Date())}
                    onChange={(e) => setStartsAtInput(e.target.value)}
                    required
                  />
                </label>
              )}
            </div>

            <fieldset className="auction-form__items">
              <legend>Itens do leilão</legend>
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

            <Button
              type="submit"
              loading={createAuction.isPending}
              disabled={!entries.some((e) => e.itemId > 0)}
            >
              {startMode === 'now' ? 'Criar Leilão' : startMode === 'at' ? 'Agendar leilão' : 'Criar sem iniciar'}
            </Button>
          </form>
        </Panel>
      )}

      <AuctionListSection title="Em andamento" auctions={active} emptyText="Nenhum leilão aberto." />
      <AuctionListSection
        title="Em breve"
        auctions={upcoming}
        onStart={isAdmin ? handleStart : undefined}
        startingId={startAuction.isPending ? startAuction.variables : null}
        onDelete={isAdmin ? setPendingDelete : undefined}
        deletingId={deleteAuction.isPending ? deleteAuction.variables : null}
      />
      <AuctionListSection
        title="Encerrados"
        auctions={closed}
        onDelete={isAdmin ? setPendingDelete : undefined}
        deletingId={deleteAuction.isPending ? deleteAuction.variables : null}
      />

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
