import { useInfiniteQuery, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { apiClient } from '@/Shared/api/client'

export type PbBossKind = 'ROTACAO' | 'EXTRA'

export const PB_KIND_LABEL: Record<PbBossKind, string> = { ROTACAO: 'Rotação', EXTRA: 'Extra' }

/** Boss acompanhado pela DLL, pelo nome como o jogo escreve (sem o "[nível]"). label = apelido da guild. */
export interface PbBoss {
  name: string
  label: string | null
  kind: PbBossKind
  kills: number
  lastKillAt: string | null
}

/** Monstro visto logo depois de um horário de PB: a liderança escolhe se é boss. */
export interface PbCandidate {
  name: string
  mapName: string | null
  seenCount: number
  lastSeenAt: string
}

export interface PbOverview {
  canView: boolean
  bosses: PbBoss[]
  candidates: PbCandidate[]
}

/** present = viu o boss vivo e estava perto na morte; false = só viu. */
export interface PbPresence {
  memberId: number
  nickname: string
  firstSeenAt: string
  lastSeenAt: string
  present: boolean
}

export interface PbKill {
  id: number
  boss: string
  label: string | null
  kind: PbBossKind
  mapName: string | null
  firstSeenAt: string
  /** null = ninguém viu a morte (sumiu longe, ou ainda vivo). */
  diedAt: string | null
  /** Menor HP visto (0..100); null = a DLL não leu. */
  minHp: number | null
  members: PbPresence[]
}

export interface PbAttendance {
  days: number
  kills: number
  rows: { memberId: number; nickname: string; present: number; seen: number }[]
}

const PAGE = 30

export const pbKeys = {
  overview: ['pb-history'] as const,
  kills: (boss: string, memberId: number | null) => ['pb-history', 'kills', boss, memberId] as const,
  attendance: (days: number) => ['pb-history', 'attendance', days] as const,
}

export function usePbOverview() {
  return useQuery({
    queryKey: pbKeys.overview,
    queryFn: () => apiClient<PbOverview>('/pb-history'),
    staleTime: 60_000,
  })
}

export function usePbKills(boss: string, memberId: number | null) {
  return useInfiniteQuery({
    queryKey: pbKeys.kills(boss, memberId),
    initialPageParam: null as number | null,
    queryFn: ({ pageParam }) => {
      const params = new URLSearchParams({ limit: String(PAGE) })
      if (boss) params.set('boss', boss)
      if (memberId != null) params.set('memberId', String(memberId))
      if (pageParam != null) params.set('before', String(pageParam))
      return apiClient<PbKill[]>(`/pb-history/kills?${params}`)
    },
    getNextPageParam: (last) => (last.length < PAGE ? null : last[last.length - 1].id),
    // Boss vivo agora: a lista de presentes muda durante a luta.
    refetchInterval: 30_000,
  })
}

export function usePbAttendance(days: number) {
  return useQuery({
    queryKey: pbKeys.attendance(days),
    queryFn: () => apiClient<PbAttendance>(`/pb-history/attendance?days=${days}`),
    staleTime: 60_000,
  })
}

function usePbMutation<V>(request: (vars: V) => Promise<PbOverview>) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: request,
    onSuccess: (data) => {
      queryClient.setQueryData(pbKeys.overview, data)
      queryClient.invalidateQueries({ queryKey: ['pb-history', 'kills'] })
    },
  })
}

export function useSavePbBoss() {
  return usePbMutation((body: { name: string; label: string | null; kind: PbBossKind }) =>
    apiClient<PbOverview>('/pb-history/bosses', { method: 'PUT', body }))
}

export function useRemovePbBoss() {
  return usePbMutation((name: string) =>
    apiClient<PbOverview>(`/pb-history/bosses?name=${encodeURIComponent(name)}`, { method: 'DELETE' }))
}

export function useIgnorePbCandidate() {
  return usePbMutation((name: string) =>
    apiClient<PbOverview>('/pb-history/candidates/ignore', { method: 'PUT', body: { name } }))
}
