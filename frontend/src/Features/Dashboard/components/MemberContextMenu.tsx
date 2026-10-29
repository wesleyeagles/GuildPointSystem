import { useEffect, useLayoutEffect, useRef, useState } from 'react'
import { createPortal } from 'react-dom'
import { Link } from 'react-router-dom'
import type { DiscordMember } from '@/Domain/Discord/hooks/useDiscordMembers'
import { useLinkDiscord, useSetSellBlock } from '@/Domain/Member/hooks/useMembers'
import type { Member } from '@/Domain/types/models'
import { useAppToast } from '@/Shared/ui/components/AppToast/AppToast'
import { Button } from '@/Shared/ui/components/Button/Button'
import { Calendar } from '@/Shared/ui/components/Calendar/Calendar'
import { discordProfileUrl, openInDiscordApp } from '@/Shared/utils/discordLink'
import { addDays, formatDay, sellBlockedUntil, todayBrt } from '@/Features/Dashboard/utils/sellBlock'

const QUICK_DAYS = [7, 15, 30]

/** Padlock shown for accounts blocked for sale. */
export function SellLock({ className, until }: { className: string; until?: string }) {
  const label = until ? `Conta bloqueada para venda até ${formatDay(until)}` : 'Conta bloqueada para venda'
  return (
    <span className={className} title={label} aria-label={label}>
      <svg viewBox="0 0 24 24" width="1em" height="1em" fill="currentColor" aria-hidden="true">
        <path d="M12 2a5 5 0 0 0-5 5v3H6a2 2 0 0 0-2 2v8a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2v-8a2 2 0 0 0-2-2h-1V7a5 5 0 0 0-5-5Zm-3 8V7a3 3 0 1 1 6 0v3H9Zm3 4a1.5 1.5 0 0 1 .75 2.8V18h-1.5v-1.2A1.5 1.5 0 0 1 12 14Z" />
      </svg>
    </span>
  )
}

export interface MemberMenuState {
  member: Member
  x: number
  y: number
}

interface MemberContextMenuProps {
  menu: MemberMenuState
  /** The member's linked Discord account, if any. */
  discordMember?: DiscordMember
  /** Every Discord member in the list, for (re)linking. */
  discordMembers: DiscordMember[]
  /** Líder/Administrador: Discord linking and the sale block. */
  canManage: boolean
  onClose: () => void
}

