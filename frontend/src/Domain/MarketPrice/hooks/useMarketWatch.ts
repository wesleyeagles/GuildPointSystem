import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { apiClient } from '@/Shared/api/client'

export const marketWatchKeys = {
  all: ['market-watch'] as const,
  items: (search: string) => ['market-watch', 'items', search] as const,
}

/** Alerta de item no market: o membro recebe um card no jogo quando o item aparece à venda. */
export interface MarketWatch {
  id: number
  memberId: number
  nickname: string
  itemCode: string
  itemName: string
  minUpgrade: number | null
  /** Preço máximo por unidade, em CS. */
  maxPrice: number | null
  createdAt: string
}

export interface MarketAlert {
  id: number
  nickname: string
  itemCode: string
  itemName: string
  upgrade: number
  quantity: number
  /** Preço total do anúncio, em CS. */
  price: number
  createdAt: string
}

/** Só vem preenchido para Admin/Líder. */
export interface MarketWatchView {
  canConfigure: boolean
  watches: MarketWatch[]
  recentAlerts: MarketAlert[]
}

export interface NewMarketWatch {
  memberId: number
  itemCode: string
  itemName: string
  minUpgrade: number | null
  maxPrice: number | null
}

export function useMarketWatches() {
  return useQuery({
    queryKey: marketWatchKeys.all,
    queryFn: () => apiClient<MarketWatchView>('/market/watches'),
    staleTime: 30_000,
    refetchInterval: 60_000,
  })
}

export function useMarketWatchItems(search: string) {
  const term = search.trim()
  return useQuery({
    queryKey: marketWatchKeys.items(term),
    queryFn: () => apiClient<{ code: string; name: string }[]>(`/market/watches/items?search=${encodeURIComponent(term)}`),
    enabled: term.length >= 2,
    staleTime: 5 * 60_000,
  })
}

export function useAddMarketWatch() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (body: NewMarketWatch) => apiClient<MarketWatchView>('/market/watches', { method: 'POST', body }),
    onSuccess: (data) => queryClient.setQueryData(marketWatchKeys.all, data),
  })
}

export function useRemoveMarketWatch() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => apiClient<MarketWatchView>(`/market/watches/${id}`, { method: 'DELETE' }),
    onSuccess: (data) => queryClient.setQueryData(marketWatchKeys.all, data),
  })
}
