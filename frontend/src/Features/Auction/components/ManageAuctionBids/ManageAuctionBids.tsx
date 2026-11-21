import { useState } from 'react'
import { useAuctionBids, useCancelAuctionBid } from '@/Domain/Auction/hooks/useAuction'
import type { Auction, AuctionBidHistory } from '@/Domain/types/models'
import { ApiError } from '@/Shared/api/client'
import { Button } from '@/Shared/ui/components/Button/Button'
import { ConfirmDialog } from '@/Shared/ui/components/ConfirmDialog/ConfirmDialog'
import './ManageAuctionBids.styles.scss'

export function ManageAuctionBids({ auction }: { auction: Auction }) {
  const [open, setOpen] = useState(false)
  const [page, setPage] = useState(0)
  const [selected, setSelected] = useState<AuctionBidHistory | null>(null)
  const [reason, setReason] = useState('Clique acidental')
  const [error, setError] = useState('')
  const history = useAuctionBids(auction.id, page, open)
  const cancel = useCancelAuctionBid(auction.id)
  const canCancel = ['OPEN', 'DOLE', 'PAUSED'].includes(auction.status)
  const historyError = history.error instanceof ApiError && [404, 405].includes(history.error.status)
    ? 'O backend publicado ainda não disponibiliza o histórico e o cancelamento de lances. É necessário publicar o backend atualizado da main.'
    : history.error instanceof Error ? history.error.message : 'Não foi possível carregar os lances.'
  const back = () => {
    if (cancel.isPending) return
    if (selected) setSelected(null)
    else setOpen(false)
    setError('')
  }
  const confirm = async () => {
    if (cancel.isPending) return
    if (!selected) { setOpen(false); return }
    if (!canCancel || !reason.trim()) { setError('Informe o motivo do cancelamento.'); return }
    try {
      await cancel.mutateAsync({ bidId: selected.id, reason: reason.trim() })
      setSelected(null)
      setError('')
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Não foi possível cancelar o lance.')
    }
  }
  return <>
    <Button variant="secondary" onClick={() => setOpen(true)}>Gerenciar lances</Button>
    <ConfirmDialog open={open} title={selected ? 'Cancelar este lance?' : 'Histórico de lances'} code="ADM"
      confirmLabel={selected ? 'Confirmar cancelamento' : 'Fechar'} cancelLabel={selected ? 'Voltar' : 'Voltar ao leilão'}
      variant={selected ? 'danger' : 'primary'} loading={cancel.isPending}
      onConfirm={() => void confirm()} onCancel={back}>
      {selected ? <div className="auction-bid-history">
        <p><strong>{selected.nickname}</strong> · {selected.amount} pts · Lance #{selected.id}</p>
        <p>Somente este lance será cancelado. A liderança e os pontos reservados serão recalculados.</p>
        <label>Motivo<input maxLength={300} value={reason} disabled={cancel.isPending}
          onChange={(e) => setReason(e.target.value)} /></label>
      </div> : <div className="auction-bid-history">
        {history.isLoading && <p>Carregando lances…</p>}
        {history.isError && <p role="alert">{historyError}
          <Button variant="secondary" onClick={() => void history.refetch()}>Tentar novamente</Button></p>}
        {history.data?.content.length === 0 && <p>Nenhum lance registrado.</p>}
        {history.data?.content.map((bid) => <div key={bid.id} className={`auction-bid-history__row${bid.cancelledAt ? ' is-cancelled' : ''}`}>
          <div><strong>{bid.nickname}</strong> · {bid.amount} pts
            <small>#{bid.id} · {new Date(bid.createdAt).toLocaleString()}</small>
            {bid.cancelledAt && <small>Cancelado por {bid.cancelledBy ?? 'admin'} · {new Date(bid.cancelledAt).toLocaleString()} · {bid.cancellationReason}</small>}
          </div>
          {!bid.cancelledAt && <Button size="sm" variant="danger" disabled={!canCancel}
            onClick={() => { setSelected(bid); setReason('Clique acidental'); setError('') }}>Cancelar lance</Button>}
        </div>)}
        {(history.data?.totalPages ?? 0) > 1 && <div className="auction-bid-history__pages">
          <Button size="sm" variant="secondary" disabled={page === 0} onClick={() => setPage(page - 1)}>Anterior</Button>
          <span>{page + 1} / {history.data?.totalPages}</span>
          <Button size="sm" variant="secondary" disabled={page + 1 >= (history.data?.totalPages ?? 0)} onClick={() => setPage(page + 1)}>Próxima</Button>
        </div>}
        {!canCancel && <p>Cancelamento disponível antes do encerramento ou sorteio.</p>}
      </div>}
      {error && <p role="alert">{error}</p>}
    </ConfirmDialog>
  </>
}
