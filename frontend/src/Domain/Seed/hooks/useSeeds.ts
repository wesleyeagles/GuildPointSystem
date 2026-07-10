import { useQuery } from '@tanstack/react-query'
import { apiClient } from '@/Shared/api/client'
import type { SeedOption } from '@/Domain/types/models'

interface SeedResponse {
  items: SeedOption[]
}

export const seedKeys = {
  races: ['seeds', 'races'] as const,
  classes: (raceId?: number) => ['seeds', 'classes', raceId] as const,
}

export function useRaces() {
  return useQuery({
    queryKey: seedKeys.races,
    queryFn: async () => {
      const res = await apiClient<SeedResponse>('/seeds/races')
      return res.items
    },
  })
}

export function useClasses(raceId?: number) {
  return useQuery({
    queryKey: seedKeys.classes(raceId),
    queryFn: async () => {
      const res = await apiClient<SeedResponse>(`/seeds/classes?raceId=${raceId}`)
      return res.items
    },
    enabled: raceId != null && raceId > 0,
  })
}
