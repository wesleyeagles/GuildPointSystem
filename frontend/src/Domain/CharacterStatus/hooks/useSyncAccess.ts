import { useEffect } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { apiClient } from '@/Shared/api/client'
import { onSocketReconnect, subscribeTopicEffect } from '@/Shared/websocket/socketClient'
import type { CharacterStatusView } from './useCharacterStatus'

/** Situacao da DLL de sincronizacao do membro logado (aviso antes do prazo, bloqueio depois). */
export interface SyncAccessView {
  synced: boolean
  lastSyncAt: string | null
  deadline: string
  /** Prazo contado do cadastro (contas criadas depois do prazo geral). */
  newAccount: boolean
  blocked: boolean
  windowDays: number
  /** Versão da DLL no último envio (null = nunca ou cliente sem versão). */
  dllVersion: string | null
  /** Versão mais recente publicada no site (null = nenhuma). */
  latestDllVersion: string | null
  /** Sincroniza, mas com uma versão anterior à mais recente: só aviso. */
  dllOutdated: boolean
  /** Quantas versões publicadas são mais novas que a dele (0 = em dia). */
  dllReleasesBehind: number
  /** Versão mínima para entrar no site. */
  minDllVersion: string
  /** Sincroniza, mas com DLL abaixo da mínima: o site recusa as requisições até atualizar. */
  dllTooOld: boolean
}

export const syncAccessKeys = {
  me: ['character-status', 'sync-access'] as const,
}

export function useSyncAccess(memberId: number | undefined) {
  const queryClient = useQueryClient()

  const query = useQuery({
    queryKey: syncAccessKeys.me,
    queryFn: () => apiClient<SyncAccessView>('/character-status/sync-access'),
    enabled: memberId != null,
    // Sem sincronizar: confere de tempos em tempos (o websocket pode estar fora para quem esta bloqueado).
    // Bloqueado (sem sincronizar ou DLL abaixo da mínima): confere de tempos em tempos para liberar sozinho.
    refetchInterval: (q) => (q.state.data && (!q.state.data.synced || q.state.data.dllTooOld) ? 30_000 : false),
  })

  // O jogo enviou dados deste membro: libera o aviso/bloqueio na hora.
  useEffect(
    () =>
      subscribeTopicEffect('/topic/character-status', (message) => {
        const status = JSON.parse(message.body) as CharacterStatusView
        if (status.memberId === memberId) {
          queryClient.invalidateQueries({ queryKey: syncAccessKeys.me })
        }
      }),
    [queryClient, memberId],
  )
  useEffect(
    () => onSocketReconnect(() => queryClient.invalidateQueries({ queryKey: syncAccessKeys.me })),
    [queryClient],
  )

  return query
}
