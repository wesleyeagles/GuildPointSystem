export type { Auction } from '@/Domain/types/models'

export interface AuctionWsPayload {
  type: 'AUCTION_UPDATE' | 'CHAT' | 'BOT' | 'CLOSED'
  auction?: import('@/Domain/types/models').Auction
  content?: string
}
