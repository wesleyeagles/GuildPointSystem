import { useQuery } from '@tanstack/react-query'
import { apiClient } from '@/Shared/api/client'
import type { EffectDefinition, GameAccessory, GameArmor, ItemSet } from '@/Domain/types/models'

export interface CatalogAccessoryFilters {
  subtype?: 'RING' | 'AMULET'
  grade?: number
  civilMask?: string
  search?: string
}

export interface CatalogArmorFilters {
  slot?: GameArmor['slot']
  grade?: number
  civilMask?: string
  minLevel?: number
  search?: string
}

export const catalogKeys = {
  accessories: (filters: CatalogAccessoryFilters) =>
    ['catalog', 'accessories', filters] as const,
  accessory: (gameCode: string) => ['catalog', 'accessories', gameCode] as const,
  armor: (filters: CatalogArmorFilters) => ['catalog', 'armor', filters] as const,
  armorItem: (gameCode: string) => ['catalog', 'armor', gameCode] as const,
  effects: ['catalog', 'effects'] as const,
  sets: (gameCode: string) => ['catalog', 'sets', gameCode] as const,
  allSets: ['catalog', 'sets', 'all'] as const,
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

export function useAllCatalogSets() {
  return useQuery({
    queryKey: catalogKeys.allSets,
    queryFn: () => apiClient<ItemSet[]>('/catalog/sets'),
    staleTime: 5 * 60 * 1000,
  })
}

export function useAllCatalogAccessories() {
  return useQuery({
    queryKey: ['catalog', 'accessories', 'all'] as const,
    queryFn: () => apiClient<GameAccessory[]>('/catalog/accessories'),
    staleTime: 5 * 60 * 1000,
  })
}

export function useCatalogArmor(filters: CatalogArmorFilters) {
  const params = new URLSearchParams()
  if (filters.slot) params.set('slot', filters.slot)
  if (filters.grade != null) params.set('grade', String(filters.grade))
  if (filters.civilMask) params.set('civilMask', filters.civilMask)
  if (filters.minLevel != null) params.set('minLevel', String(filters.minLevel))
  if (filters.search) params.set('search', filters.search)

  const query = params.toString()

  return useQuery({
    queryKey: catalogKeys.armor(filters),
    queryFn: async () => {
      const path = query ? `/catalog/armor?${query}` : '/catalog/armor'
      return apiClient<GameArmor[]>(path)
    },
  })
}

export function useAllCatalogArmor() {
  return useQuery({
    queryKey: ['catalog', 'armor', 'all'] as const,
    queryFn: () => apiClient<GameArmor[]>('/catalog/armor'),
    staleTime: 5 * 60 * 1000,
  })
}
