import { useEffect } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { onSocketReconnect, subscribeTopicEffect } from '@/Shared/websocket/socketClient'
import { apiClient } from '@/Shared/api/client'
import { serverNow } from '@/Shared/utils/serverClock'

export const resourceKeys = {
  overview: ['resources'] as const,
  spawns: (map: number) => ['resources', 'spawns', map] as const,
}

/**
 * Escala da imagem do mapa grande (400x400 do cliente), como a janela do mapa do jogo calcula:
 * px = offX + (x - minX) * scaleX; py = offY + (maxZ - z) * scaleY. Vem da DLL; null = ainda não chegou.
 */
export interface ResourceMapScale {
  areaW: number
  areaH: number
  offX: number
  offY: number
  minX: number
  maxZ: number
  scaleX: number
  scaleY: number
}

export interface ResourceMap {
  /** Índice do mapa no jogo (11 Sette, 2 Crag, 10 Ether): também o nome da imagem em /resource-maps. */
  code: number
  name: string
  view: ResourceMapScale | null
}

/** tier 1..3 = grau; 4 = Tower Rift Metal; 0 = não é recurso (ignorado); null = desconhecido (a liderança ainda não escolheu). */
export interface ResourceMob {
  name: string
  map: number
  tier: number | null
  spawns: number
  firstSeenAt: string
}

/** Página só de Administrador+: canView = canConfigure. trackers = quem vê as bolinhas no jogo. */
export interface ResourceOverview {
  canView: boolean
  canConfigure: boolean
  maps: ResourceMap[]
  mobs: ResourceMob[]
  trackers: { id: number; nickname: string }[]
}

export interface ResourceSpawn {
  id: number
  name: string
  tier: number | null
  x: number
  z: number
  foundAt: string
  foundBy: string | null
  /** Quando morreu (a DLL de quem estava perto viu o mob sumir); null = vivo ou não se sabe. */
  diedAt: string | null
  /** Só no navegador: quando esta página soube da morte (o vermelho de 5 s conta daí, como no jogo). */
  deathSeenAt?: number
}

/** Achado no último minuto e ainda vivo: pisca. Morreu enquanto piscava: vermelho piscante por 5 s. */
export const FRESH_MS = 60_000
export const DEAD_FLASH_MS = 5_000
/** Morte mais antiga que isto quando a página soube (abriu depois): só para de piscar, sem vermelho. */
const DEAD_NEWS_MS = 15_000

export const isFresh = (s: ResourceSpawn, now: number) => !s.diedAt && now - Date.parse(s.foundAt) < FRESH_MS
export const isDeadFlash = (s: ResourceSpawn, now: number) =>
  s.deathSeenAt != null && !!s.diedAt && Date.parse(s.diedAt) - Date.parse(s.foundAt) < FRESH_MS &&
  now - s.deathSeenAt < DEAD_FLASH_MS

export function useResourceOverview() {
  return useQuery({
    queryKey: resourceKeys.overview,
    queryFn: () => apiClient<ResourceOverview>('/resources'),
    staleTime: 60_000,
  })
}

export function useResourceSpawns(map: number, enabled: boolean) {
  const queryClient = useQueryClient()
  useEffect(() => {
    if (!enabled) return
    const refresh = () => {
      void queryClient.invalidateQueries({ queryKey: resourceKeys.spawns(map) })
    }
    const unsubscribe = subscribeTopicEffect(`/topic/resources/${map}`, refresh)
    const offReconnect = onSocketReconnect(refresh)
    return () => {
      unsubscribe()
      offReconnect()
    }
  }, [enabled, map, queryClient])
  return useQuery({
    queryKey: resourceKeys.spawns(map),
    queryFn: async () => {
      const list = await apiClient<ResourceSpawn[]>(`/resources/spawns?map=${map}`)
      // Guarda quando esta página soube de cada morte (a busca anterior já sabia: mantém a hora).
      const before = new Map((queryClient.getQueryData<ResourceSpawn[]>(resourceKeys.spawns(map)) ?? []).map((s) => [s.id, s]))
      const now = serverNow()
      return list.map((s) => {
        if (!s.diedAt) return s
        const known = before.get(s.id)
        if (known?.diedAt) return { ...s, deathSeenAt: known.deathSeenAt }
        return now - Date.parse(s.diedAt) < DEAD_NEWS_MS ? { ...s, deathSeenAt: now } : s
      })
    },
    enabled,
    staleTime: 30_000,
    // Com ponto piscando (achado no último minuto), busca a cada 1 s para ver a morte logo; senão a cada 15 s.
    refetchInterval: (query) => {
      const now = serverNow()
      return query.state.data?.some((s) => now - Date.parse(s.foundAt) < FRESH_MS + 10_000) ? 1_000 : 15_000
    },
  })
}

function useResourceMutation<V>(request: (vars: V) => Promise<ResourceOverview>) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: request,
    onSuccess: (data) => {
      queryClient.setQueryData(resourceKeys.overview, data)
      queryClient.invalidateQueries({ queryKey: ['resources', 'spawns'] })
    },
  })
}

export function useUpdateResourceAccess() {
  return useResourceMutation((body: { memberIds: number[] }) =>
    apiClient<ResourceOverview>('/resources/access', { method: 'PUT', body }))
}

export function useSetResourceTier() {
  return useResourceMutation((body: { name: string; tier: number }) =>
    apiClient<ResourceOverview>('/resources/mobs', { method: 'PUT', body }))
}

/** Ponto do mundo na imagem do mapa (em pixels da imagem 400x400); null fora dela. */
export function mapPoint(view: ResourceMapScale, x: number, z: number) {
  const px = view.offX + (x - view.minX) * view.scaleX
  const py = view.offY + (view.maxZ - z) * view.scaleY
  if (!(px >= 0 && py >= 0 && px < view.areaW && py < view.areaH)) return null
  return { px, py }
}

/** 4 = Tower Rift Metal (Crag Mind Field): rastreado como recurso, com categoria própria. */
export const TIER_LABEL: Record<number, string> = { 1: 'Grau 1', 2: 'Grau 2', 3: 'Grau 3', 4: 'Tower Rift Metal' }

/** Estatística de respawn de um grau num mapa (ver ResourceSpawnService.stats). */
export interface ResourceTierStats {
  tier: number
  total: number
  /** Achados por hora em que alguém com a DLL achou qualquer recurso neste mapa. */
  perObservedHour: number
  /** Média do servidor por hora (500 / 50 / 0,33). */
  expectedPerHour: number | null
  lastSeenAt: string | null
  medianGapSeconds: number | null
  /** Último + intervalo mediano: só uma estimativa (o spawn é aleatório). */
  nextLikelyAt: string | null
  /** 12 faixas de 5 min do minuto da hora (Brasília). */
  byMinute: number[]
  /** 24 horas do dia (Brasília). */
  byHour: number[]
}

export interface ResourceStats {
  map: number
  days: number
  observedHours: number
  tiers: ResourceTierStats[]
}

export function useResourceStats(map: number, days: number) {
  return useQuery({
    queryKey: ['resources', 'stats', map, days] as const,
    queryFn: () => apiClient<ResourceStats>(`/resources/stats?map=${map}&days=${days}`),
    staleTime: 60_000,
    refetchInterval: 60_000,
  })
}
