import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { apiClient, setToken } from '@/Shared/api/client'
import type { AuthResponse } from '@/Domain/types/models'

export const authKeys = {
  me: ['auth', 'me'] as const,
}

export function useAuthMe(enabled = true) {
  return useQuery({
    queryKey: authKeys.me,
    queryFn: () => apiClient<AuthResponse>('/auth/me'),
    enabled: enabled && !!localStorage.getItem('guild_points_token'),
  })
}

export function useLogin() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (body: { email: string; password: string }) =>
      apiClient<AuthResponse>('/auth/login', { method: 'POST', body }),
    onSuccess: (data) => {
      if (data.token) setToken(data.token)
      queryClient.setQueryData(authKeys.me, data)
    },
  })
}

export function useRegister() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (body: {
      email: string
      password: string
      nickname: string
      raceId: number
      classId: number
      avatarUrl?: string
    }) => apiClient<AuthResponse>('/auth/register', { method: 'POST', body }),
    onSuccess: (data) => {
      if (data.token) setToken(data.token)
      queryClient.setQueryData(authKeys.me, data)
    },
  })
}

export function useCompleteDiscordProfile() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (body: { nickname: string; raceId: number; classId: number }) =>
      apiClient<AuthResponse>('/auth/discord/complete-profile', { method: 'POST', body }),
    onSuccess: (data) => {
      if (data.token) setToken(data.token)
      queryClient.setQueryData(authKeys.me, data)
    },
  })
}
