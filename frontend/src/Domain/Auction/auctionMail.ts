import type { Auction } from '@/Domain/types/models'
import relicCodes from './auctionRelicCodes.json'

const relicInventoryCodes = new Map(Object.entries(relicCodes).map(([inventory, catalog]) => [catalog, inventory]))

export function getAuctionMailAttachment({ items }: Auction) {
  if (items.length !== 1) return
  const { gameIcon, quantity } = items[0]
  const catalogCode = gameIcon?.gameCode?.toLowerCase()
  const itemCode = catalogCode && (relicInventoryCodes.get(catalogCode) ?? catalogCode)
  // shortcut: one native source slot per letter, with an eight-bit stack count.
  if (itemCode && /^[a-z0-9]{7}$/.test(itemCode) && Number.isInteger(quantity) && quantity >= 1 && quantity <= 255) {
    return { itemCode, quantity }
  }
}
