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

  const handleCancel = async (eventId: number) => {
    if (!window.confirm('Cancelar este evento? Membros não poderão mais resgatar.')) return
    await cancelEvent.mutateAsync(eventId)
  }

  const [objectiveId, setObjectiveId] = useState(0)
  const [durationMinutes, setDurationMinutes] = useState(5)
  const [password, setPassword] = useState('')

  const handleCreate = async (e: React.FormEvent) => {
    e.preventDefault()
    await createEvent.mutateAsync({ objectiveId, durationMinutes, password })
    setPassword('')
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
                onClick={() => handleCancel(event.id)}
                loading={cancelEvent.isPending && cancelEvent.variables === event.id}
              >
                Cancelar
              </Button>
            )}
          </li>
        ))}
      </ul>
    </div>
  )
}
