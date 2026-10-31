import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { apiClient } from '@/Shared/api/client'
import { memberKeys } from '@/Domain/Member/hooks/useMembers'

/** Só quem está na lista de espera da Party, ou qualquer membro no local. */
export type LocationScope = 'QUEUE' | 'ALL'
/** Prioridade da fila da Party, ou pontos gerais do site (ranking/leilões). */
export type LocationPointsType = 'PRIORITY' | 'GENERAL'

/** Um mapa/região com gente agora (o que a DLL enviou nos últimos 3 minutos). */
export interface LocationOption {
  map: string | null
  region: string | null
  members: number
}

export interface LocationMatch {
  memberId: number
  nickname: string
  characterName: string
  map: string | null
  region: string | null
  mapX: number | null
  mapY: number | null
  locationAt: string
  inQueue: boolean
}

export interface LocationFilter {
  map: string | null
  region: string | null
  scope: LocationScope
}

export interface LocationAwardResult {
  awarded: number
  nicknames: string[]
  skipped: string[]
}

export const locationKeys = {
  locations: ['location-points', 'locations'] as const,
  preview: (filter: LocationFilter) => ['location-points', 'preview', filter] as const,
}

export function useCurrentLocations() {
  return useQuery({
    queryKey: locationKeys.locations,
    queryFn: () => apiClient<LocationOption[]>('/location-points/locations'),
    refetchInterval: 30_000,
  })
}

/** Prévia de quem está no local agora; só busca com mapa ou região escolhidos. */
export function useLocationPreview(filter: LocationFilter) {
  return useQuery({
    queryKey: locationKeys.preview(filter),
    queryFn: () => apiClient<LocationMatch[]>('/location-points/preview', { method: 'POST', body: filter }),
    enabled: Boolean(filter.map || filter.region),
    refetchInterval: 30_000,
  })
}

export function useAwardLocationPoints() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (
      body: LocationFilter & {
        pointsType: LocationPointsType
        amount: number
        memberIds: number[]
        reason?: string
        /** Nome do mapa como a guild chama, para o motivo no log. */
        mapLabel?: string
      },
    ) => apiClient<LocationAwardResult>('/location-points/award', { method: 'POST', body }),
    onSuccess: (_, body) => {
      queryClient.invalidateQueries({ queryKey: ['location-points'] })
      if (body.pointsType === 'GENERAL') queryClient.invalidateQueries({ queryKey: memberKeys.all })
    },
  })
}
