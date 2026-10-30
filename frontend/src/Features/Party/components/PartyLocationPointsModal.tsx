import { useEffect, useMemo, useState, type FormEvent } from 'react'
import {
  useAwardLocationPoints,
  useCurrentLocations,
  useLocationPreview,
  type LocationPointsType,
  type LocationScope,
} from '@/Domain/Location/hooks/useLocationPoints'
import { GAME_MAPS, mapLabel } from '@/Domain/Location/gameMaps'
import { Button } from '@/Shared/ui/components/Button/Button'
import { useAppToast } from '@/Shared/ui/components/AppToast/AppToast'
import '@/Features/Auth/Auth.styles.scss'
import './PartyLocationPointsModal.styles.scss'

interface PartyLocationPointsModalProps {
  onClose: () => void
  /**
   * party: pontos de prioridade para quem está na lista de espera.
   * rg: atalho do menu, pontos gerais para todos no local.
   */
  variant?: 'party' | 'rg'
}

const ANY = ''

/** "Vapor Lake · 3" quando há gente lá agora. */
function withCount(name: string, members: number | undefined) {
  return members ? `${name} · ${members}` : name
}

/**
 * Moderador+: escolhe o mapa e, se quiser, a região (o nome do topo do mini-mapa), confere quem
 * a DLL viu lá nos últimos 3 minutos e dá pontos a quem continuar marcado.
 */
