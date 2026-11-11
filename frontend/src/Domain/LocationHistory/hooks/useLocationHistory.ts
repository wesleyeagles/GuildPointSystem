import { useQuery } from '@tanstack/react-query'
import { apiClient } from '@/Shared/api/client'

/** Um envio de status da DLL guardado no histórico (3 dias). */
export interface LocationRow {
  memberId: number
  nickname: string
  character: string | null
  at: string
  map: string | null
  region: string | null
  x: number | null
  y: number | null
  /** Parado sem EXP há quanto tempo nesse envio (null = sem coordenada). */
  idleSeconds: number | null
  dllVersion: string | null
}

export interface LocationQuery {
  at: string
  nickname: string
  minutes: number
}

/** Sem nick: onde cada membro estava no horário. Com nick: os envios dele em volta do horário. */
export function useLocationHistory(query: LocationQuery | null) {
  return useQuery({
    queryKey: ['location-history', query],
    enabled: query !== null,
    queryFn: () => {
      const q = query as LocationQuery
      const params = new URLSearchParams({ at: q.at, minutes: String(q.minutes) })
      if (q.nickname.trim()) params.set('nickname', q.nickname.trim())
      return apiClient<LocationRow[]>(`/location-history?${params}`)
    },
  })
}
