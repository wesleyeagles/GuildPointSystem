import { useEffect, useState, type CSSProperties } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { apiClient } from '@/Shared/api/client'
import { onSocketReconnect, subscribeTopicEffect } from '@/Shared/websocket/socketClient'
import type { Role } from '@/Domain/types/models'

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
  /** Marca de raid 1..8 (estrela, círculo, losango, triângulo, lua, quadrado, X, caveira); null = nenhuma. */
  mark: number | null
  /** Oculto: a liderança acompanha o status, mas ele não vê a raid (só aparece para quem gerencia). */
  hidden: boolean
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
  access: ['raids', 'access'] as const,
  invites: (raidId: number) => ['raids', raidId, 'game-invite'] as const,
}

/** Quem vê a aba Raid. minRole e viewers só vêm para quem pode configurar (Administrador+). */
export interface RaidAccess {
  canView: boolean
  canConfigure: boolean
  minRole: Role | null
  viewers: { id: number; nickname: string }[]
}

export function useRaidAccess() {
  return useQuery({
    queryKey: raidKeys.access,
    queryFn: () => apiClient<RaidAccess>('/raids/access'),
    staleTime: 60_000,
    refetchOnWindowFocus: true,
  })
}

export function useUpdateRaidAccess() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (body: { minRole: Role; viewerIds: number[] }) =>
      apiClient<RaidAccess>('/raids/access', { method: 'PUT', body }),
    onSuccess: (data) => queryClient.setQueryData(raidKeys.access, data),
  })
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
  useRaidMutation((v: { raidId: number; memberIds: number[]; group?: number; hidden?: boolean }) =>
    apiClient<Raid>(`/raids/${v.raidId}/members`, {
      method: 'POST',
      // Sem hidden, o servidor põe oculto (padrão): a liderança acompanha e o membro não vê a raid.
      body: { memberIds: v.memberIds, group: v.group, hidden: v.hidden },
    }))

export const useRemoveRaidMember = () =>
  useRaidMutation((v: { raidId: number; memberId: number }) =>
    apiClient<void>(`/raids/${v.raidId}/members/${v.memberId}`, { method: 'DELETE' }))

export const useMoveRaidMember = () =>
  useRaidMutation((v: { raidId: number; memberId: number; group: number }) =>
    apiClient<Raid>(`/raids/${v.raidId}/members/${v.memberId}/group`, { method: 'PUT', body: { group: v.group } }))

export const useTransferRaidLeader = () =>
  useRaidMutation((v: { raidId: number; memberId: number }) =>
    apiClient<Raid>(`/raids/${v.raidId}/leader/${v.memberId}`, { method: 'PUT' }))

export const useSetRaidHidden = () =>
  useRaidMutation((v: { raidId: number; memberId: number; hidden: boolean }) =>
    apiClient<Raid>(`/raids/${v.raidId}/members/${v.memberId}/hidden`, { method: 'PUT', body: { hidden: v.hidden } }))

export const useSetRaidMark = () =>
  useRaidMutation((v: { raidId: number; memberId: number; mark: number | null }) =>
    apiClient<Raid>(`/raids/${v.raidId}/members/${v.memberId}/mark`, { method: 'PUT', body: { mark: v.mark } }))

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
  /** Buffs/debuffs ativos, lidos pela DLL do integrante (vazio em DLL anterior à 1.7.0). */
  effects: RaidEffect[]
}

/** type: 0 skill, 1 force, 2 skill de classe (como no jogo). */
export interface RaidEffect {
  type: number
  index: number
  level: number
  debuff: boolean
  /** Célula na folha de ícones do tipo (/effect-icons); -1 = desconhecido (DLL sem ícone). */
  icon: number
  name: string
}

/**
 * Folhas de ícones extraídas do cliente (RF-ui: CerberusBags/tools/effect_icons.py), células de 64 px,
 * da esquerda para a direita e de cima para baixo. Índice = tipo do efeito.
 */
