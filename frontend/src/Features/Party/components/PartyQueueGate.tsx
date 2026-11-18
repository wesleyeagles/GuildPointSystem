import type { PartyQueueAccess } from '../Party.types'
import './PartyQueueGate.styles.scss'

export function PartyQueueGate({ access }: { access: PartyQueueAccess | undefined }) {
  return <div className={`party-queue-gate${access?.open ? ' is-open' : ''}`}>
    <div className="party-queue-gate__summary">
      <strong>{access ? (access.open ? 'Fila aberta' : 'Fila fechada') : 'Carregando horário da fila…'}</strong>
      <div className="party-queue-gate__opening">
        <span>Abertura da fila</span>
        <span>18h Brasília</span>
        <span title="Moscou · UTC+3 · dia seguinte em relação a Brasília">00h Servidor</span>
        <span title="Jacarta · UTC+7 · dia seguinte em relação a Brasília">04h WIB</span>
      </div>
    </div>
    <div className="party-queue-gate__leveling">
      <span>Up · Brasília</span>
      <span><b>Z · 12h</b> <span>18h–06h</span></span>
      <span><b>Tapete · 4h</b> <span>18h–22h</span></span>
    </div>
    <div className="party-queue-gate__override">
      {access?.manual && <span>{access.open ? 'Abertura' : 'Fechamento'} manual até {new Date(access.nextChangeAt).toLocaleTimeString('pt-BR', { timeZone: 'America/Sao_Paulo', hour: '2-digit', minute: '2-digit' })}.</span>}
    </div>
  </div>
}
