import { useEffect, useRef } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { apiClient } from '@/Shared/api/client'
import { subscribeTopic } from '@/Shared/websocket/socketClient'
import { eventKeys, mergeEventIntoCache } from '@/Domain/Event/hooks/useEvents'
import { memberKeys } from '@/Domain/Member/hooks/useMembers'
import { useAppToast } from '@/Shared/ui/components/AppToast/AppToast'
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

/** Toast + balance refresh when this member's event claim is denied. */
export function useMemberLogNotifications(memberId: number | undefined) {
  const queryClient = useQueryClient()
  const { showToast } = useAppToast()
  const seenDeniedLogIds = useRef(new Set<number>())

  useEffect(() => {
    if (!memberId) return
    let cancelled = false
    let unsubscribe: (() => void) | undefined

    subscribeTopic('/topic/logs', (message) => {
      try {
        const log = JSON.parse(message.body) as AuditLogEntry
        if (log.type !== 'EVENT_DENIED' || log.targetId !== memberId) return
        if (seenDeniedLogIds.current.has(log.id)) return
        seenDeniedLogIds.current.add(log.id)

        const objectiveName = String(log.payload.objectiveName ?? '')
        const reason = String(log.payload.reason ?? '')
        const points = String(log.payload.pointsReverted ?? '')
        if (objectiveName && reason) {
          showToast(
            `Seu resgate do evento "${objectiveName}" foi removido. Motivo: ${reason} (-${points} pts)`,
            'error',
          )
          queryClient.invalidateQueries({ queryKey: memberKeys.me })
          queryClient.invalidateQueries({ queryKey: memberKeys.all })
        }
      } catch {
        // ignore malformed payloads
      }
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
  }, [memberId, queryClient, showToast])
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
