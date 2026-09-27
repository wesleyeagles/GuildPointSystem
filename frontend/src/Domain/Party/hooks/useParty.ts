import { useEffect } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { apiClient } from '@/Shared/api/client'
import { subscribeTopic } from '@/Shared/websocket/socketClient'
import type { PartyBoard, PartyMap, PartyMemberNotice } from '@/Features/Party/Party.types'

export const partyKeys = {
  board: ['party', 'board'] as const,
}

function setBoard(queryClient: ReturnType<typeof useQueryClient>, data: PartyBoard) {
  queryClient.setQueryData(partyKeys.board, data)
}

export function usePartyBoard() {
  const queryClient = useQueryClient()

  useEffect(() => {
    let unsubscribe: (() => void) | undefined
    subscribeTopic('/topic/parties', () => {
      queryClient.invalidateQueries({ queryKey: partyKeys.board, exact: true })
    }).then((fn) => {
      unsubscribe = fn
    })
    return () => unsubscribe?.()
  }, [queryClient])

  return useQuery({
    queryKey: partyKeys.board,
    queryFn: () => apiClient<PartyBoard>('/parties/board'),
  })
}

export function useCreateParty() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (body: { map: PartyMap; spot?: string }) =>
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
    mutationFn: (body: { note?: string }) =>
      apiClient<PartyBoard>('/parties/lfg', { method: 'PUT', body }),
    onSuccess: (data) => setBoard(queryClient, data),
  })
}

export function useLeavePartyLfg() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: () => apiClient<PartyBoard>('/parties/lfg', { method: 'DELETE' }),
    onSuccess: (data) => setBoard(queryClient, data),
  })
}

export function useCreatePartyForMember() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (body: { map: PartyMap; leaderMemberId: number }) =>
      apiClient<PartyBoard>('/parties/for-member', { method: 'POST', body }),
    onSuccess: (data) => setBoard(queryClient, data),
  })
}

export function useAssembleParty() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (body: { map: PartyMap; leaderMemberId: number; memberIds: number[] }) =>
      apiClient<PartyBoard>('/parties/assemble', { method: 'POST', body }),
    onSuccess: (data) => setBoard(queryClient, data),
  })
}

export function useCreateEmptyParty() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (body: { map: PartyMap; spot?: string }) =>
      apiClient<PartyBoard>('/parties/empty', { method: 'POST', body }),
    onSuccess: (data) => setBoard(queryClient, data),
  })
}

export function useMovePartyMember() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({
      partyId,
      memberId,
      fromPartyId,
    }: {
      partyId: number
      memberId: number
      fromPartyId: number | null
    }) =>
      apiClient<PartyBoard>(`/parties/${partyId}/move`, {
        method: 'POST',
        body: { memberId, fromPartyId },
      }),
    onSuccess: (data) => setBoard(queryClient, data),
  })
}

export function useKickPartyMember() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ partyId, memberId }: { partyId: number; memberId: number }) =>
      apiClient<PartyBoard>(`/parties/${partyId}/kick`, {
        method: 'POST',
        body: { memberId },
      }),
    onSuccess: (data) => setBoard(queryClient, data),
  })
}

export function useTransferPartyLeader() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ partyId, memberId }: { partyId: number; memberId: number }) =>
      apiClient<PartyBoard>(`/parties/${partyId}/leader`, {
        method: 'POST',
        body: { memberId },
      }),
    onSuccess: (data) => setBoard(queryClient, data),
  })
}

export function useAddPartyMemberDirect() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ partyId, memberId }: { partyId: number; memberId: number }) =>
      apiClient<PartyBoard>(`/parties/${partyId}/members`, {
        method: 'POST',
        body: { memberId },
      }),
    onSuccess: (data) => setBoard(queryClient, data),
  })
}

function parsePartyMemberNotice(body: string): PartyMemberNotice | null {
  try {
    const parsed = JSON.parse(body) as unknown
    if (!parsed || typeof parsed !== 'object') return null
    const raw = parsed as PartyMemberNotice
    if (raw.type !== 'KICKED_FROM_PARTY' || typeof raw.message !== 'string') return null
    return raw
  } catch {
    return null
  }
}

export function usePartyMemberNotices(
  memberId: number | undefined,
  onNotice: (notice: PartyMemberNotice) => void,
) {
  useEffect(() => {
    if (!memberId) return
    let cancelled = false
    let unsubscribe: (() => void) | undefined

    subscribeTopic(`/topic/parties/member/${memberId}`, (message) => {
      const notice = parsePartyMemberNotice(message.body)
      if (notice) onNotice(notice)
    }).then((fn) => {
      if (cancelled) fn()
      else unsubscribe = fn
    })

    return () => {
      cancelled = true
      unsubscribe?.()
    }
  }, [memberId, onNotice])
}
