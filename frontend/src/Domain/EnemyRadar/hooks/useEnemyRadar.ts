import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { apiClient } from '@/Shared/api/client'

export const enemyRadarKeys = { all: ['enemy-radar'] as const }

/** Guild inimiga: o nome tem que ser igual ao do jogo (maiúsculas contam). */
export interface EnemyGuild {
  id: number
  name: string
  createdAt: string
}

/** Jogador de guild inimiga visto pela DLL de um membro nos últimos minutos. */
export interface EnemySighting {
  nick: string
  guild: string
  /** Mapa e região de quem viu (último local enviado pela DLL dele). */
  map: string | null
  region: string | null
  seenBy: string
  ageSeconds: number
}

export interface EnemyRadarView {
  canConfigure: boolean
  guilds: EnemyGuild[]
  sightings: EnemySighting[]
}

export function useEnemyRadar() {
  return useQuery({
    queryKey: enemyRadarKeys.all,
    queryFn: () => apiClient<EnemyRadarView>('/enemy-radar'),
    staleTime: 5_000,
    refetchInterval: 10_000,
  })
}

export function useAddEnemyGuild() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (name: string) => apiClient<EnemyRadarView>('/enemy-radar/guilds', { method: 'POST', body: { name } }),
    onSuccess: (data) => queryClient.setQueryData(enemyRadarKeys.all, data),
  })
}

export function useRemoveEnemyGuild() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => apiClient<EnemyRadarView>(`/enemy-radar/guilds/${id}`, { method: 'DELETE' }),
    onSuccess: (data) => queryClient.setQueryData(enemyRadarKeys.all, data),
  })
}
