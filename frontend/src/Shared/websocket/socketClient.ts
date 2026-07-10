import { Client, type IMessage, type StompSubscription } from '@stomp/stompjs'
import SockJS from 'sockjs-client'
import { getToken } from '@/Shared/api/client'

const WS_URL = 'http://localhost:8080/ws'

interface SubscriptionEntry {
  topic: string
  handler: (message: IMessage) => void
  stomp: StompSubscription | null
}

let stompClient: Client | null = null
let connectResolve: ((c: Client) => void) | null = null
let connectPromise: Promise<Client> | null = null

const subscriptions = new Map<symbol, SubscriptionEntry>()

function resubscribeAll() {
  if (!stompClient?.connected) return
  subscriptions.forEach((entry) => {
    entry.stomp?.unsubscribe()
    entry.stomp = stompClient!.subscribe(entry.topic, entry.handler)
  })
}

function getOrCreateClient(): Client {
  if (stompClient) return stompClient

  stompClient = new Client({
    webSocketFactory: () => new SockJS(WS_URL),
    reconnectDelay: 3000,
    heartbeatIncoming: 10000,
    heartbeatOutgoing: 10000,
    beforeConnect: () => {
      const token = getToken()
      stompClient!.connectHeaders = token ? { Authorization: `Bearer ${token}` } : {}
    },
    onConnect: () => {
      connectPromise = null
      resubscribeAll()
      if (connectResolve) {
        connectResolve(stompClient!)
        connectResolve = null
      }
    },
    onStompError: (frame) => {
      console.error('[WS] STOMP error:', frame.headers.message)
      connectPromise = null
      connectResolve = null
    },
    onWebSocketClose: () => {
      connectPromise = null
      subscriptions.forEach((entry) => {
        entry.stomp = null
      })
    },
  })

  return stompClient
}

export function connectSocket(): Promise<Client> {
  const c = getOrCreateClient()

  if (c.connected) return Promise.resolve(c)
  if (connectPromise) return connectPromise

  connectPromise = new Promise<Client>((resolve) => {
    connectResolve = resolve
    if (!c.active) c.activate()
  })

  return connectPromise
}

export async function subscribeTopic(
  topic: string,
  handler: (message: IMessage) => void,
): Promise<() => void> {
  const key = Symbol()
  const entry: SubscriptionEntry = { topic, handler, stomp: null }
  subscriptions.set(key, entry)

  try {
    const c = await connectSocket()
    // resubscribeAll() in onConnect may have already subscribed this entry
    if (c.connected && subscriptions.has(key) && !entry.stomp) {
      entry.stomp = c.subscribe(topic, handler)
    }
  } catch (err) {
    console.error(`[WS] Failed to subscribe to ${topic}:`, err)
    subscriptions.delete(key)
  }

  return () => {
    entry.stomp?.unsubscribe()
    subscriptions.delete(key)
  }
}

export function disconnectSocket(): void {
  subscriptions.clear()
  stompClient?.deactivate()
  stompClient = null
  connectPromise = null
  connectResolve = null
}

export { WS_URL }
