import { useEffect, useRef } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { apiClient } from '@/Shared/api/client'
import { sanitizeState, type SavedState } from './damageState'

/** Onde os presets ficavam antes de irem para o membro: sobem uma vez e saem do navegador. */
export const LEGACY_PRESETS_KEY = 'damageCalc.presets.v1'
/** Quantos presets cabem lado a lado na comparação. */
export const MAX_COMPARE = 3
export const NAME_MAX = 40

export interface Preset { name: string; state: SavedState }
interface ApiPreset { name: string; state: unknown }

const presetsKey = ['damage-presets'] as const
const toPresets = (list: ApiPreset[]): Preset[] => list.map(p => ({ name: p.name, state: sanitizeState(p.state) }))

function legacyPresets(): ApiPreset[] {
  try {
    const raw = JSON.parse(localStorage.getItem(LEGACY_PRESETS_KEY) ?? '[]')
    if (!Array.isArray(raw)) return []
    return raw
      .filter(p => p && typeof p.name === 'string' && p.name.trim() && p.state && typeof p.state === 'object')
      .map(p => ({ name: String(p.name).trim().slice(0, NAME_MAX), state: p.state }))
  } catch {
    return []
  }
}

/** Presets do membro logado, guardados no servidor. */
export function usePresets() {
  const queryClient = useQueryClient()
  const query = useQuery({
    queryKey: presetsKey,
    queryFn: async () => toPresets(await apiClient<ApiPreset[]>('/damage-presets')),
  })
  const onSuccess = (list: ApiPreset[]) => queryClient.setQueryData(presetsKey, toPresets(list))

  const save = useMutation({
    mutationFn: (p: Preset) => apiClient<ApiPreset[]>('/damage-presets', { method: 'PUT', body: p }),
    onSuccess,
  })
  const remove = useMutation({
    mutationFn: (name: string) => apiClient<ApiPreset[]>(`/damage-presets?name=${encodeURIComponent(name)}`, { method: 'DELETE' }),
    onSuccess,
  })

  // Os que estavam só neste navegador sobem para a conta uma vez (os de mesmo nome já na conta ficam).
  const imported = useRef(false)
  useEffect(() => {
    if (!query.isSuccess || imported.current) return
    imported.current = true
    const local = legacyPresets()
    if (!local.length) { localStorage.removeItem(LEGACY_PRESETS_KEY); return }
    void apiClient<ApiPreset[]>('/damage-presets/import', { method: 'POST', body: local })
      .then(list => { onSuccess(list); localStorage.removeItem(LEGACY_PRESETS_KEY) })
      .catch(() => { imported.current = false })
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [query.isSuccess])

  return { presets: query.data ?? [], loading: query.isLoading, loadError: query.error, save, remove }
}
