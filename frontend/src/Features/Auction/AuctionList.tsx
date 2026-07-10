import { useState } from 'react'
import { Link } from 'react-router-dom'
import { useAuctions, useCreateAuction } from '@/Domain/Auction/hooks/useAuction'
import { useItems } from '@/Domain/Item/hooks/useItems'
import { useAuthContext } from '@/Features/Auth/contexts/AuthContext'
import { Button } from '@/Shared/ui/components/Button/Button'
import type { Auction } from '@/Domain/types/models'
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

const emptyEntry = (): ItemEntry => ({ itemId: 0, quantity: 1 })

const STATUS_LABEL: Record<string, string> = {
  OPEN: 'Aberto',
  DOLE: 'DOLE',
  TIE_BREAK: 'Empate',
  CLOSED: 'Encerrado',
}

function AuctionListSection({
  title,
  auctions,
  emptyText,
}: {
  title: string
  auctions: Auction[]
  emptyText?: string
}) {
  if (auctions.length === 0) {
    return emptyText ? <p className="auction-page__empty">{emptyText}</p> : null
  }

  return (
    <section className="auction-page__section">
      <h3 className="auction-page__section-title">{title}</h3>
      <ul className="auction-list">
        {auctions.map((auction) => (
          <li
            key={auction.id}
            className={`auction-list__item${auction.status === 'CLOSED' ? ' auction-list__item--closed' : ''}`}
          >
            <div>
              <strong>
                {auction.items.map((i) => i.itemName).join(', ') || `Leilão #${auction.id}`}
              </strong>
              <br />
              <small>
                Lance: {auction.currentBid} pts · {STATUS_LABEL[auction.status] ?? auction.status}
                {auction.winnerNickname ? ` · Vencedor: ${auction.winnerNickname}` : ''}
                {auction.tiedCount > 1 ? ` · ${auction.tiedCount} empatados` : ''}
              </small>
            </div>
            <Link to={`/auctions/${auction.id}`}>
              <Button variant="secondary" size="sm">
                Ver
              </Button>
            </Link>
          </li>
        ))}
      </ul>
    </section>
  )
}

export function AuctionListPage() {
  const { data: auctions = [], isLoading } = useAuctions()
  const { data: items = [] } = useItems()
  const createAuction = useCreateAuction()
  const { hasRole } = useAuthContext()
  const isAdmin = hasRole('ADMINISTRADOR')

  const [durationMinutes, setDurationMinutes] = useState(15)
  const [entries, setEntries] = useState<ItemEntry[]>([emptyEntry()])

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

    await createAuction.mutateAsync({
      durationMinutes,
      items: validItems.map(({ itemId, quantity }) => ({ itemId, quantity })),
    })
    setEntries([emptyEntry()])
  }

  if (isLoading) return <p>Carregando leilões...</p>

  const active = auctions.filter((a) => a.status !== 'CLOSED')
  const closed = auctions.filter((a) => a.status === 'CLOSED')

  return (
    <div className="auction-page">
      <div className="auction-page__header">
        <h2>Leilões</h2>
      </div>

      {isAdmin && (
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
          </div>

          <fieldset className="auction-form__items">
            <legend>Itens do leilão</legend>
            {entries.map((entry, index) => (
              <div key={index} className="auction-form__item-row">
                <select
                  value={entry.itemId}
                  onChange={(e) => updateEntry(index, { itemId: Number(e.target.value) })}
                  required={index === 0}
                >
                  <option value={0}>Selecione um item...</option>
                  {items.map((item) => (
                    <option key={item.id} value={item.id}>
                      {item.name}
                    </option>
                  ))}
                </select>
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
            ))}
            <Button type="button" variant="secondary" size="sm" onClick={addEntry}>
              + Adicionar item
            </Button>
          </fieldset>

          <Button type="submit" loading={createAuction.isPending} disabled={items.length === 0}>
            Criar Leilão
          </Button>
        </form>
      )}

      <AuctionListSection title="Em andamento" auctions={active} emptyText="Nenhum leilão aberto." />
      <AuctionListSection title="Encerrados" auctions={closed} />
    </div>
  )
}
