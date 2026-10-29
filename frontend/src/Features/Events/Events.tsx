import { useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { useActiveEvents, useCancelEvent, useCreateEvent } from '@/Domain/Event/hooks/useEvents'
import { useObjectives } from '@/Domain/Objective/hooks/useObjectives'
import { useAuthContext } from '@/Features/Auth/contexts/AuthContext'
import { Button } from '@/Shared/ui/components/Button/Button'
import { Panel } from '@/Shared/ui/components/Panel/Panel'
import { ObjectivesPage } from '@/Features/Objectives/Objectives'
import '@/Features/Items/Items.styles.scss'
import './Events.styles.scss'

const DURATIONS = [5, 15, 30, 60]

export function EventsPage() {
  const { data: events = [] } = useActiveEvents()
  const { data: objectives = [] } = useObjectives()
  const createEvent = useCreateEvent()
  const cancelEvent = useCancelEvent()
  const { hasRole } = useAuthContext()
  const isAdmin = hasRole('ADMINISTRADOR')
  // Regular members only get the active events list.
  const canSeeObjectives = hasRole('MODERADOR')
  const [searchParams, setSearchParams] = useSearchParams()
  const tab = searchParams.get('tab') === 'objectives' && canSeeObjectives ? 'objectives' : 'events'

  const [confirmCancel, setConfirmCancel] = useState<number | null>(null)
  const [objectiveId, setObjectiveId] = useState(0)
  const [durationMinutes, setDurationMinutes] = useState(5)
  const [password, setPassword] = useState('')

  const handleCreate = async (e: React.FormEvent) => {
    e.preventDefault()
    await createEvent.mutateAsync({ objectiveId, durationMinutes, password })
    setPassword('')
  }

  const handleCancel = async (eventId: number) => {
    await cancelEvent.mutateAsync(eventId)
    setConfirmCancel(null)
  }

  const tabButton = (value: typeof tab, label: string) => (
    <button
      type="button"
      role="tab"
      aria-selected={tab === value}
      className={`items-tabs__btn${tab === value ? ' items-tabs__btn--active' : ''}`}
      onClick={() => setSearchParams(value === 'events' ? {} : { tab: value }, { replace: true })}
    >
      {label}
    </button>
  )

  return (
    <div className="items-page">
      {canSeeObjectives && (
        <div className="items-tabs" role="tablist">
          {tabButton('events', 'Eventos')}
          {tabButton('objectives', 'Objetivos')}
        </div>
      )}

      {tab === 'objectives' ? (
        <ObjectivesPage />
      ) : (
        <div className="events-page">
          {isAdmin && (
            <Panel title="Lançar evento" variant="amber" code="ADM">
              <form className="events-form" onSubmit={handleCreate}>
                <select
                  value={objectiveId}
                  onChange={(e) => setObjectiveId(Number(e.target.value))}
                  required
                >
                  <option value={0}>Objetivo...</option>
                  {objectives.map((o) => (
                    <option key={o.id} value={o.id}>
                      {o.name} ({o.points} pts)
                    </option>
                  ))}
                </select>
                <select
                  value={durationMinutes}
                  onChange={(e) => setDurationMinutes(Number(e.target.value))}
                >
                  {DURATIONS.map((d) => (
                    <option key={d} value={d}>
                      {d} min
                    </option>
                  ))}
                </select>
                <input
                  type="text"
                  maxLength={4}
                  minLength={4}
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  placeholder="Senha"
                  className="events-form__password"
                  required
                />
                <Button type="submit" loading={createEvent.isPending}>
                  Criar Evento
                </Button>
              </form>
            </Panel>
          )}

          <Panel title="Eventos ativos" code={`${events.length} OPS`}>
            {events.length === 0 && <p className="events-list__empty">Nenhum evento ativo.</p>}
            <ul className="events-list">
              {events.map((event) => (
                <li
                  key={event.id}
                  className={`events-list__item${event.claimedByMe ? ' events-list__item--claimed' : ''}`}
                >
                  <div className="events-list__content">
                    <strong>{event.objectiveName}</strong>
                    <div className="events-list__meta">
                      <span className="events-list__pts">+{event.points} pts</span>
                      <small>Expira {new Date(event.expiresAt).toLocaleString()}</small>
                      {event.claimedByMe && <span className="events-list__claimed">Resgatado</span>}
                    </div>
                  </div>
                  {isAdmin && (
                    <Button
                      variant="danger"
                      size="sm"
                      onClick={() => setConfirmCancel(event.id)}
                      loading={cancelEvent.isPending && cancelEvent.variables === event.id}
                    >
                      Cancelar
                    </Button>
                  )}
                </li>
              ))}
            </ul>
          </Panel>

          {confirmCancel !== null && (
            <div className="events-confirm-overlay" onClick={() => setConfirmCancel(null)}>
              <div onClick={(e) => e.stopPropagation()}>
                <Panel title="Confirmar cancelamento" variant="danger" className="events-confirm">
                  <p>Cancelar este evento? Membros não poderão mais resgatar.</p>
                  <div className="events-confirm__actions">
                    <Button variant="secondary" size="sm" onClick={() => setConfirmCancel(null)}>
                      Voltar
                    </Button>
                    <Button
                      variant="danger"
                      size="sm"
                      onClick={() => handleCancel(confirmCancel)}
                      disabled={cancelEvent.isPending}
                    >
                      {cancelEvent.isPending ? 'Cancelando...' : 'Cancelar evento'}
                    </Button>
                  </div>
                </Panel>
              </div>
            </div>
          )}
        </div>
      )}
    </div>
  )
}
