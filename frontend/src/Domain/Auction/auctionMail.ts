import type { Auction, Item } from '@/Domain/types/models'
import { apiClient } from '@/Shared/api/client'
import relicCodes from './auctionRelicCodes.json'

const relicInventoryCodes = new Map(Object.entries(relicCodes).map(([inventory, catalog]) => [catalog, inventory]))

export function canResolveAuctionMailAttachment({ items }: Auction) {
  const quantity = items[0]?.quantity
  // shortcut: one native source slot per letter, with an eight-bit stack count.
  return items.length === 1 && Number.isInteger(quantity) && quantity >= 1 && quantity <= 255
}

function inventoryCode(code?: string | null) {
  const catalogCode = code?.trim().toLowerCase()
  const itemCode = catalogCode && (relicInventoryCodes.get(catalogCode) ?? catalogCode)
  return itemCode && /^[a-z0-9]{7}$/.test(itemCode) ? itemCode : undefined
}

export async function resolveAuctionMailAttachment(auction: Auction) {
  if (!canResolveAuctionMailAttachment(auction)) return
  const { itemId, gameIcon, quantity } = auction.items[0]
  const itemCode = inventoryCode(gameIcon?.gameCode) ?? inventoryCode(
    (await apiClient<Item>(`/items/${itemId}`, { signal: AbortSignal.timeout(10_000) })).gameCode,
  )
  if (itemCode) return { itemCode, quantity }
}
