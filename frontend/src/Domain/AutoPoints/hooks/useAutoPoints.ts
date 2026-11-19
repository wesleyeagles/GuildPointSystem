import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { apiClient } from '@/Shared/api/client'

/** O formulário de uma regra de pontos automáticos. Horários "HH:mm" de Brasília; dias 1 = seg … 7 = dom. */
export interface AutoPointRuleInput {
  label: string
  enabled: boolean
  weekdays: number[]
  /** Minutos antes do início (RG, ou começo da presença) em que o "Sistema" manda o shout. */
  shoutsBefore: number[]
  shout: string | null
  shoutEn: string | null
  rgEnabled: boolean
  rgAt: string | null
  rgMap: string | null
  /** Vazio = o mapa inteiro. */
  rgRegions: string[]
  rgPoints: number
  presenceEnabled: boolean
  presenceFrom: string | null
  presenceTo: string | null
  presenceMap: string | null
  presenceRegions: string[]
  objectiveName: string | null
}

export interface AutoPointRun {
  date: string
  rg: number
  objective: number
}

export interface AutoPointRule extends AutoPointRuleInput {
  id: number
  code: string
  /** O objetivo da presença não existe na aba Objetivos: ninguém recebe. */
  objectiveMissing: boolean
  /** Últimas rodadas (7 dias), da mais nova para a mais velha. */
  lastRuns: AutoPointRun[]
}

export interface AutoPointMap {
  map: string
  regions: string[]
}

const keys = { rules: ['auto-points'] as const, maps: ['auto-points', 'maps'] as const }

export function useAutoPointRules() {
  return useQuery({ queryKey: keys.rules, queryFn: () => apiClient<AutoPointRule[]>('/auto-points'), refetchInterval: 60_000 })
}

export function useAutoPointMaps() {
  return useQuery({ queryKey: keys.maps, queryFn: () => apiClient<AutoPointMap[]>('/auto-points/maps'), staleTime: 5 * 60_000 })
}

export function useSaveAutoPointRule() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ id, body }: { id?: number; body: AutoPointRuleInput }) =>
      id
        ? apiClient<AutoPointRule>(`/auto-points/${id}`, { method: 'PUT', body })
        : apiClient<AutoPointRule>('/auto-points', { method: 'POST', body }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: keys.rules }),
  })
}

export function useDeleteAutoPointRule() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => apiClient<void>(`/auto-points/${id}`, { method: 'DELETE' }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: keys.rules }),
  })
}