export function PartyLocationPointsModal({ onClose, variant = 'party' }: PartyLocationPointsModalProps) {
  const rg = variant === 'rg'
  const { showToast } = useAppToast()
  const { data: locations = [] } = useCurrentLocations()
  const award = useAwardLocationPoints()
  const [map, setMap] = useState(ANY)
  const [region, setRegion] = useState(ANY)
  const scope: LocationScope = rg ? 'ALL' : 'QUEUE'
  const pointsType: LocationPointsType = rg ? 'GENERAL' : 'PRIORITY'
  const [amount, setAmount] = useState(1)
  const [reason, setReason] = useState('')
  // Desmarcados: quem chega no local depois entra marcado.
  const [unchecked, setUnchecked] = useState<Set<number>>(new Set())

  const filter = useMemo(() => ({ map: map || null, region: region || null, scope }), [map, region, scope])
  const preview = useLocationPreview(filter)
  const matches = preview.data ?? []
  const selected = matches.filter((m) => !unchecked.has(m.memberId))

  // Mapas da guild (GAME_MAPS), com quantos estão lá agora.
  const mapCounts = new Map<string, number>()
  locations.forEach((l) => l.map && mapCounts.set(l.map, (mapCounts.get(l.map) ?? 0) + l.members))
  // Regiões já vistas pela DLL nesse mapa (o catálogo cresce conforme a guild joga).
  const regionCounts = new Map<string, number>()
  for (const l of locations) {
    if (l.map === map && l.region) regionCounts.set(l.region, (regionCounts.get(l.region) ?? 0) + l.members)
  }
  if (region && !regionCounts.has(region)) regionCounts.set(region, 0)
  const regions = [...regionCounts.entries()]
    .map(([name, members]) => ({ name, members }))
    .sort((a, b) => a.name.localeCompare(b.name, 'pt-BR'))
  const hasFilter = Boolean(map)

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose()
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [onClose])

  const toggle = (memberId: number) =>
    setUnchecked((prev) => {
      const next = new Set(prev)
      if (next.has(memberId)) next.delete(memberId)
      else next.add(memberId)
      return next
    })

  const submit = (e: FormEvent) => {
    e.preventDefault()
    if (!hasFilter || selected.length === 0 || amount < 1) return
    award.mutate(
      { ...filter, pointsType, amount, memberIds: selected.map((m) => m.memberId), reason: reason.trim() || undefined, mapLabel: mapLabel(map) },
      {
        onSuccess: (result) => {
          const given = pointsType === 'PRIORITY' ? `+${amount} de prioridade` : `+${amount} pontos`
          const left = result.skipped.length ? ` Saíram do local e não receberam: ${result.skipped.join(', ')}.` : ''
          showToast(`${given} para ${result.awarded} membro(s).${left}`, 'success')
          onClose()
        },
        onError: (err) => showToast(err instanceof Error ? err.message : 'Não foi possível dar os pontos.', 'error'),
      },
    )
  }

  return (
    <div className="modal-overlay" role="presentation" onClick={onClose}>
      <form
        className="modal-card party-location-points"
        role="dialog"
        aria-modal="true"
        aria-label={rg ? 'Pontos de RG' : 'Pontos por local'}
        onClick={(e) => e.stopPropagation()}
        onSubmit={submit}
      >
        <div className="auth-window-bar">
          <span>{rg ? 'Pontos de RG' : 'Pontos por local'}</span>
          <span className="auth-window-bar__code">{rg ? 'RG' : 'LOC'}</span>
        </div>
        <div className="auth-card-content party-location-points__body">
          <div className="party-location-points__grid">
            <label>
              Mapa
              <select
                value={map}
                onChange={(e) => {
                  setMap(e.target.value)
                  setRegion(ANY)
                }}
              >
                <option value={ANY}>Selecione o mapa</option>
                {GAME_MAPS.map((m) => (
                  <option key={m.value} value={m.value}>
                    {withCount(m.label, mapCounts.get(m.value))}
                  </option>
                ))}
              </select>
            </label>
            <label>
              Região
              <select value={region} disabled={!map} onChange={(e) => setRegion(e.target.value)}>
                <option value={ANY}>{map ? 'Qualquer região' : 'Selecione o mapa primeiro'}</option>
                {regions.map((r) => (
                  <option key={r.name} value={r.name}>
                    {withCount(r.name, r.members)}
                  </option>
                ))}
              </select>
            </label>
            <label>
              Pontos
              <input
                type="number"
                min={1}
                max={1000}
                value={amount}
                onChange={(e) => setAmount(Number(e.target.value))}
                required
              />
            </label>
            <label>
              Motivo (opcional)
              <input value={reason} maxLength={200} placeholder={rg ? 'ex.: RG de sábado' : 'ex.: Boss do Vapor Lake'} onChange={(e) => setReason(e.target.value)} />
            </label>
          </div>

          <div className="party-location-points__preview">
            {!hasFilter ? (
              <p className="party-location-points__empty">Escolha o mapa.</p>
            ) : preview.isLoading ? (
              <p className="party-location-points__empty">Procurando...</p>
            ) : preview.isError ? (
              <p className="party-location-points__empty">
                {preview.error instanceof Error ? preview.error.message : 'Não foi possível buscar.'}
              </p>
            ) : matches.length === 0 ? (
              <p className="party-location-points__empty">Ninguém nesse local agora.</p>
            ) : (
              <ul className="party-location-points__list">
                {matches.map((m) => (
                  <li key={m.memberId}>
                    <label className="party-location-points__item">
                      <input type="checkbox" checked={!unchecked.has(m.memberId)} onChange={() => toggle(m.memberId)} />
                      <span className="party-location-points__name" title={m.characterName}>
                        {m.nickname}
                      </span>
                      <span className="party-location-points__where">
                        {[m.region, mapLabel(m.map)].filter(Boolean).join(' · ')}
                        {m.mapX != null && m.mapY != null ? ` (${m.mapX}, ${m.mapY})` : ''}
                      </span>                    </label>
                  </li>
                ))}
              </ul>
            )}
          </div>

          <div className="party-location-points__actions">
            <Button type="button" variant="secondary" onClick={onClose}>
              Cancelar
            </Button>
            <Button type="submit" loading={award.isPending} disabled={!hasFilter || selected.length === 0 || amount < 1}>
              Dar {amount} ponto{amount === 1 ? '' : 's'} para {selected.length}
            </Button>
          </div>
        </div>
      </form>
    </div>
  )
}
