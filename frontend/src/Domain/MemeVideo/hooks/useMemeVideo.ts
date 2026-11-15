import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { apiClient } from '@/Shared/api/client'

export const memeVideoKeys = {
  all: ['meme-video'] as const,
  optOut: ['meme-video', 'opt-out'] as const,
}

/** Vídeo do YouTube mandado para a DLL de um membro (abre no meio do jogo). */
export interface SentMemeVideo {
  id: number
  nickname: string
  sentBy: string | null
  youtubeId: string
  startSecond: number
  createdAt: string
  /** Quando a DLL abriu; null = ainda esperando (ou expirou). */
  playedAt: string | null
  /** Não abriu em 10 min (jogo fechado, raid ou CW). */
  expired: boolean
}

/** Só vem preenchido para Admin/Líder. */
export interface MemeVideoView {
  canSend: boolean
  recent: SentMemeVideo[]
}

export function useMemeVideos() {
  return useQuery({
    queryKey: memeVideoKeys.all,
    queryFn: () => apiClient<MemeVideoView>('/meme-videos'),
    staleTime: 15_000,
    refetchInterval: 20_000,
  })
}

export function useSendMemeVideo() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (body: { memberId: number; url: string }) =>
      apiClient<MemeVideoView>('/meme-videos', { method: 'POST', body }),
    onSuccess: (data) => queryClient.setQueryData(memeVideoKeys.all, data),
  })
}

export function useMemeVideoOptOut() {
  return useQuery({
    queryKey: memeVideoKeys.optOut,
    queryFn: () => apiClient<{ optOut: boolean }>('/meme-videos/opt-out'),
  })
}

export function useSetMemeVideoOptOut() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (optOut: boolean) =>
      apiClient<{ optOut: boolean }>('/meme-videos/opt-out', { method: 'PUT', body: { optOut } }),
    onSuccess: (data) => queryClient.setQueryData(memeVideoKeys.optOut, data),
  })
}
