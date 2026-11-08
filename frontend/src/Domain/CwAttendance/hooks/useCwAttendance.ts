import { useQuery } from '@tanstack/react-query'
import { apiClient } from '@/Shared/api/client'

/** Um membro numa CW: tempo no mapa da CW (Crag Mind Field) sem AFK, e o tempo AFK à parte (segundos). */
export interface CwMember {
  memberId: number
  nickname: string
  character: string | null
  activeSeconds: number
  afkSeconds: number
  /** Primeiro e último envio da DLL no mapa da CW. */
  firstSeenAt: string
  lastSeenAt: string
}

export interface Cw {
  date: string
  slot: number
  label: string
  startsAt: string
  endsAt: string
  members: CwMember[]
}

export interface CwAttendance {
  days: number
  cws: Cw[]
}

export function useCwAttendance(days: number) {
  return useQuery({
    queryKey: ['cw-attendance', days] as const,
    queryFn: () => apiClient<CwAttendance>(`/cw-attendance?days=${days}`),
    staleTime: 30_000,
    refetchInterval: 60_000,
  })
}
