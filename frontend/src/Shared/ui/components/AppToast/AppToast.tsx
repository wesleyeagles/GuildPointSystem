import { createContext, useCallback, useContext, useRef, useState, type ReactNode } from 'react'
import './AppToast.styles.scss'

export type ToastType = 'success' | 'error' | 'info'

interface Toast {
  id: number
  message: string
  type: ToastType
}

interface ShowToastOptions {
  durationMs?: number
}

interface AppToastContextValue {
  showToast: (message: string, type?: ToastType, options?: ShowToastOptions) => void
}

const AppToastContext = createContext<AppToastContextValue | null>(null)

let counter = 0

export function AppToastProvider({ children }: { children: ReactNode }) {
  const [toasts, setToasts] = useState<Toast[]>([])
  const timers = useRef<Map<number, ReturnType<typeof setTimeout>>>(new Map())

  const dismiss = useCallback((id: number) => {
    setToasts((prev) => prev.filter((t) => t.id !== id))
    const timer = timers.current.get(id)
    if (timer) {
      clearTimeout(timer)
      timers.current.delete(id)
    }
  }, [])

  const showToast = useCallback(
    (message: string, type: ToastType = 'success', options?: ShowToastOptions) => {
      const id = ++counter
      setToasts((prev) => [...prev, { id, message, type }])
      const duration = options?.durationMs ?? 3500
      const timer = setTimeout(() => dismiss(id), duration)
      timers.current.set(id, timer)
    },
    [dismiss],
  )

  return (
    <AppToastContext.Provider value={{ showToast }}>
      {children}
      <div className="app-toasts">
        {toasts.map((t) => (
          <div key={t.id} className={`app-toast app-toast--${t.type}`}>
            <span className="app-toast__icon">
              {t.type === 'success' ? '✓' : t.type === 'error' ? '✕' : 'ℹ'}
            </span>
            <span className="app-toast__msg">{t.message}</span>
            <button type="button" className="app-toast__close" onClick={() => dismiss(t.id)}>
              ×
            </button>
          </div>
        ))}
      </div>
    </AppToastContext.Provider>
  )
}

export function useAppToast() {
  const ctx = useContext(AppToastContext)
  if (!ctx) throw new Error('useAppToast must be used within AppToastProvider')
  return ctx
}
