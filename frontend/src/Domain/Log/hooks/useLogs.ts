import { useEffect } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { apiClient } from '@/Shared/api/client'
import { subscribeTopic } from '@/Shared/websocket/socketClient'
import { eventKeys, mergeEventIntoCache } from '@/Domain/Event/hooks/useEvents'
import type { AuditLogEntry, GuildEvent, Page } from '@/Domain/types/models'

export function useLogs(page = 0, size = 50) {
  return useQuery({
    queryKey: ['logs', page, size],
    queryFn: () => apiClient<Page<AuditLogEntry>>(`/logs?page=${page}&size=${size}`),
  })
}

export function useLogsSubscription() {
  const queryClient = useQueryClient()

  useEffect(() => {
    let unsubscribe: (() => void) | undefined

    subscribeTopic('/topic/logs', () => {
      queryClient.invalidateQueries({ queryKey: ['logs'] })
    }).then((fn) => {
      unsubscribe = fn
    })

    return () => unsubscribe?.()
  }, [queryClient])
}

export function useEventSubscription(onEvent?: (event: GuildEvent) => void) {
  const queryClient = useQueryClient()

  useEffect(() => {
    let cancelled = false
    let unsubscribe: (() => void) | undefined

    subscribeTopic('/topic/events', (message) => {
      let parsed: unknown
      try {
        parsed = JSON.parse(message.body)
      } catch {
        return
      }
      if (
        !parsed ||
        typeof parsed !== 'object' ||
        !('id' in parsed) ||
        !('active' in parsed)
      ) {
        return
      }
      const event = parsed as GuildEvent
      mergeEventIntoCache(queryClient, event)
      queryClient.invalidateQueries({ queryKey: eventKeys.active })
      onEvent?.(event)
    }).then((fn) => {
      if (cancelled) {
        fn()
      } else {
        unsubscribe = fn
      }
    })

    return () => {
      cancelled = true
      unsubscribe?.()
    }
  }, [queryClient, onEvent])
}

export type { AuditLogEntry }
