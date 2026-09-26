import { useEffect } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { apiClient } from '@/Shared/api/client'
import { subscribeTopic } from '@/Shared/websocket/socketClient'
import type { PartyBoard, PartyMap } from '@/Features/Party/Party.types'

export const partyKeys = {
  board: (map: PartyMap) => ['party', 'board', map] as const,
}

function setBoard(queryClient: ReturnType<typeof useQueryClient>, data: PartyBoard) {
  queryClient.setQueryData(partyKeys.board(data.map), data)
}

export function usePartyBoard(map: PartyMap) {
  const queryClient = useQueryClient()

  useEffect(() => {
    let unsubscribe: (() => void) | undefined
    subscribeTopic(`/topic/parties/${map}`, () => {
      queryClient.invalidateQueries({ queryKey: partyKeys.board(map), exact: true })
    }).then((fn) => {
      unsubscribe = fn
    })
    return () => unsubscribe?.()
  }, [map, queryClient])

  return useQuery({
    queryKey: partyKeys.board(map),
    queryFn: () => apiClient<PartyBoard>(`/parties/board?map=${map}`),
  })
}

export function useCreateParty() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (body: { map: PartyMap }) =>
      apiClient<PartyBoard>('/parties', { method: 'POST', body }),
    onSuccess: (data) => setBoard(queryClient, data),
  })
}

export function useDisbandParty() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (partyId: number) =>
      apiClient<PartyBoard>(`/parties/${partyId}`, { method: 'DELETE' }),
    onSuccess: (data) => setBoard(queryClient, data),
  })
}

export function useLeaveParty() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (partyId: number) =>
      apiClient<PartyBoard>(`/parties/${partyId}/leave`, { method: 'POST' }),
    onSuccess: (data) => setBoard(queryClient, data),
  })
}

export function useRequestJoinParty() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (partyId: number) =>
      apiClient<PartyBoard>(`/parties/${partyId}/requests`, { method: 'POST' }),
    onSuccess: (data) => setBoard(queryClient, data),
  })
}

export function useInviteToParty() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ partyId, memberId }: { partyId: number; memberId: number }) =>
      apiClient<PartyBoard>(`/parties/${partyId}/invites`, {
        method: 'POST',
        body: { memberId },
      }),
    onSuccess: (data) => setBoard(queryClient, data),
  })
}

export function useAcceptPartyPending() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (pendingId: number) =>
      apiClient<PartyBoard>(`/parties/pending/${pendingId}/accept`, { method: 'POST' }),
    onSuccess: (data) => setBoard(queryClient, data),
  })
}

export function useRejectPartyPending() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (pendingId: number) =>
      apiClient<PartyBoard>(`/parties/pending/${pendingId}/reject`, { method: 'POST' }),
    onSuccess: (data) => setBoard(queryClient, data),
  })
}

export function useCancelPartyPending() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (pendingId: number) =>
      apiClient<PartyBoard>(`/parties/pending/${pendingId}`, { method: 'DELETE' }),
    onSuccess: (data) => setBoard(queryClient, data),
  })
}

export function useUpsertPartyLfg() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (body: { map: PartyMap; note?: string }) =>
      apiClient<PartyBoard>('/parties/lfg', { method: 'PUT', body }),
    onSuccess: (data) => setBoard(queryClient, data),
  })
}

export function useLeavePartyLfg(map: PartyMap) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: () => apiClient<PartyBoard>(`/parties/lfg?map=${map}`, { method: 'DELETE' }),
    onSuccess: (data) => setBoard(queryClient, data),
  })
}
