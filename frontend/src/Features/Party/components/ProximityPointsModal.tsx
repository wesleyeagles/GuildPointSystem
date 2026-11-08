import { useEffect, useState, type FormEvent, type ReactNode } from 'react'
import {
  useCreateProximityPoints,
  useProximityAnchors,
  useProximityStatus,
} from '@/Domain/Proximity/hooks/useProximityPoints'
import { mapLabel } from '@/Domain/Location/gameMaps'
import { Button } from '@/Shared/ui/components/Button/Button'
import { useAppToast } from '@/Shared/ui/components/AppToast/AppToast'
import '@/Features/Auth/Auth.styles.scss'
import './PartyLocationPointsModal.styles.scss'

/**
 * Moderador+: escolhe os membros de referência e os pontos. A DLL de cada referência lê quem está perto
 * dele no jogo (até 900 de distância) e o site dá os pontos a cada membro aprovado da lista.
 */
export function ProximityPointsModal({ onClose, tabs }: { onClose: () => void; tabs?: ReactNode }) {
  const { showToast } = useAppToast()
  const { data: anchors = [], isLoading } = useProximityAnchors()
  const create = useCreateProximityPoints()
  const [chosen, setChosen] = useState<Set<number>>(new Set())
  const [search, setSearch] = useState('')
  const [amount, setAmount] = useState(1)
  const [reason, setReason] = useState('')
  const [includeAnchors, setIncludeAnchors] = useState(true)
  const [requestId, setRequestId] = useState<number | null>(null)
  const status = useProximityStatus(requestId)
  const result = status.data ?? create.data

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose()
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [onClose])

  const toggle = (memberId: number) =>
    setChosen((prev) => {
      const next = new Set(prev)
      if (next.has(memberId)) next.delete(memberId)
      else if (next.size < 10) next.add(memberId)
      return next
    })

  const term = search.trim().toLowerCase()
  const shown = anchors.filter((a) => !term || a.nickname.toLowerCase().includes(term) || chosen.has(a.memberId))

  const submit = (e: FormEvent) => {
    e.preventDefault()
    if (chosen.size === 0 || amount < 1) return
    create.mutate(
      { anchorIds: [...chosen], amount, includeAnchors, reason: reason.trim() || undefined },
      {
        onSuccess: (created) => setRequestId(created.id),
        onError: (err) => showToast(err instanceof Error ? err.message : 'Não foi possível pedir os pontos.', 'error'),
      },
    )
  }

  const waiting = requestId != null && (!result || result.state === 'WAITING')
  const finished = requestId != null && result != null && result.state !== 'WAITING'

  return (
    <div className="modal-overlay" role="presentation" onClick={onClose}>
      <form
        className="modal-card party-location-points"
        role="dialog"
        aria-modal="true"
        aria-label="Pontos por aproximação"
        onClick={(e) => e.stopPropagation()}
        onSubmit={submit}
      >
        {tabs}
        <div className="auth-window-bar">
          <span>Pontos por aproximação</span>
          <span className="auth-window-bar__code">PROX</span>
        </div>
        <div className="auth-card-content party-location-points__body">
          {requestId == null ? (
            <>
              <div className="party-location-points__grid">
                <label>
                  Pontos
                  <input type="number" min={1} max={1000} value={amount} onChange={(e) => setAmount(Number(e.target.value))} required />
                </label>
                <label>
                  Motivo (opcional)
                  <input value={reason} maxLength={200} placeholder="ex.: PB de domingo" onChange={(e) => setReason(e.target.value)} />
                </label>
                <label>
                  Buscar referência
                  <input value={search} placeholder="Nick" onChange={(e) => setSearch(e.target.value)} />
                </label>
              </div>
              <label className="party-location-points__item">
                <input type="checkbox" checked={includeAnchors} onChange={(e) => setIncludeAnchors(e.target.checked)} />
                <span>As referências também ganham os pontos</span>
              </label>

              <div className="party-location-points__preview">
                <p className="party-location-points__empty">
                  Quem estiver a até 900 de distância de qualquer referência ganha. Só aparecem membros com a DLL no jogo
                  agora (até 10).
                </p>
                {isLoading ? (
                  <p className="party-location-points__empty">Procurando...</p>
                ) : shown.length === 0 ? (
                  <p className="party-location-points__empty">Ninguém com a DLL no jogo agora.</p>
                ) : (
                  <ul className="party-location-points__list">
                    {shown.map((a) => (
                      <li key={a.memberId}>
                        <label className="party-location-points__item">
                          <input type="checkbox" checked={chosen.has(a.memberId)} onChange={() => toggle(a.memberId)} />
                          <span className="party-location-points__name">{a.nickname}</span>
                          <span className="party-location-points__where">{mapLabel(a.map)}</span>
                        </label>
                      </li>
                    ))}
                  </ul>
                )}
              </div>
            </>
          ) : (
            <div className="party-location-points__preview">
              {waiting && (
                <p className="party-location-points__empty">
                  Esperando a DLL de {result?.anchors.filter((a) => !a.answered).map((a) => a.nickname).join(', ') || '...'}
                  {' '}(até 15 s)
                </p>
              )}
              {finished && result?.state === 'FAILED' && <p className="party-location-points__empty">{result.error}</p>}
              {finished && result?.state === 'DONE' && (
                <>
                  <p className="party-location-points__empty">
                    +{result.amount} ponto{result.amount === 1 ? '' : 's'} para {result.awarded.length} membro(s).
                    {result.outsiders > 0 ? ` ${result.outsiders} jogador(es) por perto não são membros aprovados.` : ''}
                  </p>
                  {result.anchors.some((a) => !a.answered) && (
                    <p className="party-location-points__empty">
                      Sem resposta (fora do jogo ou sem a DLL):{' '}
                      {result.anchors.filter((a) => !a.answered).map((a) => a.nickname).join(', ')}
                    </p>
                  )}
                  <ul className="party-location-points__list">
                    {result.awarded.map((a) => (
                      <li key={a.nickname} className="party-location-points__item">
                        <span className="party-location-points__name">{a.nickname}</span>
                        <span className="party-location-points__where">
                          {a.distance === 0 && a.near === a.nickname ? 'referência' : `${a.distance} de ${a.near}`}
                        </span>
                      </li>
                    ))}
                  </ul>
                </>
              )}
            </div>
          )}

          <div className="party-location-points__actions">
            <Button type="button" variant="secondary" onClick={onClose}>
              {finished ? 'Fechar' : 'Cancelar'}
            </Button>
            {requestId == null && (
              <Button type="submit" loading={create.isPending} disabled={chosen.size === 0 || amount < 1}>
                Dar {amount} ponto{amount === 1 ? '' : 's'} perto de {chosen.size} referência{chosen.size === 1 ? '' : 's'}
              </Button>
            )}
            {waiting && <Button type="button" loading disabled>Esperando</Button>}
          </div>
        </div>
      </form>
    </div>
  )
}
