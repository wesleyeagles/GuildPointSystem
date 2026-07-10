import { useEffect, useMemo, useRef, useState } from 'react'
import { useParams } from 'react-router-dom'
import {
  useAuction,
  useAuctionMessages,
  usePlaceBid,
  useSendAuctionMessage,
} from '@/Domain/Auction/hooks/useAuction'
import { useCurrentMember } from '@/Domain/Member/hooks/useMembers'
import { useItem } from '@/Domain/Item/hooks/useItems'
import { useItemSeeds } from '@/Domain/Seed/hooks/useItemSeeds'
import { Roulette } from '@/Features/Auction/components/Roulette/Roulette'
import { Button } from '@/Shared/ui/components/Button/Button'
import { ItemCard } from '@/Features/Items/components/ItemCard/ItemCard'
import type { AuctionItem, SeedOption } from '@/Domain/types/models'
import './Auction.styles.scss'

// ─── Live countdown ───────────────────────────────────────────────────────────

function useCountdown(endsAt: string) {
  const [remaining, setRemaining] = useState(0)
  useEffect(() => {
    if (!endsAt) return
    const tick = () => {
      setRemaining(Math.max(0, Math.floor((new Date(endsAt).getTime() - Date.now()) / 1000)))
    }
    tick()
    const timer = setInterval(tick, 1000)
    return () => clearInterval(timer)
  }, [endsAt])
  return remaining
}

function formatTime(seconds: number) {
  if (seconds <= 0) return '0:00'
  const m = Math.floor(seconds / 60)
  const s = seconds % 60
  return `${m}:${s.toString().padStart(2, '0')}`
}

// ─── Single auction item card (fetches full data) ─────────────────────────────

function AuctionItemCard({
  auctionItem,
  weaponCasts,
}: {
  auctionItem: AuctionItem
  weaponCasts: SeedOption[]
}) {
  const { data: item } = useItem(auctionItem.itemId)

  if (!item) {
    return (
      <div className="auction-item-placeholder">
        {auctionItem.imageUrl && (
          <img src={auctionItem.imageUrl} alt={auctionItem.itemName} className="auction-item-placeholder__img" />
        )}
        <span>{auctionItem.itemName}</span>
      </div>
    )
  }

  return (
    <div className="auction-item-wrap">
      <ItemCard item={item} canAdmin={false} onDelete={() => {}} weaponCasts={weaponCasts} />
      {auctionItem.quantity > 1 && (
        <div className="auction-item-wrap__qty">x{auctionItem.quantity}</div>
      )}
    </div>
  )
}

// ─── Page ─────────────────────────────────────────────────────────────────────

const STATUS_LABEL: Record<string, string> = {
  OPEN: 'Aberto',
  DOLE: 'DOLE',
  TIE_BREAK: 'Empate',
  CLOSED: 'Encerrado',
}

