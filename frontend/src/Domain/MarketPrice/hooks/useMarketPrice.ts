import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { apiClient } from '@/Shared/api/client'

export const marketPriceKeys = {
  access: ['market-price', 'access'] as const,
}

/** Preço do market no tooltip de item (DLL). A lista só vem para quem pode configurar (Líder). */
export interface MarketPriceAccess {
  canConfigure: boolean
  viewers: { id: number; nickname: string }[]
}

export function useMarketPriceAccess() {
  return useQuery({
    queryKey: marketPriceKeys.access,
    queryFn: () => apiClient<MarketPriceAccess>('/market/access'),
    staleTime: 60_000,
  })
}

export function useUpdateMarketPriceAccess() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (body: { memberIds: number[] }) =>
      apiClient<MarketPriceAccess>('/market/access', { method: 'PUT', body }),
    onSuccess: (data) => queryClient.setQueryData(marketPriceKeys.access, data),
  })
}