/** Right-click menu on a ranking member: Discord shortcuts, profile and (staff) Discord link + sale block. */
export function MemberContextMenu({ menu, discordMember, discordMembers, canManage, onClose }: MemberContextMenuProps) {
  const setSellBlock = useSetSellBlock()
  const linkDiscord = useLinkDiscord()
  const { showToast } = useAppToast()
  const ref = useRef<HTMLDivElement>(null)
  const { member } = menu
  const blockedUntil = sellBlockedUntil(member)
  const [until, setUntil] = useState(blockedUntil ?? '')
  const [discordId, setDiscordId] = useState(discordMember?.id ?? '')
  const [pos, setPos] = useState({ left: menu.x, top: menu.y })

  // Keep the menu inside the viewport once its real size is known.
  useLayoutEffect(() => {
    const el = ref.current
    if (!el) return
    const { width, height } = el.getBoundingClientRect()
    setPos({
      left: Math.max(8, Math.min(menu.x, window.innerWidth - width - 8)),
      top: Math.max(8, Math.min(menu.y, window.innerHeight - height - 8)),
    })
  }, [menu.x, menu.y])

  useEffect(() => {
    const close = (e: Event) => {
      if (ref.current?.contains(e.target as Node)) return
      onClose()
    }
    const onKey = (e: KeyboardEvent) => e.key === 'Escape' && onClose()
    document.addEventListener('mousedown', close)
    document.addEventListener('contextmenu', close, true)
    window.addEventListener('scroll', close, true)
    window.addEventListener('resize', close)
    document.addEventListener('keydown', onKey)
    return () => {
      document.removeEventListener('mousedown', close)
      document.removeEventListener('contextmenu', close, true)
      window.removeEventListener('scroll', close, true)
      window.removeEventListener('resize', close)
      document.removeEventListener('keydown', onKey)
    }
  }, [onClose])

  const copy = async (text: string, label: string) => {
    try {
      await navigator.clipboard.writeText(text)
      showToast(`${label} copiado.`, 'success')
    } catch {
      showToast('Não foi possível copiar.', 'error')
    }
    onClose()
  }

  const saveSellBlock = async (next: string | null) => {
    try {
      await setSellBlock.mutateAsync({ id: member.id, until: next })
      showToast(
        next
          ? `${member.nickname}: conta bloqueada para venda até ${formatDay(next)}.`
          : `${member.nickname}: bloqueio de venda removido.`,
        'success',
      )
      onClose()
    } catch (err) {
      showToast(err instanceof Error ? err.message : 'Erro ao alterar bloqueio de venda', 'error')
    }
  }

  const saveDiscord = async () => {
    const next = discordId || null
    try {
      await linkDiscord.mutateAsync({ id: member.id, discordId: next })
      const name = discordMembers.find((d) => d.id === next)?.name
      showToast(next ? `${member.nickname} vinculado a ${name ?? 'Discord'}.` : `${member.nickname}: Discord desvinculado.`, 'success')
      onClose()
    } catch (err) {
      showToast(err instanceof Error ? err.message : 'Erro ao vincular Discord', 'error')
    }
  }

  // Discord accounts free to link: not tied to another site account.
  const linkOptions = discordMembers
    .filter((d) => !d.siteMemberId || d.siteMemberId === member.id)
    .sort((a, b) => a.name.localeCompare(b.name))

  return createPortal(
    <div ref={ref} className="member-context-menu" style={pos} role="menu">
      <div className="member-context-menu__title">{member.nickname}</div>

      <Link to={`/profile/${member.id}`} role="menuitem" className="member-context-menu__item" onClick={onClose}>
        Ver perfil no site
      </Link>

      <div className="member-context-menu__section">Discord</div>
      {discordMember ? (
        <>
          <a
            href={discordProfileUrl(discordMember.id)}
            target="_blank"
            rel="noopener noreferrer"
            role="menuitem"
            className="member-context-menu__item"
            title="Abre o perfil no app do Discord; use o botão Mensagem para mandar PM"
            onClick={(e) => {
              openInDiscordApp(e, discordMember.id)
              onClose()
            }}
          >
            <span style={discordMember.color ? { color: discordMember.color } : undefined}>{discordMember.name}</span>
            <span className="member-context-menu__hint">Enviar PM / abrir perfil</span>
          </a>
          <button type="button" role="menuitem" className="member-context-menu__item" onClick={() => copy(`<@${discordMember.id}>`, 'Menção')}>
            Copiar menção (@)
          </button>
          <button type="button" role="menuitem" className="member-context-menu__item" onClick={() => copy(discordMember.name, 'Nome do Discord')}>
            Copiar nome do Discord
          </button>
        </>
      ) : (
        <div className="member-context-menu__status">Sem Discord vinculado</div>
      )}

      {canManage && (
        <form
          className="member-context-menu__form"
          onSubmit={(e) => {
            e.preventDefault()
            saveDiscord()
          }}
        >
          <label className="member-context-menu__label">
            Vincular Discord
            <select value={discordId} onChange={(e) => setDiscordId(e.target.value)}>
              <option value="">— Nenhum —</option>
              {linkOptions.map((d) => (
                <option key={d.id} value={d.id}>
                  {d.name}
                </option>
              ))}
            </select>
          </label>
          <Button
            type="submit"
            size="sm"
            variant="secondary"
            loading={linkDiscord.isPending}
            disabled={discordId === (discordMember?.id ?? '')}
          >
            Salvar vínculo
          </Button>
        </form>
      )}

      {canManage && (
        <>
          <div className="member-context-menu__section">Venda da conta</div>
          <div className="member-context-menu__status">
            <SellLock className="member-context-menu__icon" />
            {blockedUntil ? `Bloqueada para venda até ${formatDay(blockedUntil)}` : 'Conta não bloqueada para venda'}
          </div>
          <form
            className="member-context-menu__form"
            onSubmit={(e) => {
              e.preventDefault()
              if (until) saveSellBlock(until)
            }}
          >
            <span className="member-context-menu__label">
              Bloqueada até{' '}
              <strong className="member-context-menu__date">{until ? formatDay(until) : '—'}</strong>
            </span>
            <div className="member-context-menu__quick">
              {QUICK_DAYS.map((days) => (
                <button
                  key={days}
                  type="button"
                  className={`member-context-menu__chip${until === addDays(todayBrt(), days) ? ' member-context-menu__chip--active' : ''}`}
                  onClick={() => setUntil(addDays(todayBrt(), days))}
                >
                  +{days} dias
                </button>
              ))}
            </div>
            <Calendar value={until} onChange={setUntil} min={todayBrt()} today={todayBrt()} />
            <Button
              type="submit"
              size="sm"
              loading={setSellBlock.isPending && !!setSellBlock.variables?.until}
              disabled={!until || until === blockedUntil || setSellBlock.isPending}
            >
              {blockedUntil ? 'Alterar data do bloqueio' : 'Bloquear conta para venda'}
            </Button>
            {blockedUntil && (
              <Button
                size="sm"
                variant="danger"
                loading={setSellBlock.isPending && !setSellBlock.variables?.until}
                disabled={setSellBlock.isPending}
                onClick={() => saveSellBlock(null)}
              >
                Remover bloqueio
              </Button>
            )}
          </form>
        </>
      )}
    </div>,
    document.body,
  )
}
