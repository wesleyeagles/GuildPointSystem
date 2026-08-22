import { useQuery } from '@tanstack/react-query'
import { apiClient } from '@/Shared/api/client'
import type { EffectDefinition, GameAccessory, ItemSet } from '@/Domain/types/models'

export interface CatalogAccessoryFilters {
  subtype?: 'RING' | 'AMULET'
  grade?: number
  civilMask?: string
  search?: string
}

export const catalogKeys = {
  accessories: (filters: CatalogAccessoryFilters) =>
    ['catalog', 'accessories', filters] as const,
  accessory: (gameCode: string) => ['catalog', 'accessories', gameCode] as const,
  effects: ['catalog', 'effects'] as const,
  sets: (gameCode: string) => ['catalog', 'sets', gameCode] as const,
}

export function useCatalogAccessories(filters: CatalogAccessoryFilters) {
  const params = new URLSearchParams()
  if (filters.subtype) params.set('subtype', filters.subtype)
  if (filters.grade != null) params.set('grade', String(filters.grade))
  if (filters.civilMask) params.set('civilMask', filters.civilMask)
  if (filters.search) params.set('search', filters.search)

  const query = params.toString()

  return useQuery({
    queryKey: catalogKeys.accessories(filters),
    queryFn: async () => {
      const path = query ? `/catalog/accessories?${query}` : '/catalog/accessories'
      return apiClient<GameAccessory[]>(path)
    },
  })
}

export function useCatalogAccessory(gameCode: string | null) {
  return useQuery({
    queryKey: catalogKeys.accessory(gameCode ?? ''),
    queryFn: () => apiClient<GameAccessory>(`/catalog/accessories/${gameCode}`),
    enabled: Boolean(gameCode),
  })
}

export function useCatalogEffects() {
  return useQuery({
    queryKey: catalogKeys.effects,
    queryFn: () => apiClient<EffectDefinition[]>('/catalog/effects'),
  })
}

export function useCatalogSets(gameCode: string | null) {
  return useQuery({
    queryKey: catalogKeys.sets(gameCode ?? ''),
    queryFn: () => apiClient<ItemSet[]>(`/catalog/sets?gameCode=${encodeURIComponent(gameCode!)}`),
    enabled: Boolean(gameCode),
  })
}
