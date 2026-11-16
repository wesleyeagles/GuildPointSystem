import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { apiClient } from '@/Shared/api/client'

export const rouletteKeys = {
  all: ['roulette'] as const,
}

/** Roleta mostrada no meio do jogo pela DLL (1.47.0+). */
export interface SentRoulette {
  id: number
  prize: string
  createdBy: string | null
  /** Teste: só personagem de Líder vê no jogo. */
  leadersOnly: boolean
  entries: string[]
  /** Fica null enquanto a roleta gira (não estraga a surpresa). */
  winner: string | null
  createdAt: string
  spinning: boolean
}

/** Só vem preenchido para Admin/Líder. */
export interface RouletteView {
  canCreate: boolean
  recent: SentRoulette[]
}

export function useRoulettes() {
  return useQuery({
    queryKey: rouletteKeys.all,
    queryFn: () => apiClient<RouletteView>('/roulettes'),
    staleTime: 5_000,
    refetchInterval: (query) => (query.state.data?.recent.some((r) => r.spinning) ? 2_000 : 20_000),
  })
}

export function useCreateRoulette() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (body: { prize: string; memberIds: number[]; leadersOnly: boolean }) =>
      apiClient<RouletteView>('/roulettes', { method: 'POST', body }),
    onSuccess: (data) => queryClient.setQueryData(rouletteKeys.all, data),
  })
}
