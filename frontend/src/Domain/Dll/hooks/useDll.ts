import { useEffect } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { API_BASE, ApiError, apiClient, getToken } from '@/Shared/api/client'
import { formatApiError } from '@/Shared/utils/formatApiError'
import { onSocketReconnect, subscribeTopicEffect } from '@/Shared/websocket/socketClient'
import { syncAccessKeys } from '@/Domain/CharacterStatus/hooks/useSyncAccess'

/** Versão da DLL publicada pela administração. */
export interface DllRelease {
  id: number
  version: string
  notes: string | null
  fileName: string
  fileSize: number
  publishedBy: string | null
  publishedAt: string
  /** Publicada com o version.dll assinado: as DLLs instaladas se atualizam sozinhas. */
  autoUpdate: boolean
}

/** Versão da DLL de cada membro (painel da administração). */
export interface MemberDll {
  memberId: number
  nickname: string
  dllVersion: string | null
  lastSyncAt: string | null
  outdated: boolean
}

export const dllKeys = {
  latest: ['dll', 'latest'] as const,
  releases: ['dll', 'releases'] as const,
  members: ['dll', 'members'] as const,
}

/** Antes da primeira publicação no site, o download usa o arquivo de frontend/public. */
const FALLBACK_DOWNLOAD_URL = '/version.rar?v=1.0.0'

/** Link do botão "Download DLL": sempre a versão mais recente publicada. */
export function dllDownloadUrl(latest: DllRelease | null | undefined) {
  return latest ? `${API_BASE}/dll/download?v=${encodeURIComponent(latest.version)}` : FALLBACK_DOWNLOAD_URL
}

export function useLatestDll(enabled = true) {
  return useQuery({
    queryKey: dllKeys.latest,
    // 204 enquanto nada foi publicado: apiClient devolve undefined.
    queryFn: async () => (await apiClient<DllRelease | undefined>('/dll/latest')) ?? null,
    enabled,
    staleTime: 5 * 60_000,
  })
}

/** Nova versão publicada: atualiza o aviso de todo mundo na hora. */
export function useDllReleaseSubscription() {
  const queryClient = useQueryClient()
  useEffect(() => {
    const refresh = () => {
      queryClient.invalidateQueries({ queryKey: ['dll'] })
      queryClient.invalidateQueries({ queryKey: syncAccessKeys.me })
    }
    const unsubscribe = subscribeTopicEffect('/topic/dll-release', refresh)
    const offReconnect = onSocketReconnect(refresh)
    return () => {
      unsubscribe()
      offReconnect()
    }
  }, [queryClient])
}

export function useDllReleases() {
  return useQuery({ queryKey: dllKeys.releases, queryFn: () => apiClient<DllRelease[]>('/dll/releases') })
}

export function useMemberDlls() {
  return useQuery({ queryKey: dllKeys.members, queryFn: () => apiClient<MemberDll[]>('/dll/members') })
}

export function usePublishDll() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: async ({
      version,
      notes,
      dll,
      signature,
      file,
    }: {
      version: string
      notes: string
      /** version.dll e version.dll.sig (tools/dll-signing.ps1): atualização automática. */
      dll: File | null
      signature: File | null
      /** .rar/.zip para download manual; opcional com a DLL assinada (o site gera um .zip). */
      file: File | null
    }) => {
      const formData = new FormData()
      formData.append('version', version)
      if (notes.trim()) formData.append('notes', notes)
      if (dll) formData.append('dll', dll)
      if (signature) formData.append('signature', signature)
      if (file) formData.append('file', file)
      const token = getToken()
      const response = await fetch(`${API_BASE}/dll/releases`, {
        method: 'POST',
        headers: token ? { Authorization: `Bearer ${token}` } : {},
        body: formData,
      })
      if (!response.ok) {
        let message = response.statusText
        try {
          const err = (await response.json()) as { message?: string }
          if (err.message) message = err.message
        } catch {
          /* ignore */
        }
        throw new ApiError(formatApiError(message), response.status)
      }
      return (await response.json()) as DllRelease
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['dll'] })
      queryClient.invalidateQueries({ queryKey: syncAccessKeys.me })
    },
  })
}
