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
import { serverNow } from '@/Shared/utils/serverClock'
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

  // Cards closed with "×" (or expired) in this session: the active-events refetch must not bring them back.
  const [dismissed, setDismissed] = useState<ReadonlySet<number>>(() => new Set())

  // The list is the source of truth: drop claimed/ended cards and open one for every active event not yet
  // claimed. Without this, a member who opened the site after the event was created (or whose socket was
  // down when it was broadcast) never saw the card, and so had nowhere to type the password.
  useEffect(() => {
    setToasts((prev) => {
      const kept = prev.filter((toast) => {
        const match = activeEvents.find((e) => e.id === toast.id)
        return match && match.active && !match.claimedByMe
      })
      const missing = activeEvents.filter(
        (e) =>
          e.active &&
          !e.claimedByMe &&
          !dismissed.has(e.id) &&
          new Date(e.expiresAt).getTime() > serverNow() &&
          !kept.some((k) => k.id === e.id),
      )
      return missing.length === 0 && kept.length === prev.length ? prev : [...kept, ...missing]
    })
  }, [activeEvents, dismissed])

  const dismiss = (id: number) => {
    setDismissed((prev) => (prev.has(id) ? prev : new Set(prev).add(id)))
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
  // "4:07" abaixo de 1 hora; "21:13:56" a partir dela (igual ao card da DLL).
  const h = Math.floor(seconds / 3600)
  const m = Math.floor(seconds / 60) % 60
  const s = String(seconds % 60).padStart(2, '0')
  return h > 0 ? `${h}:${String(m).padStart(2, '0')}:${s}` : `${m}:${s}`
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
    Math.max(0, Math.floor((new Date(event.expiresAt).getTime() - serverNow()) / 1000)),
  )

  useEffect(() => {
    const tick = () => {
      const secs = Math.max(
        0,
        Math.floor((new Date(event.expiresAt).getTime() - serverNow()) / 1000),
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
      <div className="event-toast__body">
        <span className="event-toast__icon" aria-hidden="true">!</span>
        <p>
          {event.objectiveName} — {event.points} pts
        </p>
      </div>
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
