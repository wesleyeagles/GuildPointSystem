import { useRef, useState } from 'react'
import { useAppToast } from '@/Shared/ui/components/AppToast/AppToast'

type MailAttachment = { itemCode: string; quantity: number }
type MailReply = { message?: string; tracking?: boolean; delivery?: string }

function gameMailRequest(path: 'prepare-letter' | 'delivery-status', body: object) {
  const request: RequestInit & { targetAddressSpace: 'loopback' } = {
    method: 'POST', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body), signal: AbortSignal.timeout(path === 'prepare-letter' ? 30_000 : 10_000),
    credentials: 'omit', redirect: 'error', targetAddressSpace: 'loopback',
  }
  return fetch(`http://127.0.0.1:15183/${path}`, request)
}

async function waitForGameDelivery(tracking: string, onSent: () => Promise<void>) {
  const deadline = Date.now() + 180_000
  do {
    const response = await gameMailRequest('delivery-status', { tracking })
    if (!response.ok) return
    const { delivery }: MailReply = await response.json()
    if (delivery === 'sent') return onSent()
    if (delivery !== 'waiting') return
    await new Promise((resolve) => window.setTimeout(resolve, 1500))
  } while (Date.now() < deadline)
}

export function useGameMail(allowed: boolean, sender: string) {
  const { showToast } = useAppToast()
  const busy = useRef(false)
  const [preparingMail, setPreparingMail] = useState(false)
  const canPrepareMail = allowed && (
    window.location.origin === 'https://blacklist.guildsystem.com.br' ||
    (import.meta.env.DEV && import.meta.env.VITE_GAME_MAIL_PREVIEW === 'true')
  )

  async function prepareMail(recipient: string, resolveAttachment?: () => Promise<MailAttachment | undefined>, onSent?: () => Promise<void>) {
    if (!canPrepareMail || busy.current) return
    busy.current = true
    setPreparingMail(true)
    try {
      let attachment: MailAttachment | undefined
      try {
        attachment = await resolveAttachment?.()
      } catch {
        showToast('Não foi possível consultar o código do item do lote. Tente novamente.', 'error')
        return
      }
      const draft = { sender, recipient, ...attachment }
      const tracking = attachment && onSent ? crypto.randomUUID().replaceAll('-', '') : undefined
      let response = await gameMailRequest('prepare-letter', { ...draft, tracking })
      // Old bridges reject the extra field before opening a draft.
      if (tracking && response.status === 400) response = await gameMailRequest('prepare-letter', draft)
      const result: MailReply = await response.json()
      const manual = response.ok && resolveAttachment && !attachment
      showToast(
        manual ? 'Carta aberta sem anexo. O item do lote está sem um código de catálogo válido.'
          : result.message ?? 'Não foi possível confirmar o preparo da carta.',
        manual ? 'info' : response.ok ? 'success' : 'error',
      )
      if (response.ok && result.tracking && tracking && onSent) {
        void waitForGameDelivery(tracking, onSent).catch(() =>
          showToast('Não foi possível confirmar o envio automaticamente. Confira a carta e use Confirmar envio.', 'error'),
        )
      }
    } catch {
      showToast('Confira a ponte de cartas e a permissão de acesso local do navegador. Se a carta já abriu no jogo, revise-a antes de tentar novamente.', 'error')
    } finally {
      busy.current = false
      setPreparingMail(false)
    }
  }

  return { canPrepareMail, preparingMail, prepareMail }
}

export function getGameMailSender() {
  return localStorage.getItem('equipment-mail-sender') || 'boi'
}
