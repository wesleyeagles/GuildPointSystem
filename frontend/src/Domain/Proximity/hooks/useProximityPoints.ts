import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { apiClient } from '@/Shared/api/client'
import { memberKeys } from '@/Domain/Member/hooks/useMembers'

/** Membro com a DLL no jogo agora: pode ser referência. */
export interface ProximityAnchorOption {
  memberId: number
  nickname: string
  map: string | null
}

export interface ProximityAnchorState {
  nickname: string
  answered: boolean
  nearby: number
}

export interface ProximityAwarded {
  nickname: string
  distance: number
  near: string
}

/** WAITING: esperando a DLL das referências; DONE: pontos dados; FAILED: erro ao dar os pontos. */
export interface ProximityStatus {
  id: number
  state: 'WAITING' | 'DONE' | 'FAILED'
  amount: number
  range: number
  anchors: ProximityAnchorState[]
  awarded: ProximityAwarded[]
  outsiders: number
  error: string | null
}

export const proximityKeys = {
  anchors: ['proximity-points', 'anchors'] as const,
  status: (id: number) => ['proximity-points', 'status', id] as const,
}

export function useProximityAnchors() {
  return useQuery({
    queryKey: proximityKeys.anchors,
    queryFn: () => apiClient<ProximityAnchorOption[]>('/proximity-points/anchors'),
    refetchInterval: 30_000,
  })
}

export function useCreateProximityPoints() {
  return useMutation({
    mutationFn: (body: { anchorIds: number[]; amount: number; includeAnchors: boolean; reason?: string }) =>
      apiClient<ProximityStatus>('/proximity-points', { method: 'POST', body }),
  })
}

/** Acompanha o pedido a cada 1 s até as DLLs responderem (ou o tempo acabar). */
export function useProximityStatus(id: number | null) {
  const queryClient = useQueryClient()
  return useQuery({
    queryKey: proximityKeys.status(id ?? 0),
    queryFn: async () => {
      const status = await apiClient<ProximityStatus>(`/proximity-points/${id}`)
      if (status.state === 'DONE') queryClient.invalidateQueries({ queryKey: memberKeys.all })
      return status
    },
    enabled: id != null,
    refetchInterval: (query) => (query.state.data && query.state.data.state !== 'WAITING' ? false : 1000),
  })
}
