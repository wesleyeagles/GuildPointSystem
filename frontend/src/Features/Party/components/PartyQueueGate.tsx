import type { PartyQueueAccess } from '../Party.types'
import './PartyQueueGate.styles.scss'

export function PartyQueueGate({ access }: { access: PartyQueueAccess | undefined }) {
  return <div className={`party-queue-gate${access?.open ? ' is-open' : ''}`}>
    <div><strong>{access ? (access.open ? 'Fila aberta' : 'Fila fechada') : 'Carregando horário da fila…'}</strong>
      <span>Entradas das 18h às 06h · Brasília</span>
      {access?.manual && <span>{access.open ? 'Abertura' : 'Fechamento'} manual até {new Date(access.nextChangeAt).toLocaleTimeString('pt-BR', { timeZone: 'America/Sao_Paulo', hour: '2-digit', minute: '2-digit' })}.</span>}
    </div>

  </div>
}