const EFFECT_SHEETS = [
  { url: '/effect-icons/skill.png', columns: 8, rows: 8 },
  { url: '/effect-icons/force.png', columns: 16, rows: 8 },
  { url: '/effect-icons/class.png', columns: 16, rows: 16 },
]

/** Recorte do ícone na folha do jogo, no tamanho pedido; null se o efeito não tem ícone conhecido. */
export function effectIconStyle(effect: RaidEffect, size: number): CSSProperties | null {
  const sheet = EFFECT_SHEETS[effect.type]
  if (!sheet || effect.icon == null || effect.icon < 0 || effect.icon >= sheet.columns * sheet.rows) return null
  const column = effect.icon % sheet.columns
  const row = Math.floor(effect.icon / sheet.columns)
  return {
    backgroundImage: `url(${sheet.url})`,
    backgroundSize: `${sheet.columns * size}px ${sheet.rows * size}px`,
    backgroundPosition: `-${column * size}px -${row * size}px`,
  }
}

/** Ícones de force que o site já tem em /forces, pelo nome em minúsculas com hífens. */
const FORCE_ICONS = new Set([
  'acute', 'adept', 'aegis', 'agility', 'air-blast', 'aqua-blade', 'blaze-lance', 'blaze-pearl',
  'blind-sight', 'celerity', 'chain-lightning', 'circle-of-fire', 'conservation', 'conservation-dark',
  'contain', 'counter', 'dispel', 'energize', 'enfeeblement', 'entangle', 'fire-ball', 'fire-bomb',
  'fire-wall', 'flame-arrow', 'flash-beam', 'focus', 'frost-arrow', 'frost-nova', 'geyser', 'healing',
  'ice-shard', 'individual-cancellation-skill-revoker', 'lightning-bolt', 'meteor-swarm',
  'offensive-cancellation-force-revoker', 'poison-bloom', 'poison-mist', 'prism-shard', 'radiant-fall',
  'raging-wave', 'refresh', 'repress', 'resistance', 'restrain', 'rock-blade', 'sacrifice', 'sand-storm',
  'sight', 'sloth', 'slow', 'solar-blade', 'soul-vitality', 'spark', 'stifle', 'suppress', 'tempo',
  'torment', 'vein-fall', 'velocity', 'velocity-speed', 'venom', 'vista', 'vulnerability', 'weakness',
  'wind-blast', 'wind-shear',
])

/** Buff de velocidade: as forces "Velocity" e "Velocity Speed". Sem ele, a raid mostra um "!" no nick (como a DLL). */
export function hasSpeedBuff(effects: readonly RaidEffect[] | undefined): boolean {
  return !!effects?.some((e) => !e.debuff && e.name.toLowerCase().startsWith('velocity'))
}

export function effectSlug(name: string): string {
  return name.toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-|-$/g, '')
}

/** Reserva quando o efeito não traz ícone: a imagem da force pelo nome, se o site tiver; senão null (inicial). */
export function effectIconUrl(effect: RaidEffect): string | null {
  const slug = effectSlug(effect.name)
  return effect.type === 1 && FORCE_ICONS.has(slug) ? `/forces/${slug}.png` : null
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

/** full: quem gerencia a raid recebe também os integrantes ocultos (canal /live/all). */
export function useRaidLive(raidId: number, full = false) {
  const queryClient = useQueryClient()
  const key = ['raids', raidId, 'live'] as const
  useEffect(
    () =>
      subscribeTopicEffect(`/topic/raids/${raidId}/live${full ? '/all' : ''}`, (message) => {
        try {
          queryClient.setQueryData(['raids', raidId, 'live'], toLive(JSON.parse(message.body) as RaidLiveMember[]))
        } catch {
          // mensagem inválida: espera a próxima
        }
      }),
    [queryClient, raidId, full],
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
