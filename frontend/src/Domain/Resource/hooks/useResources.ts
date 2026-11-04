import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { apiClient } from '@/Shared/api/client'

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

/** tier 1..3 = grau; 0 = não é recurso (ignorado); null = desconhecido (a liderança ainda não escolheu). */
export interface ResourceMob {
  name: string
  map: number
  tier: number | null
  spawns: number
  firstSeenAt: string
}

/** canView: está na lista ou é Administrador+. trackers só vem para quem configura. */
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
}

export function useResourceOverview() {
  return useQuery({
    queryKey: resourceKeys.overview,
    queryFn: () => apiClient<ResourceOverview>('/resources'),
    staleTime: 60_000,
  })
}

export function useResourceSpawns(map: number, enabled: boolean) {
  return useQuery({
    queryKey: resourceKeys.spawns(map),
    queryFn: () => apiClient<ResourceSpawn[]>(`/resources/spawns?map=${map}`),
    enabled,
    staleTime: 30_000,
    refetchInterval: 60_000, // os membros vão achando recursos enquanto a página está aberta
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

export const TIER_LABEL: Record<number, string> = { 1: 'Grau 1', 2: 'Grau 2', 3: 'Grau 3' }
