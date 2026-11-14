import { useRef, useState } from 'react'
import { useAppToast } from '@/Shared/ui/components/AppToast/AppToast'

export function useGameMail(allowed: boolean, sender: string) {
  const { showToast } = useAppToast()
  const busy = useRef(false)
  const [preparingMail, setPreparingMail] = useState(false)
  const canPrepareMail = allowed && (
    window.location.origin === 'https://blacklist.guildsystem.com.br' ||
    (import.meta.env.DEV && import.meta.env.VITE_GAME_MAIL_PREVIEW === 'true')
  )

  async function prepareMail(recipient: string) {
    if (!canPrepareMail || busy.current) return
    busy.current = true
    setPreparingMail(true)
    try {
      const request: RequestInit & { targetAddressSpace: 'loopback' } = {
        method: 'POST', headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ sender, recipient }), signal: AbortSignal.timeout(30_000),
        credentials: 'omit', redirect: 'error', targetAddressSpace: 'loopback',
      }
      const response = await fetch('http://127.0.0.1:15183/prepare-letter', request)
      const result: { message?: string } = await response.json()
      showToast(result.message ?? 'Não foi possível confirmar o preparo da carta.', response.ok ? 'success' : 'error')
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
