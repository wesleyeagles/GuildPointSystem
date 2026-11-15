import { useCallback } from 'react'
import { useAuctionOutbidSubscription, type AuctionOutbid } from '@/Domain/Auction/hooks/useAuction'
import { formatAuctionItems } from '@/Domain/types/models'
import { useAuthContext } from '@/Features/Auth/contexts/AuthContext'
import { AuctionItemIcon } from '@/Features/Auction/components/AuctionItemIcon/AuctionItemIcon'
import { useAppToast } from '@/Shared/ui/components/AppToast/AppToast'
import { playScheduleAlertSound } from '@/Shared/utils/notificationSound'

/** Mounted once in the layout: tells a member, wherever they are on the site, that their bid was passed. */
export function AuctionOutbidListener() {
  const { user } = useAuthContext()
  const { showToast } = useAppToast()

  const onOutbid = useCallback(
    (outbid: AuctionOutbid) => {
      const lot = String(outbid.auctionId).padStart(3, '0')
      const names = formatAuctionItems(outbid.items ?? []) || `Leilão #${lot}`
      playScheduleAlertSound()
      showToast(`${outbid.bidder} deu ${outbid.amount} pts em ${names}.`, 'error', {
        title: `Lance superado #${lot}`,
        icon: outbid.items?.[0] ? <AuctionItemIcon item={outbid.items[0]} size={44} /> : undefined,
        link: { to: `/auctions/${outbid.auctionId}`, label: 'Dar lance →' },
        durationMs: 15_000,
      })
    },
    [showToast],
  )

  useAuctionOutbidSubscription(user?.memberId, onOutbid)
  return null
}
