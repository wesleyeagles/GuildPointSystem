import { useEffect, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { apiClient } from '@/Shared/api/client'
import { onSocketReconnect, subscribeTopicEffect } from '@/Shared/websocket/socketClient'

/** Integrantes por subgrupo: cada subgrupo é uma PT do jogo. */
export const RAID_GROUP_SIZE = 8

export interface RaidMember {
  id: number
  nickname: string
  className: string | null
  classImageUrl: string | null
  level: number
  group: number
  raidLeader: boolean
  /** Quem chama o subgrupo no jogo (a DLL dele envia os convites). */
  groupLeader: boolean
}

export interface Raid {
  id: number
  name: string
  leaderId: number
  leader: string
  createdAt: string
  canManage: boolean
  members: RaidMember[]
}

export interface RaidGroupInvite {
  group: number
  id: string
  leader: string
  state: 'WAITING' | 'DELIVERED' | 'COMPLETE' | 'EXPIRED'
  nicks: string[]
  skipped: string[]
  createdAt: string
  expiresAt: string
}

export const raidKeys = {
  all: ['raids'] as const,
  invites: (raidId: number) => ['raids', raidId, 'game-invite'] as const,
}

export function useRaids() {
  const queryClient = useQueryClient()
  // O servidor só avisa que algo mudou; a lista é buscada de novo.
  useEffect(
    () => subscribeTopicEffect('/topic/raids', () => queryClient.invalidateQueries({ queryKey: raidKeys.all, exact: true })),
    [queryClient],
  )
  useEffect(
    () => onSocketReconnect(() => queryClient.invalidateQueries({ queryKey: raidKeys.all, exact: true })),
    [queryClient],
  )
  return useQuery({ queryKey: raidKeys.all, queryFn: () => apiClient<Raid[]>('/raids') })
}

function useRaidMutation<V>(fn: (vars: V) => Promise<unknown>) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: fn,
    onSettled: () => queryClient.invalidateQueries({ queryKey: raidKeys.all, exact: true }),
  })
}

export const useCreateRaid = () =>
  useRaidMutation((name: string) => apiClient<Raid>('/raids', { method: 'POST', body: { name } }))

export const useDisbandRaid = () =>
  useRaidMutation((raidId: number) => apiClient<void>(`/raids/${raidId}`, { method: 'DELETE' }))

export const useAddRaidMembers = () =>
  useRaidMutation((v: { raidId: number; memberIds: number[]; group?: number }) =>
    apiClient<Raid>(`/raids/${v.raidId}/members`, { method: 'POST', body: { memberIds: v.memberIds, group: v.group } }))

export const useRemoveRaidMember = () =>
  useRaidMutation((v: { raidId: number; memberId: number }) =>
    apiClient<void>(`/raids/${v.raidId}/members/${v.memberId}`, { method: 'DELETE' }))

export const useMoveRaidMember = () =>
  useRaidMutation((v: { raidId: number; memberId: number; group: number }) =>
    apiClient<Raid>(`/raids/${v.raidId}/members/${v.memberId}/group`, { method: 'PUT', body: { group: v.group } }))

export const useTransferRaidLeader = () =>
  useRaidMutation((v: { raidId: number; memberId: number }) =>
    apiClient<Raid>(`/raids/${v.raidId}/leader/${v.memberId}`, { method: 'PUT' }))

export const useSetRaidGroupLeader = () =>
  useRaidMutation((v: { raidId: number; memberId: number }) =>
    apiClient<Raid>(`/raids/${v.raidId}/group-leader/${v.memberId}`, { method: 'PUT' }))

export function useRaidGameInvites(raidId: number, enabled: boolean) {
  return useQuery({
    queryKey: raidKeys.invites(raidId),
    queryFn: () => apiClient<RaidGroupInvite[]>(`/raids/${raidId}/game-invite`),
    enabled,
    refetchInterval: (query) =>
      query.state.data?.some((i) => i.state === 'WAITING' || i.state === 'DELIVERED') ? 3000 : 15000,
  })
}

export function useCallRaidInGame(raidId: number) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: () => apiClient<RaidGroupInvite[]>(`/raids/${raidId}/game-invite`, { method: 'POST' }),
    onSuccess: (data) => queryClient.setQueryData(raidKeys.invites(raidId), data),
  })
}

/** Subgrupos em ordem, com os integrantes de cada um (o líder do subgrupo vem primeiro, como o servidor manda). */
export function raidGroups(raid: Raid): { group: number; members: RaidMember[] }[] {
  const map = new Map<number, RaidMember[]>()
  for (const m of raid.members) map.set(m.group, [...(map.get(m.group) ?? []), m])
  return [...map.entries()].sort(([a], [b]) => a - b).map(([group, members]) => ({ group, members }))
}

/** Primeiro número de subgrupo sem ninguém: onde soltar para abrir um subgrupo novo. */
export function nextFreeGroup(raid: Raid): number {
  const used = new Set(raid.members.map((m) => m.group))
  let group = 1
  while (used.has(group)) group++
  return group
}

/** HP/FP/SP enviados pela DLL de cada integrante. ageMs = idade no momento em que o servidor respondeu (-1 = nunca). */
export interface RaidLiveMember {
  memberId: number
  level: number
  hp: number
  hpMax: number
  fp: number
  fpMax: number
  sp: number
  spMax: number
  ageMs: number
}

export interface RaidLive {
  /** Date.now() quando chegou: a idade de cada envio continua contando a partir daqui. */
  receivedAt: number
  members: Map<number, RaidLiveMember>
}

/** Sem envio há mais que isso, o integrante aparece fora do jogo (a DLL envia a cada segundo). */
export const RAID_LIVE_ONLINE_MS = 5000

function toLive(list: RaidLiveMember[]): RaidLive {
  return { receivedAt: Date.now(), members: new Map(list.map((m) => [m.memberId, m])) }
}

export function isRaidMemberOnline(member: RaidLiveMember | undefined, receivedAt: number, now: number): boolean {
  return !!member && member.ageMs >= 0 && member.ageMs + (now - receivedAt) < RAID_LIVE_ONLINE_MS
}

export function useRaidLive(raidId: number) {
  const queryClient = useQueryClient()
  const key = ['raids', raidId, 'live'] as const
  useEffect(
    () =>
      subscribeTopicEffect(`/topic/raids/${raidId}/live`, (message) => {
        try {
          queryClient.setQueryData(['raids', raidId, 'live'], toLive(JSON.parse(message.body) as RaidLiveMember[]))
        } catch {
          // mensagem inválida: espera a próxima
        }
      }),
    [queryClient, raidId],
  )
  return useQuery({
    queryKey: key,
    queryFn: async () => toLive(await apiClient<RaidLiveMember[]>(`/raids/${raidId}/live`)),
    // Quem fica fora do jogo não gera mensagem: a busca periódica atualiza essas idades.
    refetchInterval: 15000,
  })
}

/** Re-renderiza a cada segundo, para quem parou de enviar sair do ar sem esperar mensagem nova. */
export function useNowTick(intervalMs = 1000) {
  const [now, setNow] = useState(() => Date.now())
  useEffect(() => {
    const id = setInterval(() => setNow(Date.now()), intervalMs)
    return () => clearInterval(id)
  }, [intervalMs])
  return now
}
