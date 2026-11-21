import { useState } from 'react'
import { useAuthContext } from '@/Features/Auth/contexts/AuthContext'
import { Button } from '@/Shared/ui/components/Button/Button'
import { ConfirmDialog } from '@/Shared/ui/components/ConfirmDialog/ConfirmDialog'
import { TeleportMailDialog } from '@/Features/Equipment/TeleportMail'
import { useEventRewardRecipients } from '@/Domain/Equipment/hooks/useTeleportMail'

export function EventRewardMail({ claimIds }: { claimIds: number[] }) {
  const { hasRole } = useAuthContext()
  const [open, setOpen] = useState(false)
  const allowed = hasRole('ADMINISTRADOR')
  const query = useEventRewardRecipients(claimIds, open && allowed)
  if (!allowed || !claimIds.length) return null
  return <>
    <Button size="sm" variant="secondary" onClick={() => setOpen(true)}>Enviar recompensa</Button>
    {open && (query.data && !query.isFetching && !query.error ? <TeleportMailDialog allowed={allowed} close={() => setOpen(false)}
      members={query.data.recipients.map(row => ({ memberId: row.memberId, nickname: row.nickname, level: 0 }))}
      reward={{ name: query.data.name, claims: Object.fromEntries(query.data.recipients.map(row => [row.nickname, row.claimIds])) }} /> :
      <ConfirmDialog open title="Enviar recompensa do evento" confirmLabel="Fechar" cancelLabel="Fechar"
        onConfirm={() => setOpen(false)} onCancel={() => setOpen(false)}>
        <p role={query.error ? 'alert' : 'status'}>{query.error ? query.error.message : 'Conferindo quem recebeu os pontos...'}</p>
      </ConfirmDialog>)}
  </>
}
