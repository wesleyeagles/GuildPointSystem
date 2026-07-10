import { useEffect } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { apiClient } from '@/Shared/api/client'
import { subscribeTopic } from '@/Shared/websocket/socketClient'
import type { Auction, AuctionMessage } from '@/Domain/types/models'
import { memberKeys } from '@/Domain/Member/hooks/useMembers'

export const auctionKeys = {
  all: ['auctions'] as const,
  detail: (id: number) => ['auctions', id] as const,
  messages: (id: number) => ['auctions', id, 'messages'] as const,
}

export function useAuctions() {
  const queryClient = useQueryClient()

  useEffect(() => {
    let unsubscribe: (() => void) | undefined
    subscribeTopic('/topic/auctions', () => {
      queryClient.invalidateQueries({ queryKey: auctionKeys.all, exact: true })
    }).then((fn) => {
      unsubscribe = fn
    })
    return () => unsubscribe?.()
  }, [queryClient])

  return useQuery({
    queryKey: auctionKeys.all,
    queryFn: () => apiClient<Auction[]>('/auctions'),
  })
}

export function useAuction(id: number) {
  const queryClient = useQueryClient()

  const query = useQuery({
    queryKey: auctionKeys.detail(id),
    queryFn: () => apiClient<Auction>(`/auctions/${id}`),
    enabled: id > 0,
    refetchInterval: (q) => (q.state.data?.status === 'OPEN' ? 5000 : false),
  })

  useEffect(() => {
    if (id <= 0) return
    let unsubscribe: (() => void) | undefined

    subscribeTopic(`/topic/auctions/${id}`, (message) => {
      const payload = JSON.parse(message.body) as {
        type: string
        auction?: Auction
        message?: AuctionMessage
      }

      if (payload.type === 'AUCTION_UPDATE' && payload.auction) {
        queryClient.setQueryData(auctionKeys.detail(id), payload.auction)
        queryClient.invalidateQueries({ queryKey: auctionKeys.all, exact: true })
      }

      // Append message directly — never invalidate+refetch for messages:
      // WS publishes happen before DB commit, so a refetch would return stale data.
      if (
        (payload.type === 'CHAT' || payload.type === 'BOT' || payload.type === 'BID') &&
        payload.message
      ) {
        queryClient.setQueryData(
          auctionKeys.messages(id),
          (old: AuctionMessage[] | undefined) => {
            if (!old) return [payload.message!]
            if (old.some((m) => m.id === payload.message!.id)) return old
            return [...old, payload.message!]
          },
        )
      }

      // TIE_BREAK_START: do NOT invalidate detail — AUCTION_UPDATE follows immediately
      // and uses setQueryData. A pre-commit refetch would overwrite good data with DOLE.

      if (payload.type === 'AUCTION_CLOSED') {
        // AUCTION_UPDATE already handled via setQueryData above; only refresh the list
        queryClient.invalidateQueries({ queryKey: auctionKeys.all, exact: true })
      }
    }).then((fn) => {
      unsubscribe = fn
    })

    return () => unsubscribe?.()
  }, [id, queryClient])

  return query
}

export function useAuctionMessages(id: number) {
  return useQuery({
    queryKey: auctionKeys.messages(id),
    queryFn: () => apiClient<AuctionMessage[]>(`/auctions/${id}/messages`),
    enabled: id > 0,
    refetchInterval: 30000, // fallback for missed WS events (reconnect recovery)
  })
}

export function usePlaceBid() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ id, amount }: { id: number; amount: number }) =>
      apiClient<Auction>(`/auctions/${id}/bids`, { method: 'POST', body: { amount } }),
    onSuccess: (_, { id }) => {
      queryClient.invalidateQueries({ queryKey: auctionKeys.detail(id), exact: true })
      queryClient.invalidateQueries({ queryKey: memberKeys.me })
    },
  })
}

export function useSendAuctionMessage() {
  return useMutation({
    mutationFn: ({ id, content, imageUrl }: { id: number; content: string; imageUrl?: string }) =>
      apiClient<void>(`/auctions/${id}/messages`, {
        method: 'POST',
        body: { content, imageUrl },
      }),
    // No invalidation needed — the sender receives their own WS CHAT push
    // which appends directly via setQueryData in useAuction.
  })
}

export function useCreateAuction() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (body: {
      durationMinutes: number
      items: { itemId: number; quantity: number }[]
    }) => apiClient<Auction>('/auctions', { method: 'POST', body }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: auctionKeys.all, exact: true }),
  })
}

export function usePointsSubscription(memberId: number | undefined) {
  const queryClient = useQueryClient()

  useEffect(() => {
    if (!memberId) return
    let unsubscribe: (() => void) | undefined

    subscribeTopic(`/topic/points/${memberId}`, () => {
      queryClient.invalidateQueries({ queryKey: memberKeys.me })
    }).then((fn) => {
      unsubscribe = fn
    })

    return () => unsubscribe?.()
  }, [memberId, queryClient])
}
