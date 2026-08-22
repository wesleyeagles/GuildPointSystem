import { useState } from 'react'
import { useActiveEvents, useCancelEvent, useCreateEvent } from '@/Domain/Event/hooks/useEvents'
import { useObjectives } from '@/Domain/Objective/hooks/useObjectives'
import { useAuthContext } from '@/Features/Auth/contexts/AuthContext'
import { Button } from '@/Shared/ui/components/Button/Button'
import './Events.styles.scss'

const DURATIONS = [5, 15, 30, 60]

export function EventsPage() {
  const { data: events = [] } = useActiveEvents()
  const { data: objectives = [] } = useObjectives()
  const createEvent = useCreateEvent()
  const cancelEvent = useCancelEvent()
  const { hasRole } = useAuthContext()
  const isAdmin = hasRole('ADMINISTRADOR')

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

  return (
    <div className="events-page">
      <h2>Eventos</h2>

      {isAdmin && (
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
            required
          />
          <Button type="submit" loading={createEvent.isPending}>
            Criar Evento
          </Button>
        </form>
      )}

      <ul className="events-list">
        {events.map((event) => (
          <li key={event.id} className="events-list__item">
            <div className="events-list__content">
              <strong>{event.objectiveName}</strong> — {event.points} pts
              <br />
              <small>
                Expira: {new Date(event.expiresAt).toLocaleString()}
                {event.claimedByMe ? ' (resgatado)' : ''}
              </small>
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

      {confirmCancel !== null && (
        <div className="events-confirm-overlay" onClick={() => setConfirmCancel(null)}>
          <div className="events-confirm" onClick={(e) => e.stopPropagation()}>
            <p>Cancelar este evento? Membros não poderão mais resgatar.</p>
            <div className="events-confirm__actions">
              <button
                type="button"
                className="events-confirm__btn events-confirm__btn--cancel"
                onClick={() => setConfirmCancel(null)}
              >
                Voltar
              </button>
              <button
                type="button"
                className="events-confirm__btn events-confirm__btn--confirm"
                onClick={() => handleCancel(confirmCancel)}
                disabled={cancelEvent.isPending}
              >
                {cancelEvent.isPending ? 'Cancelando...' : 'Cancelar evento'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
