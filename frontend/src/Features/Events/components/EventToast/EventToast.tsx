import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useState,
  type ReactNode,
} from 'react'
import { useEventSubscription } from '@/Domain/Log/hooks/useLogs'
import { useActiveEvents, useClaimEvent } from '@/Domain/Event/hooks/useEvents'
import type { GuildEvent } from '@/Domain/types/models'
import { Button } from '@/Shared/ui/components/Button/Button'
import { useAppToast } from '@/Shared/ui/components/AppToast/AppToast'
import './EventToast.styles.scss'

interface EventToastContextValue {
  pushEvent: (event: GuildEvent) => void
}

const EventToastContext = createContext<EventToastContextValue | null>(null)

export function EventToastProvider({ children }: { children: ReactNode }) {
  const [toasts, setToasts] = useState<GuildEvent[]>([])
  const { data: activeEvents = [] } = useActiveEvents()
  const claimMutation = useClaimEvent()
  const { showToast } = useAppToast()

  const pushEvent = useCallback((event: GuildEvent) => {
    if (!event.active || event.claimedByMe) {
      setToasts((prev) => prev.filter((e) => e.id !== event.id))
      return
    }
    setToasts((prev) => {
      if (prev.some((e) => e.id === event.id)) return prev
      return [...prev, event]
    })
  }, [])

  useEventSubscription(pushEvent)

  useEffect(() => {
    setToasts((prev) =>
      prev.filter((toast) => {
        const match = activeEvents.find((e) => e.id === toast.id)
        return match && match.active && !match.claimedByMe
      }),
    )
  }, [activeEvents])

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
              showToast(
                `Evento "${event.objectiveName}" resgatado! +${event.points} pts`,
                'success',
              )
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
    if (password.length !== 4) {
      setError('A senha deve ter exatamente 4 caracteres.')
      return
    }
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
