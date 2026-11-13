import { useEffect } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { apiClient } from '@/Shared/api/client'
import { onSocketReconnect, subscribeTopicEffect } from '@/Shared/websocket/socketClient'
import type { Phrase, PhraseBoard } from '@/Domain/types/models'

export const phraseKeys = {
  board: ['phrases'] as const,
}

/** Every phrase with its votes; refreshes live when anyone votes or an admin edits. */
export function usePhraseBoard() {
  const queryClient = useQueryClient()
  useEffect(
    () =>
      subscribeTopicEffect('/topic/phrases', () => {
        queryClient.invalidateQueries({ queryKey: phraseKeys.board })
      }),
    [queryClient],
  )
  useEffect(() => onSocketReconnect(() => queryClient.invalidateQueries({ queryKey: phraseKeys.board })), [queryClient])

  return useQuery({
    queryKey: phraseKeys.board,
    queryFn: () => apiClient<PhraseBoard>('/phrases'),
  })
}

/** Vote or take the vote back; the server answers with the updated board. */
export function useTogglePhraseVote() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ id, voted }: { id: number; voted: boolean }) =>
      apiClient<PhraseBoard>(`/phrases/${id}/vote`, { method: voted ? 'DELETE' : 'POST' }),
    onSuccess: (board) => queryClient.setQueryData(phraseKeys.board, board),
  })
}

export type PhraseInput = { text: string; memberId: number; saidAt: string | null }

export function useSavePhrase() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ id, ...body }: PhraseInput & { id?: number }) =>
      apiClient<Phrase>(id ? `/phrases/${id}` : '/phrases', { method: id ? 'PUT' : 'POST', body }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: phraseKeys.board }),
  })
}

export function useDeletePhrase() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => apiClient<void>(`/phrases/${id}`, { method: 'DELETE' }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: phraseKeys.board }),
  })
}
