import { useQuery } from '@tanstack/react-query'
import { apiClient } from '@/Shared/api/client'
import { useAuthContext } from '@/Features/Auth/contexts/AuthContext'

export type GuildBdHistory = {
  day: string; timezone: string; pendingCode: string
  members: { character: string; completedAt: string | null }[]
}
export function useGuildBd(day: string) {
  const { user } = useAuthContext()
  return useQuery({ queryKey: ['guild-bd', user?.memberId, day], enabled: !!user?.memberId && user.status === 'APROVADO' && user.profileComplete && !!day,
    queryFn: () => apiClient<GuildBdHistory>(`/quests/bd?${new URLSearchParams({ day })}`),
    refetchInterval: 30_000, staleTime: 15_000 })
}
