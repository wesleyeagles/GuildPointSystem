import { useQuery } from '@tanstack/react-query'
import { apiClient } from '@/Shared/api/client'
import type { SeedOption } from '@/Domain/types/models'

interface SeedResponse {
  items: SeedOption[]
}

export type ItemSeedCategory =
  | 'WEAPON_RARITY'
  | 'ARMOR_RARITY'
  | 'WEAPON_SUBTYPE'
  | 'ARMOR_SUBTYPE'
  | 'ARMOR_CLASS'
  | 'ACCESSORY_SUBTYPE'
  | 'WEAPON_CAST'

export const itemSeedKeys = {
  category: (category: ItemSeedCategory) => ['seeds', 'item', category] as const,
}

export function useItemSeeds(category: ItemSeedCategory, enabled = true) {
  return useQuery({
    queryKey: itemSeedKeys.category(category),
    queryFn: async () => {
      const res = await apiClient<SeedResponse>(`/seeds/item?category=${category}`)
      return res.items
    },
    enabled,
  })
}
