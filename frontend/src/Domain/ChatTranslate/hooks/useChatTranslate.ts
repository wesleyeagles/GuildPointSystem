import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { apiClient } from '@/Shared/api/client'

export const chatTranslateKeys = {
  access: ['chat-translate', 'access'] as const,
}

export interface ChatGlossaryTerm {
  id: number
  /** Termo em russo, normalizado (minúsculas, ё = е). */
  source: string
  translation: string
}

/**
 * Uso de hoje (dia de Brasília). apiChars é o que conta na cota do MyMemory; dailyLimit 0 = desconhecido.
 * quotaPausedUntil: a cota acabou e o site só volta a chamar a API depois dessa hora.
 */
export interface ChatTranslateUsage {
  day: string
  apiChars: number
  dailyLimit: number
  apiCalls: number
  savedHits: number
  glossaryHits: number
  failed: number
  quotaPausedUntil: string | null
}

/**
 * Tradutor do chat no jogo (DLL). Listas e contagem só vêm para quem pode configurar (Administrador+).
 * savedTranslations = frases que a API já traduziu e ficaram no dicionário (não gastam cota de novo).
 */
export interface ChatTranslateAccess {
  canConfigure: boolean
  translators: { id: number; nickname: string }[]
  glossary: ChatGlossaryTerm[]
  savedTranslations: number
  usage: ChatTranslateUsage | null
}

export function useChatTranslateAccess() {
  return useQuery({
    queryKey: chatTranslateKeys.access,
    queryFn: () => apiClient<ChatTranslateAccess>('/chat-translate/access'),
    staleTime: 60_000,
    refetchInterval: 60_000, // o uso da cota muda enquanto a página está aberta
  })
}

function useChatTranslateMutation<V>(request: (vars: V) => Promise<ChatTranslateAccess>) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: request,
    onSuccess: (data) => queryClient.setQueryData(chatTranslateKeys.access, data),
  })
}

export function useUpdateChatTranslateAccess() {
  return useChatTranslateMutation((body: { memberIds: number[] }) =>
    apiClient<ChatTranslateAccess>('/chat-translate/access', { method: 'PUT', body }))
}

/** Cria ou troca um termo do glossário (o mesmo termo em russo substitui a tradução anterior). */
export function useSaveChatGlossaryTerm() {
  return useChatTranslateMutation((body: { source: string; translation: string }) =>
    apiClient<ChatTranslateAccess>('/chat-translate/glossary', { method: 'PUT', body }))
}

export function useDeleteChatGlossaryTerm() {
  return useChatTranslateMutation((id: number) =>
    apiClient<ChatTranslateAccess>(`/chat-translate/glossary/${id}`, { method: 'DELETE' }))
}
