import { useState } from 'react'
import { Button } from '@/Shared/ui/components/Button/Button'
import {
  PARTY_MAP_OPTIONS,
  PARTY_SPOT_PLACEHOLDER,
  partyMapHasSpot,
  type PartyMap,
} from '@/Features/Party/Party.types'
import '@/Features/Auth/Auth.styles.scss'
import './PartyCreateModal.styles.scss'

export type PartyCreateMode = 'member' | 'empty'

interface PartyCreateModalProps {
  mode: PartyCreateMode
  loading: boolean
  onClose: () => void
  onSubmit: (payload: { map: PartyMap; spot?: string }) => void
}

export function PartyCreateModal({ mode, loading, onClose, onSubmit }: PartyCreateModalProps) {
  const [map, setMap] = useState<PartyMap>('ETHER')
  const [spot, setSpot] = useState('')

  const title = mode === 'empty' ? 'Criar PT vazia' : 'Criar PT'
  const code = mode === 'empty' ? 'ORG-PT' : 'NEW-PT'

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    onSubmit({
      map,
      spot: partyMapHasSpot(map) ? spot.trim() || undefined : undefined,
    })
  }

  const handleMapChange = (next: PartyMap) => {
    setMap(next)
    if (!partyMapHasSpot(next)) {
      setSpot('')
    }
  }

  return (
    <div className="modal-overlay" role="presentation" onClick={onClose}>
      <form
        className="modal-card party-create-modal"
        onClick={(e) => e.stopPropagation()}
        onSubmit={handleSubmit}
      >
        <div className="auth-window-bar">
          <span>{title}</span>
          <span className="auth-window-bar__code">{code}</span>
        </div>
        <div className="auth-card-content party-create-modal__body">
          <div className="auth-field">
            <label htmlFor="party-create-map">Mapa</label>
            <select
              id="party-create-map"
              value={map}
              onChange={(e) => handleMapChange(e.target.value as PartyMap)}
              required
            >
              {PARTY_MAP_OPTIONS.map((opt) => (
                <option key={opt.value} value={opt.value}>
                  {opt.label}
                </option>
              ))}
            </select>
          </div>
          {partyMapHasSpot(map) && (
            <div className="auth-field">
              <label htmlFor="party-create-spot">Spot (opcional)</label>
              <input
                id="party-create-spot"
                type="text"
                maxLength={200}
                placeholder={PARTY_SPOT_PLACEHOLDER[map as Exclude<PartyMap, 'SEM_MAPA'>]}
                value={spot}
                onChange={(e) => setSpot(e.target.value)}
              />
            </div>
          )}
          <div className="party-create-modal__actions">
            <Button type="button" variant="secondary" onClick={onClose} disabled={loading}>
              Cancelar
            </Button>
            <Button type="submit" loading={loading}>
              {mode === 'empty' ? 'Criar PT vazia' : 'Criar PT'}
            </Button>
          </div>
        </div>
      </form>
    </div>
  )
}
