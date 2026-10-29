import { useState } from 'react'
import { useCreateEvent } from '@/Domain/Event/hooks/useEvents'
import { useObjectives } from '@/Domain/Objective/hooks/useObjectives'
import { Button } from '@/Shared/ui/components/Button/Button'
import '@/Features/Auth/Auth.styles.scss'
import './EventCreateModal.styles.scss'

const DURATIONS = [5, 15, 30, 60]

interface EventCreateModalProps {
  onClose: () => void
}

export function EventCreateModal({ onClose }: EventCreateModalProps) {
  const { data: objectives = [] } = useObjectives()
  const createEvent = useCreateEvent()

  const [objectiveId, setObjectiveId] = useState(0)
  const [durationMinutes, setDurationMinutes] = useState(5)
  const [password, setPassword] = useState('')

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    createEvent.mutate({ objectiveId, durationMinutes, password }, { onSuccess: onClose })
  }

  return (
    <div className="modal-overlay" role="presentation" onClick={onClose}>
      <form
        className="modal-card event-create-modal"
        onClick={(e) => e.stopPropagation()}
        onSubmit={handleSubmit}
      >
        <div className="auth-window-bar">
          <span>Lançar evento</span>
          <span className="auth-window-bar__code">ADM</span>
        </div>
        <div className="auth-card-content event-create-modal__body">
          <div className="auth-field">
            <label htmlFor="event-create-objective">Objetivo</label>
            <select
              id="event-create-objective"
              value={objectiveId || ''}
              onChange={(e) => setObjectiveId(Number(e.target.value))}
              required
              autoFocus
            >
              <option value="" disabled>
                Selecione...
              </option>
              {objectives.map((o) => (
                <option key={o.id} value={o.id}>
                  {o.name} ({o.points} pts)
                </option>
              ))}
            </select>
          </div>
          <div className="auth-field">
            <label htmlFor="event-create-duration">Duração</label>
            <select
              id="event-create-duration"
              value={durationMinutes}
              onChange={(e) => setDurationMinutes(Number(e.target.value))}
            >
              {DURATIONS.map((d) => (
                <option key={d} value={d}>
                  {d} min
                </option>
              ))}
            </select>
          </div>
          <div className="auth-field">
            <label htmlFor="event-create-password">Senha</label>
            <input
              id="event-create-password"
              type="text"
              maxLength={4}
              minLength={4}
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              placeholder="4 caracteres"
              className="event-create-modal__password"
              required
            />
          </div>
          {createEvent.error && (
            <p className="event-create-modal__error">{createEvent.error.message}</p>
          )}
          <div className="event-create-modal__actions">
            <Button type="button" variant="secondary" onClick={onClose} disabled={createEvent.isPending}>
              Cancelar
            </Button>
            <Button type="submit" loading={createEvent.isPending}>
              Criar Evento
            </Button>
          </div>
        </div>
      </form>
    </div>
  )
}
