import { createContext, useContext, type ReactNode } from 'react'
import type { Auction } from '@/Domain/types/models'

interface AuctionContextValue {
  auction: Auction | undefined
}

const AuctionContext = createContext<AuctionContextValue | null>(null)

export function AuctionProvider({
  auction,
  children,
}: {
  auction: Auction | undefined
  children: ReactNode
}) {
  return (
    <AuctionContext.Provider value={{ auction }}>{children}</AuctionContext.Provider>
  )
}

export function useAuctionContext() {
  const ctx = useContext(AuctionContext)
  if (!ctx) throw new Error('useAuctionContext must be used within AuctionProvider')
  return ctx
}