export function AuctionDetailPage() {
  const { id } = useParams<{ id: string }>()
  const auctionId = Number(id)

  const { data: auction, isLoading, isError } = useAuction(auctionId)
  const { data: messages = [] } = useAuctionMessages(auctionId)
  const { data: member } = useCurrentMember()
  const { data: weaponCasts = [] } = useItemSeeds('WEAPON_CAST')
  const placeBid = usePlaceBid()
  const sendMessage = useSendAuctionMessage()

  const [bidAmount, setBidAmount] = useState('')
  const [chatText, setChatText] = useState('')
  const [rouletteDone, setRouletteDone] = useState(false)
  const chatEndRef = useRef<HTMLDivElement>(null)

  const remaining = useCountdown(auction?.endsAt ?? '')

  useEffect(() => {
    chatEndRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [messages])

  const tieSegments = useMemo(() => {
    if (!auction?.tied || !auction.tiedMemberNicknames?.length) return []
    return auction.tiedMemberNicknames.map((nickname, i) => ({ id: i, label: nickname }))
  }, [auction])

  const winnerIndex = useMemo(() => {
    if (!auction?.tieBreakSeed || tieSegments.length === 0) return 0
    const seed = Number(auction.tieBreakSeed)
    const n = tieSegments.length
    return ((seed % n) + n) % n
  }, [auction, tieSegments.length])

  const handleBid = async () => {
    const amount = Number(bidAmount)
    if (!amount) return
    await placeBid.mutateAsync({ id: auctionId, amount })
    setBidAmount('')
  }

  const quickBid = (increment: number) => {
    const current = auction?.currentBid ?? 0
    setBidAmount(String(current + increment))
  }

  const handleChat = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!chatText.trim()) return
    await sendMessage.mutateAsync({ id: auctionId, content: chatText })
    setChatText('')
  }

  if (isLoading) {
    return <p className="auction-detail__loading">Carregando leilão...</p>
  }

  if (isError || !auction) {
    return <p className="auction-detail__loading">Leilão não encontrado.</p>
  }

  const isClosed = auction.status === 'CLOSED'
  const isOpen = auction.status === 'OPEN'
  const isUrgent = remaining > 0 && remaining <= 30
  const iAmLeading = !!(member && (
    auction.tied
      ? auction.tiedMemberIds?.includes(member.id)
      : auction.leaderId !== null && auction.leaderId === member.id
  ))

  return (
    <div className="auction-detail">
      {/* ── LEFT ────────────────────────────────────────────────────────────── */}
      <div className="auction-detail__left">
        {/* Item cards */}
        <div className="auction-detail__items">
          {auction.items.map((ai) => (
            <AuctionItemCard key={ai.itemId} auctionItem={ai} weaponCasts={weaponCasts} />
          ))}
        </div>

        {/* Info panel */}
        <div className="auction-detail__info">
          <div className="auction-detail__info-row">
            <span className="auction-detail__info-label">Status</span>
            <span className={`auction-detail__badge auction-detail__badge--${auction.status.toLowerCase()}`}>
              {STATUS_LABEL[auction.status] ?? auction.status}
            </span>
          </div>

          <div className="auction-detail__info-row">
            <span className="auction-detail__info-label">Lance atual</span>
            <span className="auction-detail__info-value">{auction.currentBid} pts</span>
          </div>

          <div className="auction-detail__info-row">
            <span className="auction-detail__info-label">Tempo</span>
            <span className={`auction-detail__timer${isUrgent ? ' auction-detail__timer--urgent' : ''}`}>
              {auction.status === 'CLOSED' ? 'Encerrado' : formatTime(remaining)}
            </span>
          </div>

          {member && (
            <div className="auction-detail__info-row">
              <span className="auction-detail__info-label">Disponível</span>
              <span className="auction-detail__info-value">{member.availablePoints} pts</span>
            </div>
          )}

          {/* Leader during open auction */}
          {!isClosed && (auction.leaderNickname || auction.tied) && (
            <div className="auction-detail__info-row">
              <span className="auction-detail__info-label">Vencendo</span>
              {auction.tied ? (
                <span className="auction-detail__winner-name">
                  Empatado entre {auction.tiedCount}
                </span>
              ) : (
                <span className={iAmLeading ? 'auction-detail__leader--me' : 'auction-detail__winner-name'}>
                  {iAmLeading ? 'Você' : auction.leaderNickname}
                </span>
              )}
            </div>
          )}

          {/* Final winner after close */}
          {isClosed && auction.winnerNickname && (
            <div className="auction-detail__info-row">
              <span className="auction-detail__info-label">Vencedor</span>
              <span className="auction-detail__winner-name">{auction.winnerNickname}</span>
            </div>
          )}
        </div>

        {/* "You're winning / tied" banner */}
        {isOpen && iAmLeading && (
          <div className="auction-detail__leading-banner">
            {auction.tied
              ? `Você está empatado entre ${auction.tiedCount} pessoas`
              : 'Seu lance é o atual vencedor — você está ganhando!'}
          </div>
        )}

        {/* Tie-break roulette */}
        {auction.status === 'TIE_BREAK' && tieSegments.length > 0 && !rouletteDone && (
          <Roulette
            segments={tieSegments}
            winnerIndex={winnerIndex}
            seed={auction.tieBreakSeed ?? undefined}
            onComplete={() => setRouletteDone(true)}
          />
        )}

        {/* Bid input — hidden if I'm already leading */}
        {isOpen && !iAmLeading && (
          <div className="auction-detail__bid">
            <input
              type="number"
              value={bidAmount}
              onChange={(e) => setBidAmount(e.target.value)}
              placeholder="Valor do lance"
              className="auction-detail__bid-input"
              onKeyDown={(e) => e.key === 'Enter' && handleBid()}
            />
            <div className="auction-detail__bid-actions">
              <Button onClick={handleBid} loading={placeBid.isPending}>
                Lance
              </Button>
              {[100, 250, 500].map((inc) => (
                <Button key={inc} variant="secondary" size="sm" onClick={() => quickBid(inc)}>
                  +{inc}
                </Button>
              ))}
            </div>
          </div>
        )}
      </div>

      {/* ── RIGHT ───────────────────────────────────────────────────────────── */}
      <div className="auction-detail__right">
        <div className="auction-chat">
          <div className="auction-chat__header">Chat / Lances</div>

          <div className="auction-chat__messages">
            {messages.length === 0 && (
              <p className="auction-chat__empty">Nenhuma mensagem ainda.</p>
            )}
            {messages.map((msg) => {
              const isOwn = !!member && msg.member?.id === member.id
              const isBid = msg.type === 'BID'
              const isBot = msg.type === 'BOT'
              const isSystem = isBid || isBot
              const senderName = isSystem
                ? 'Sistema'
                : (isOwn ? undefined : msg.member?.nickname)

              return (
                <div
                  key={msg.id}
                  className={`auction-chat__bubble-wrap${!isSystem && isOwn ? ' auction-chat__bubble-wrap--own' : ''}`}
                >
                  <div
                    className={[
                      'auction-chat__bubble',
                      isSystem && isBid ? 'auction-chat__bubble--bid' : '',
                      isSystem && isBot ? 'auction-chat__bubble--bot' : '',
                      !isSystem && isOwn ? 'auction-chat__bubble--own' : '',
                      !isSystem && !isOwn ? 'auction-chat__bubble--other' : '',
                    ].filter(Boolean).join(' ')}
                  >
                    {senderName && (
                      <span className={`auction-chat__nickname${isSystem ? ' auction-chat__nickname--system' : ''}`}>
                        {senderName}
                      </span>
                    )}
                    <span className="auction-chat__text">{msg.content}</span>
                    <span className="auction-chat__time">
                      {new Date(msg.createdAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
                    </span>
                  </div>
                </div>
              )
            })}
            <div ref={chatEndRef} />
          </div>

          <form onSubmit={handleChat} className="auction-chat__form">
            <input
              value={chatText}
              onChange={(e) => setChatText(e.target.value)}
              placeholder={isClosed ? 'Leilão encerrado' : 'Mensagem...'}
              className="auction-chat__input"
              disabled={isClosed}
            />
            <Button type="submit" size="sm" loading={sendMessage.isPending} disabled={isClosed}>
              Enviar
            </Button>
          </form>
        </div>
      </div>
    </div>
  )
}
