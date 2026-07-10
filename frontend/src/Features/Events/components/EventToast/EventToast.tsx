import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useState,
  type ReactNode,
} from 'react'
import { useEventSubscription } from '@/Domain/Log/hooks/useLogs'
import { useClaimEvent } from '@/Domain/Event/hooks/useEvents'
import type { GuildEvent } from '@/Domain/types/models'
import { Button } from '@/Shared/ui/components/Button/Button'
import './EventToast.styles.scss'

interface EventToastContextValue {
  pushEvent: (event: GuildEvent) => void
}

const EventToastContext = createContext<EventToastContextValue | null>(null)

export function EventToastProvider({ children }: { children: ReactNode }) {
  const [toasts, setToasts] = useState<GuildEvent[]>([])
  const claimMutation = useClaimEvent()

  const pushEvent = useCallback((event: GuildEvent) => {
    if (!event.active) {
      setToasts((prev) => prev.filter((e) => e.id !== event.id))
      return
    }
    if (event.claimedByMe) return
    setToasts((prev) => {
      if (prev.some((e) => e.id === event.id)) return prev
      return [...prev, event]
    })
  }, [])

  useEventSubscription(pushEvent)

  const dismiss = (id: number) => {
    setToasts((prev) => prev.filter((e) => e.id !== id))
  }

  return (
    <EventToastContext.Provider value={{ pushEvent }}>
      {children}
      <div className="event-toasts">
        {toasts.map((event) => (
          <EventToastCard
            key={event.id}
            event={event}
            onDismiss={() => dismiss(event.id)}
            onClaim={async (password) => {
              await claimMutation.mutateAsync({ id: event.id, password })
              dismiss(event.id)
            }}
            loading={claimMutation.isPending}
          />
        ))}
      </div>
    </EventToastContext.Provider>
  )
}

function formatRemaining(seconds: number): string {
  const m = Math.floor(seconds / 60)
  const s = seconds % 60
  return `${m}:${String(s).padStart(2, '0')}`
}

function EventToastCard({
  event,
  onDismiss,
  onClaim,
  loading,
}: {
  event: GuildEvent
  onDismiss: () => void
  onClaim: (password: string) => Promise<void>
  loading: boolean
}) {
  const [password, setPassword] = useState('')
  const [error, setError] = useState('')
  const [remainingSeconds, setRemainingSeconds] = useState(() =>
    Math.max(0, Math.floor((new Date(event.expiresAt).getTime() - Date.now()) / 1000)),
  )

  useEffect(() => {
    const tick = () => {
      const secs = Math.max(
        0,
        Math.floor((new Date(event.expiresAt).getTime() - Date.now()) / 1000),
      )
      setRemainingSeconds(secs)
      if (secs <= 0) onDismiss()
    }

    tick()
    const id = window.setInterval(tick, 1000)
    return () => window.clearInterval(id)
  }, [event.expiresAt, onDismiss])

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    setError('')
    try {
      await onClaim(password)
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Erro ao resgatar')
    }
  }

  return (
    <div className="event-toast">
      <div className="event-toast__header">
        <strong>Novo Evento!</strong>
        <div className="event-toast__header-actions">
          <span className="event-toast__timer">{formatRemaining(remainingSeconds)}</span>
          <button type="button" className="event-toast__close" onClick={onDismiss}>
            ×
          </button>
        </div>
      </div>
      <p>
        {event.objectiveName} — {event.points} pts
      </p>
      <form onSubmit={handleSubmit} className="event-toast__form">
        <input
          type="text"
          maxLength={4}
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          placeholder="Senha"
          className="event-toast__input"
        />
        <Button type="submit" size="sm" loading={loading}>
          Resgatar
        </Button>
      </form>
      {error && <p className="event-toast__error">{error}</p>}
    </div>
  )
}

export function useEventToast() {
  const ctx = useContext(EventToastContext)
  if (!ctx) throw new Error('useEventToast must be used within EventToastProvider')
  return ctx
}
