import { useSetPartyQueueAccess } from '@/Domain/Party/hooks/useParty'
import type { PartyQueueAccess } from '../Party.types'
import { Button } from '@/Shared/ui/components/Button/Button'
import './PartyQueueGate.styles.scss'

export function PartyQueueGate({ access, canManage }: { access: PartyQueueAccess | undefined; canManage: boolean }) {
  const change = useSetPartyQueueAccess()
  return <div className={`party-queue-gate${access?.open ? ' is-open' : ''}`}>
    <div><strong>{access ? (access.open ? 'Fila aberta' : 'Fila fechada') : 'Carregando horário da fila…'}</strong>
      <span>Entradas das 18h às 06h · Brasília</span>
      {access?.manual && <span>{access.open ? 'Abertura' : 'Fechamento'} manual até {new Date(access.nextChangeAt).toLocaleTimeString('pt-BR', { timeZone: 'America/Sao_Paulo', hour: '2-digit', minute: '2-digit' })}.</span>}
    </div>
    {canManage && access && <Button size="sm" variant="secondary" loading={change.isPending} onClick={() => change.mutate(!access.open)}>
      {access.open ? 'Fechar fila' : 'Abrir fila'}
    </Button>}
    {change.error && <p role="alert">{change.error.message}</p>}
  </div>
}
