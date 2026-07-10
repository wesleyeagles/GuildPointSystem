import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { apiClient } from '@/Shared/api/client'
import type { Objective, ObjectiveType } from '@/Domain/types/models'

export const objectiveKeys = {
  all: ['objectives'] as const,
}

export function useObjectives() {
  return useQuery({
    queryKey: objectiveKeys.all,
    queryFn: () => apiClient<Objective[]>('/objectives'),
  })
}

export function useCreateObjective() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (body: {
      name: string
      points: number
      type: ObjectiveType
      dailyLimit?: number
    }) => apiClient<Objective>('/objectives', { method: 'POST', body }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: objectiveKeys.all }),
  })
}

export function useUpdateObjective() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({
      id,
      ...body
    }: {
      id: number
      name: string
      points: number
      type: ObjectiveType
      dailyLimit?: number
    }) => apiClient<Objective>(`/objectives/${id}`, { method: 'PUT', body }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: objectiveKeys.all }),
  })
}

export function useDeleteObjective() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => apiClient<void>(`/objectives/${id}`, { method: 'DELETE' }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: objectiveKeys.all }),
  })
}
