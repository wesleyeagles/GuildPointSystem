import { useAuthContext } from '@/Features/Auth/contexts/AuthContext'
import { useEffect, useRef } from 'react'
import { keepPreviousData, useQuery, useQueryClient, type QueryClient } from '@tanstack/react-query'
import { apiClient } from '@/Shared/api/client'
import { onSocketReconnect, subscribeTopic, subscribeTopicEffect } from '@/Shared/websocket/socketClient'
import { eventKeys, mergeEventIntoCache } from '@/Domain/Event/hooks/useEvents'
import { memberKeys } from '@/Domain/Member/hooks/useMembers'
import { getPayloadClaimId, markClaimDeniedInPage } from '@/Domain/Log/utils/logClaimUtils'
import { useAppToast } from '@/Shared/ui/components/AppToast/AppToast'
import type { AuditLogEntry, AuditLogType, GuildEvent, Page } from '@/Domain/types/models'

const logKeys = {
  all: ['logs'] as const,
  objectives: ['log-objectives'] as const,
}

/** Filtros da aba Logs; null = sem filtro. memberId casa com quem fez ou com quem recebeu. */
export interface LogFilters {
  type: AuditLogType | null
  memberId: number | null
  objective: string | null
}

export const NO_LOG_FILTERS: LogFilters = { type: null, memberId: null, objective: null }

function matchesFilters(log: AuditLogEntry, f: LogFilters | undefined) {
  if (!f) return true
  if (f.type && log.type !== f.type) return false
  if (f.memberId && log.actorId !== f.memberId && log.targetId !== f.memberId) return false
  if (f.objective && log.payload.objectiveName !== f.objective) return false
  return true
}

export function useLogs(page = 0, size = 50, filters: LogFilters = NO_LOG_FILTERS) {
  const { hasRole } = useAuthContext()
  const audience = hasRole('ADMINISTRADOR') ? 'admin' : 'public'
  return useQuery({
    queryKey: [...logKeys.all, audience, page, size, filters],
    queryFn: () => {
      const params = new URLSearchParams({ page: String(page), size: String(size) })
      if (filters.type) params.set('type', filters.type)
      if (filters.memberId) params.set('memberId', String(filters.memberId))
      if (filters.objective) params.set('objective', filters.objective)
      return apiClient<Page<AuditLogEntry>>(`/logs?${params}`)
    },
    placeholderData: keepPreviousData,
  })
}

/** Nomes de evento (objetivo) que já apareceram nos logs, para o filtro "Evento". */
export function useLogObjectives() {
  return useQuery({
    queryKey: logKeys.objectives,
    queryFn: () => apiClient<string[]>('/logs/objectives'),
    staleTime: 60_000,
  })
}

/**
 * Applies a broadcast log without a refetch: with hundreds of members claiming one event, refetching
 * on every log would turn each claim into one GET /logs per connected client.
 */
function applyLogToCache(queryClient: QueryClient, log: AuditLogEntry) {
  // Cada lista só recebe o log se ele passar nos filtros dela (5º item da chave).
  const matching = {
    queryKey: logKeys.all,
    predicate: (q: { queryKey: readonly unknown[] }) => matchesFilters(log, q.queryKey[5] as LogFilters | undefined),
  }
  queryClient.setQueriesData<Page<AuditLogEntry>>(matching, (old) => {
    if (!old || old.number !== 0 || old.content.some((l) => l.id === log.id)) return old
    const totalElements = old.totalElements + 1
    return {
      ...old,
      content: [log, ...old.content].slice(0, old.size),
      totalElements,
      totalPages: Math.ceil(totalElements / old.size),
    }
  })

  if (log.type === 'EVENT_DENIED') {
    const claimId = getPayloadClaimId(log.payload)
    if (claimId !== null) {
      queryClient.setQueriesData<Page<AuditLogEntry>>({ queryKey: logKeys.all }, (old) =>
        markClaimDeniedInPage(old, claimId),
      )
    }
  }

  // Older pages shifted by one entry: refresh them when next viewed, not now.
  queryClient.invalidateQueries({
    queryKey: logKeys.all,
    predicate: (q) => (q.state.data as Page<AuditLogEntry> | undefined)?.number !== 0,
    refetchType: 'none',
  })
}

export function useLogsSubscription() {
  const queryClient = useQueryClient()

  useEffect(
    () =>
      subscribeTopicEffect('/topic/logs', (message) => {
        try {
          applyLogToCache(queryClient, JSON.parse(message.body) as AuditLogEntry)
        } catch {
          queryClient.invalidateQueries({ queryKey: logKeys.all })
        }
      }),
    [queryClient],
  )

  // Logs published while the socket was down never arrive — refetch on reconnect.
  useEffect(
    () => onSocketReconnect(() => queryClient.invalidateQueries({ queryKey: logKeys.all })),
    [queryClient],
  )
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

  // Creations/cancellations published while the socket was down never arrive — refetch on reconnect.
  useEffect(
    () => onSocketReconnect(() => queryClient.invalidateQueries({ queryKey: eventKeys.active })),
    [queryClient],
  )

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
      // claimedByMe is per member and a broadcast can't know it — keep what this client already has.
      const known = queryClient.getQueryData<GuildEvent[]>(eventKeys.active)?.find((e) => e.id === parsed.id)
      const event = { ...(parsed as GuildEvent), claimedByMe: known?.claimedByMe ?? false }
      // The payload is the full event: apply it directly, no refetch fan-out across every client.
      mergeEventIntoCache(queryClient, event)
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
