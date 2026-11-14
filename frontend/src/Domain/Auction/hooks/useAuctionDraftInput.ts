import { useEffect, useEffectEvent, useRef, useState } from 'react'
import { apiClient, ApiError } from '@/Shared/api/client'
import type { Item, Page } from '@/Domain/types/models'
import auctionRelicCodes from '../auctionRelicCodes.json'

type DraftItem = { id: number; name: string }
const bridge = 'http://127.0.0.1:15184/draft'

async function findExactItem(code: string, signal: AbortSignal): Promise<DraftItem | undefined> {
  let match: DraftItem | undefined
  for (let page = 0; ; page++) {
    const params = new URLSearchParams({ search: code, page: String(page), size: '24' })
    const result = await apiClient<Page<Item>>(`/items?${params}`, { signal })
    for (const item of result.content) {
      if (item.gameCode?.toLowerCase() !== code) continue
      if (match) throw new ApiError(`Há mais de um cadastro para o código ${code}.`, 409)
      match = item
    }
    if (page + 1 >= result.totalPages) return match
  }
}

async function lookupDraftItem(inventoryCode: string, signal: AbortSignal): Promise<DraftItem> {
  const code = inventoryCode.toLowerCase()
  const exact = await findExactItem(code, signal)
  if (exact) return exact
  // Tradable inventory codes verified against Item.edf and catalog migration V120 (#45).
  const alias = (auctionRelicCodes as Partial<Record<string, string>>)[code]
  const relic = alias ? await findExactItem(alias, signal) : undefined
  if (!relic) throw new ApiError(`Item não cadastrado no site: ${inventoryCode}`, 404)
  return relic
}

async function request(action: string, key: string, signal?: AbortSignal, ids?: string[]) {
  return fetch(bridge, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ action, key, ids }),
    signal,
    keepalive: action === 'close',
  })
}

export function useAuctionDraftInput(enabled: boolean, paused: boolean, addItem: (id: number) => void) {
  const [connected, setConnected] = useState(true)
  const [lease, setLease] = useState<AbortSignal | null>(null)
  const [message, setMessage] = useState('')
  const session = useRef<AbortController | null>(null)
  const receiveItem = useEffectEvent(addItem)

  function suspend() {
    session.current?.abort()
  }

  function stop() {
    suspend()
    setConnected(false)
    setMessage('')
  }

  const requested = enabled && connected
  const ownsDestination = lease !== null && !lease.aborted
  useEffect(() => {
    if (!requested) return
    const lock = new AbortController()
    let release = () => {}
    const held = new Promise<void>((resolve) => { release = resolve })
    void (async () => {
      try {
        await navigator.locks.request('auction-rf-ui-draft', { signal: lock.signal }, async () => {
          if (lock.signal.aborted) return
          setLease(lock.signal)
          await held
        })
      } catch {
        if (!lock.signal.aborted) {
          setConnected(false)
          setMessage('Não foi possível reservar a conexão nesta aba. Confira as permissões do navegador.')
        }
      }
    })()
    return () => {
      lock.abort()
      release()
    }
  }, [requested])

  useEffect(() => {
    if (!requested || !ownsDestination || paused) return
    const key = Array.from(crypto.getRandomValues(new Uint8Array(16)), (byte) => byte.toString(16).padStart(2, '0')).join('')
    const controller = new AbortController()
    const { signal } = controller
    session.current = controller
    let timer: number | undefined
    let selected = false
    const applied = new Set<string>()
    const items = new Map<string, DraftItem>()

    signal.addEventListener('abort', () => {
      window.clearTimeout(timer)
      void request('close', key).catch(() => {})
    }, { once: true })

    function disconnect(reason: string) {
      controller.abort()
      setConnected(false)
      setMessage(reason)
    }

    async function poll() {
      try {
        if (!selected) {
          const response = await request('select', key, signal)
          if (signal.aborted) return
          if (!response.ok) throw new Error()
          selected = true
          setMessage('RF-ui conectada. Use Ctrl + Alt + clique no inventário.')
        }
        const response = await request('read', key, signal)
        if (signal.aborted) return
        if (response.status === 409) {
          disconnect('O rascunho deixou de ser o destino. Selecione-o novamente para continuar.')
          return
        }
        if (!response.ok) throw new Error()
        const events: unknown = await response.json()
        if (!Array.isArray(events) || events.length > 256 || events.some((event) =>
          !event || typeof event.id !== 'string' || !/^[a-f0-9]{32}$/.test(event.id) ||
          (event.code !== null && (typeof event.code !== 'string' || !/^[A-Za-z0-9]{7}$/.test(event.code))))) {
          disconnect('Resposta inválida da ponte local. Nenhum item foi adicionado.')
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
                item = await lookupDraftItem(event.code, signal)
                items.set(event.code, item)
              }
              if (signal.aborted) return
              receiveItem(item.id)
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
          setMessage('Ponte da RF-ui indisponível. Abra a ponte local e permita o acesso à rede local se o navegador pedir.')
        } else {
          setMessage('Conexão com a RF-ui ou com o catálogo interrompida. Tentando novamente…')
        }
      }
      if (!signal.aborted) timer = window.setTimeout(() => void poll(), selected ? 750 : 3000)
    }

    void poll()
    return () => controller.abort()
  }, [ownsDestination, paused, requested])

  return {
    active: requested,
    message: !enabled || paused ? '' : requested && !ownsDestination
      ? 'Aguardando a conexão da RF-ui ficar disponível nesta aba.'
      : message,
    start: () => setConnected(true),
    stop,
    suspend,
  }
}
