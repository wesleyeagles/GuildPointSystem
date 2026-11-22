import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { apiClient } from '@/Shared/api/client'
import { gameMailRequest } from '@/Shared/hooks/useGameMail'

export type TeleportKind = 'taraven' | 'ether' | 'cash499'
export interface EventReward { name: string; claims: Record<string, number[]> }
export interface RewardRecipients { name: string; recipients: { memberId: number; nickname: string; claimIds: number[] }[] }
export interface TeleportRequest { id: string; sender: string; recipients: string[]; items: { kind: TeleportKind; quantity: number }[]; reward?: EventReward }
export interface TeleportLetter { recipient: string; name: string; itemCode: string; quantity: number; state: string; tracking: string; claimIds?: number[]; sentAt?: number }
export interface TeleportBatch { id: string; sender: string; state: string; control: string | null; error: string | null; createdAt: number; request?: TeleportRequest; letters: TeleportLetter[] }
const key = ['teleport-mail']
const terminal = new Set(['completed', 'cancelled', 'uncertain'])
export function batchActive(batch: TeleportBatch) { return !terminal.has(batch.state) && batch.state !== 'planned' }
async function request<T>(path: Parameters<typeof gameMailRequest>[0], body: object): Promise<T> {
  const response = await gameMailRequest(path, body)
  const value = await response.json()
  if (!response.ok) throw new Error(value.message || 'A ponte não confirmou a operação. Confira o andamento antes de tentar novamente.')
  return value
}
export function useEventRewardRecipients(claimIds: number[], enabled: boolean) {
  return useQuery({
    queryKey: ['event-reward-recipients', claimIds], enabled,
    queryFn: () => apiClient<RewardRecipients>('/events/claims/recipients', { method: 'POST', body: { claimIds } }),
    staleTime: 0, retry: false,
  })
}

export function useTeleportMail(open: boolean) {
  const queries = useQueryClient()
  const batches = useQuery({ queryKey: key, queryFn: () => request<{ batches: TeleportBatch[]; simulation?: boolean }>('teleport-status', {}),
    enabled: open, retry: false, staleTime: 0, refetchOnWindowFocus: false,
    refetchInterval: query => query.state.data?.batches.some(batchActive) ? 1500 : false })
  const start = useMutation({ mutationFn: async (data: TeleportRequest) => {
    if (data.reward) {
      const latest = await apiClient<RewardRecipients>('/events/claims/recipients', { method: 'POST', body: { claimIds: Object.values(data.reward.claims).flat() } })
      for (const name of data.recipients) {
        const recipient = latest.recipients.find(row => row.nickname === name)
        if (!recipient || recipient.claimIds.some(id => !data.reward!.claims[name]?.includes(id)) || recipient.claimIds.length !== data.reward.claims[name]?.length)
          throw new Error('Os participantes mudaram. Feche e atualize o evento antes de enviar.')
      }
    }
    await request<TeleportBatch>('teleport-plan', data)
    return request<TeleportBatch>('teleport-start', { id: data.id })
  }, retry: false, onSettled: () => { void queries.invalidateQueries({ queryKey: key }) } })
  const control = useMutation({ mutationFn: (data: { id: string; action: 'pause' | 'resume' | 'cancel' }) => request<TeleportBatch>('teleport-control', data),
    retry: false, onSettled: () => { void queries.invalidateQueries({ queryKey: key }) } })
  return { batches, start, control }
}
