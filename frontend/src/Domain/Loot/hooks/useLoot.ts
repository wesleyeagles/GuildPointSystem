import { useQuery } from '@tanstack/react-query'
import { apiClient } from '@/Shared/api/client'

/** DROP = anúncio do servidor, PARTY = loot da PT, REWARD = recompensa de dungeon/Citadel. */
export type LootKind = 'DROP' | 'PARTY' | 'REWARD'

/** Um loot de membro lido pela DLL (1.49.0+) no chat do sistema do jogo. */
export interface LootRow {
  id: number
  memberId: number
  nickname: string
  /** Nome do personagem como veio no chat. */
  looter: string
  kind: LootKind
  item: string
  quantity: number
  lootedAt: string
  /** Quantos membros com a DLL viram a mesma linha. */
  seenBy: number
}

export interface LootQuery {
  nickname: string
  item: string
  kind: LootKind | ''
  days: number
}

export function useLoot(query: LootQuery) {
  return useQuery({
    queryKey: ['loot', query],
    queryFn: () => {
      const params = new URLSearchParams({ days: String(query.days) })
      if (query.nickname.trim()) params.set('nickname', query.nickname.trim())
      if (query.item.trim()) params.set('item', query.item.trim())
      if (query.kind) params.set('kind', query.kind)
      return apiClient<LootRow[]>(`/loot?${params}`)
    },
    refetchInterval: 30_000,
    placeholderData: (previous) => previous,
  })
}
