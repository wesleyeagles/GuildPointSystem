import { useEffect, useRef, useState } from 'react'
import { apiClient, ApiError } from '@/Shared/api/client'

type DraftItem = { id: number; name: string }
const bridge = 'http://127.0.0.1:15184/draft'

async function request(action: string, key: string, signal?: AbortSignal, ids?: string[]) {
  return fetch(bridge, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ action, key, ids }),
    signal,
    keepalive: action === 'close',
  })
}

export function useAuctionDraftInput(enabled: boolean, addItem: (id: number) => void) {
  const [active, setActive] = useState(false)
  const [message, setMessage] = useState('')
  const session = useRef<AbortController | null>(null)
  const [wasEnabled, setWasEnabled] = useState(enabled)

  if (wasEnabled !== enabled) {
    setWasEnabled(enabled)
    setActive(false)
    setMessage('')
  }

  function stop() {
    session.current?.abort()
    session.current = null
    setActive(false)
    setMessage('')
  }

  useEffect(() => {
    if (enabled) return () => session.current?.abort()
  }, [enabled])

  function start() {
    if (!enabled || (session.current && !session.current.signal.aborted)) return
    const key = Array.from(crypto.getRandomValues(new Uint8Array(16)), (byte) => byte.toString(16).padStart(2, '0')).join('')
    const controller = new AbortController()
    const { signal } = controller
    session.current = controller
    setActive(true)
    setMessage('Conectando à RF-ui…')
    let timer: number | undefined
    let selected = false
    const applied = new Set<string>()
    const items = new Map<string, DraftItem>()

    signal.addEventListener('abort', () => {
      window.clearTimeout(timer)
      void request('close', key).catch(() => {})
    }, { once: true })

    async function poll() {
      try {
        if (!selected) {
          const response = await request('select', key, signal)
          if (!response.ok) throw new Error()
          selected = true
          setMessage('RF-ui conectada. Use Ctrl + Alt + clique no inventário.')
        }
        const response = await request('read', key, signal)
        if (signal.aborted) return
        if (response.status === 409) {
          stop()
          setMessage('O rascunho deixou de ser o destino. Selecione-o novamente para continuar.')
          return
        }
        if (!response.ok) throw new Error()
        const events: unknown = await response.json()
        if (!Array.isArray(events) || events.length > 256 || events.some((event) =>
          !event || typeof event.id !== 'string' || !/^[a-f0-9]{32}$/.test(event.id) ||
          (event.code !== null && (typeof event.code !== 'string' || !/^[A-Za-z0-9]{7}$/.test(event.code))))) {
          stop()
          setMessage('Resposta inválida da ponte local. Nenhum item foi adicionado.')
          return
        }
        for (const event of events) {
          if (signal.aborted) return
          if (applied.has(event.id)) continue
          if (!event.code) {
            setMessage('Item sem código confirmado no Item.edf.')
          } else {
            try {
              let item = items.get(event.code)
              if (!item) {
                item = await apiClient<DraftItem>(`/items/auction-code/${encodeURIComponent(event.code)}`, { signal })
                items.set(event.code, item)
              }
              if (signal.aborted) return
              addItem(item.id)
              setMessage(`+1 ${item.name}`)
            } catch (error) {
              if (signal.aborted) return
              if (!(error instanceof ApiError) || ![400, 404, 409].includes(error.status)) throw error
              setMessage(error.message)
            }
          }
          applied.add(event.id)
        }
        if (events.length) {
          const ack = await request('ack', key, signal, events.map(({ id }) => id))
          if (!ack.ok) throw new Error()
          for (const { id } of events) applied.delete(id)
        }
      } catch {
        if (signal.aborted) return
        if (!selected) {
          stop()
          setMessage('Ponte da RF-ui indisponível. Abra a ponte local e permita o acesso à rede local se o navegador pedir.')
          return
        }
        setMessage('Conexão com a RF-ui ou com o catálogo interrompida. Tentando novamente…')
      }
      if (!signal.aborted) timer = window.setTimeout(() => void poll(), 750)
    }

    void poll()
  }

  return { active: enabled && active, message: enabled ? message : '', start, stop }
}
