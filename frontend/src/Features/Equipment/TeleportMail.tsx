import { useRef, useState } from 'react'
import type { MemberEquipment } from '@/Domain/Equipment/types'
import { batchActive, useTeleportMail, type TeleportKind, type EventReward } from '@/Domain/Equipment/hooks/useTeleportMail'
import { ConfirmDialog } from '@/Shared/ui/components/ConfirmDialog/ConfirmDialog'
import { Button } from '@/Shared/ui/components/Button/Button'
import { SCHEDULE_TIME_ZONE } from '@/Shared/utils/scheduleTimeZone'
import './TeleportMail.styles.scss'

const names: Record<TeleportKind, string> = { taraven: 'Taraven Teleport Scroll', ether: 'Ether Scroll (Cora)', cash499: '500cc potion · ipcss04' }
const states: Record<string, string> = { planned: 'Conferido', queued: 'Aguardando', preparing: 'Preparando carta',
  'opening-confirmation': 'Conferindo envio', confirming: 'Aguardando o jogo', sent: 'Enviado', cancelled: 'Cancelado',
  completed: 'Concluído', uncertain: 'Parado: conferir no jogo', paused: 'Pausado', running: 'Em andamento' }

export function TeleportMail({ members, allowed }: { members: MemberEquipment[]; allowed: boolean }) {
  const [open, setOpen] = useState(false)
  return <><Button variant="secondary" disabled={!allowed} onClick={() => setOpen(true)}>Enviar teleportes</Button>
    {open && <TeleportMailDialog members={members} allowed={allowed} close={() => setOpen(false)} />}</>
}

export function TeleportMailDialog({ members, allowed, close, reward }: {
  members: Pick<MemberEquipment, 'memberId' | 'nickname' | 'level'>[]; allowed: boolean; close: () => void; reward?: EventReward
}) {
  const [sender, setSender] = useState(() => localStorage.getItem(reward ? 'reward-mail-sender' : 'teleport-mail-sender') || (reward ? 'boi' : 'meow'))
  const [subject, setSubject] = useState('')
  const [selected, setSelected] = useState<number[]>(() => members.filter(m => (!!reward || m.level === 50) && m.nickname.toLocaleLowerCase() !== sender.toLocaleLowerCase()).map(m => m.memberId))
  const [items, setItems] = useState<Record<TeleportKind, { selected: boolean; quantity: number }>>({
    taraven: { selected: !reward, quantity: 10 }, ether: { selected: !reward, quantity: 10 }, cash499: { selected: !!reward, quantity: 10 },
  })
  const [submitted, setSubmitted] = useState(false)
  const [confirmedUncertain, setConfirmedUncertain] = useState(false)
  const busy = useRef(false)
  const { batches, start, control } = useTeleportMail(true)
  const kinds: TeleportKind[] = reward ? ['cash499'] : ['taraven', 'ether']
  const eligible = members.filter(m => (!!reward || m.level === 50) && m.nickname.toLocaleLowerCase() !== sender.trim().toLocaleLowerCase())
    .sort((a, b) => a.nickname.localeCompare(b.nickname, 'pt-BR'))
  const usedClaims = new Set(reward ? batches.data?.batches.flatMap(batch => batch.letters.filter(row => row.state !== 'cancelled').flatMap(row => row.claimIds ?? [])) : [])
  const blocked = (nickname: string) => !!reward?.claims[nickname]?.some(id => usedClaims.has(id))
  const recipients = eligible.filter(m => selected.includes(m.memberId) && !blocked(m.nickname))
  const chosen = kinds.filter(kind => items[kind].selected)
  const active = batches.data?.batches.find(batchActive)
  const uncertain = batches.data?.batches[0]?.state === 'uncertain'
  const valid = sender.trim().length > 0 && sender.trim().length <= 16 && recipients.length > 0 && chosen.length > 0 &&
    chosen.every(kind => Number.isInteger(items[kind].quantity) && items[kind].quantity >= 1 && items[kind].quantity <= 255)
  const simulationRequired = import.meta.env.VITE_MAIL_SIMULATION_ONLY === 'true'
  const simulation = batches.data?.simulation === true
  const locked = !!active || start.isPending || submitted
  return <ConfirmDialog open title={reward ? `Enviar recompensa · ${reward.name}` : "Enviar teleportes · personagens nível 50"} code="ADM" variant="primary"
      confirmLabel={submitted || active ? 'Fechar' : simulation ? 'Iniciar simulação' : 'Conferir e iniciar envios'} cancelLabel="Fechar"
      loading={start.isPending} confirmDisabled={!submitted && !active && (!valid || (simulationRequired && !simulation) || batches.isPending || !!batches.error || (uncertain && !confirmedUncertain))}
      onCancel={close} onConfirm={() => {
        if (submitted || active) { close(); return }
        if (!valid || busy.current || !allowed || (simulationRequired && !simulation)) return
        busy.current = true
        localStorage.setItem(reward ? 'reward-mail-sender' : 'teleport-mail-sender', sender.trim())
        void start.mutateAsync({ id: crypto.randomUUID().replaceAll('-', ''), sender: sender.trim(), ...(subject.trim() ? { subject: subject.trim() } : {}), recipients: recipients.map(m => m.nickname),
          items: chosen.map(kind => ({ kind, quantity: items[kind].quantity })),
          ...(reward ? { reward: { ...reward, claims: Object.fromEntries(recipients.map(m => [m.nickname, reward.claims[m.nickname]])) } } : {}) })
          .then(() => setSubmitted(true)).catch(() => setSubmitted(true)).finally(() => { busy.current = false })
      }}>
      <div className="teleport-mail">
        {simulation && <p role="status"><strong>MODO SIMULADO — nenhuma carta será enviada ao jogo. Estoque e confirmações são simulados.</strong></p>}
        {simulationRequired && !simulation && <p role="alert">Aguardando a ponte em modo simulado. Envios reais estão bloqueados nesta prévia.</p>}
        <fieldset disabled={locked} className="teleport-mail__setup">
          <label>Remetente no jogo<input aria-label={reward ? "Remetente da recompensa" : "Remetente dos teleportes"} maxLength={16} value={sender} onChange={e => setSender(e.target.value)} /></label>
          <label>Assunto (opcional)<input aria-label="Assunto da carta" maxLength={31} placeholder="1" value={subject} onChange={e => setSubject(e.target.value)} /></label>
          <div className="teleport-mail__items">{kinds.map(kind => <div key={kind}>
            <label><input type="checkbox" checked={items[kind].selected} onChange={e => setItems(old => ({ ...old, [kind]: { ...old[kind], selected: e.target.checked } }))} />{names[kind]}</label>
            <label>Quantidade por personagem<input type="number" min={1} max={255} step={1} aria-label={`Quantidade de ${names[kind]}`} value={Number.isFinite(items[kind].quantity) ? items[kind].quantity : ''}
              onChange={e => setItems(old => ({ ...old, [kind]: { ...old[kind], quantity: e.target.valueAsNumber } }))} /></label>
          </div>)}</div>
          <div className="teleport-mail__selection"><strong>{recipients.length}/{eligible.length} personagens</strong>
            <Button type="button" size="sm" variant="ghost" onClick={() => setSelected(eligible.map(m => m.memberId))}>Marcar todos</Button>
            <Button type="button" size="sm" variant="ghost" onClick={() => setSelected([])}>Desmarcar todos</Button></div>
          <div className="teleport-mail__players" role="group" aria-label={reward ? "Destinatários do evento" : "Destinatários nível 50"}>{eligible.map(member => <label key={member.memberId}>
            <input type="checkbox" checked={selected.includes(member.memberId) && !blocked(member.nickname)} disabled={blocked(member.nickname)} onChange={e => setSelected(old => e.target.checked ? [...old, member.memberId] : old.filter(id => id !== member.memberId))} />{member.nickname}{blocked(member.nickname) ? " · já registrado / conferir" : ""}
          </label>)}</div>
        </fieldset>
        <p>{recipients.length * chosen.length} cartas · {chosen.map(kind => `${names[kind]}: ${recipients.length * items[kind].quantity} unidades`).join(' · ')}</p>
        {reward && members.some(member => member.nickname.toLowerCase() === sender.trim().toLowerCase()) && <p>Para entregar também ao remetente, use outro personagem no campo Remetente.</p>}
        <p>Uma carta por vez. Qualquer confirmação incerta interrompe o lote. Fechar esta janela não interrompe um lote iniciado.</p>
        {uncertain && !locked && <label><input type="checkbox" checked={confirmedUncertain} onChange={e => setConfirmedUncertain(e.target.checked)} />Conferi o último envio no jogo e revisei os destinatários deste novo lote.</label>}
        {(batches.error || start.error || control.error) && <p role="alert">{(batches.error || start.error || control.error)?.message}</p>}
        <Button size="sm" variant="ghost" onClick={() => void batches.refetch()}>Atualizar andamento</Button>
        {active && <div className="teleport-mail__selection">
          <strong>{states[active.state]}{active.control === 'pause' ? ' · pausa solicitada' : active.control === 'cancel' ? ' · cancelamento solicitado' : ''}</strong>
          <Button size="sm" disabled={control.isPending} onClick={() => control.mutate({ id: active.id, action: active.state === 'paused' ? 'resume' : 'pause' })}>{active.state === 'paused' ? 'Continuar' : 'Pausar após esta carta'}</Button>
          <Button size="sm" variant="danger" disabled={control.isPending} onClick={() => control.mutate({ id: active.id, action: 'cancel' })}>Cancelar restantes</Button>
        </div>}
        {batches.data?.batches.map(batch => <details key={batch.id} open={batchActive(batch) || batch.state === 'uncertain'}>
          <summary>{new Date(batch.createdAt * 1000).toLocaleString('pt-BR', { timeZone: SCHEDULE_TIME_ZONE })} (Brasília) · {simulation ? 'SIMULAÇÃO · ' : ''}{batch.request?.reward?.name ?? 'Teleportes'} · {batch.sender} · {states[batch.state] ?? batch.state} · {batch.letters.filter(l => l.state === 'sent').length}/{batch.letters.length} enviadas</summary>
          {batch.error && <p role="alert">{batch.error}</p>}
          {batch.state === 'planned' && <Button size="sm" variant="danger" disabled={control.isPending} onClick={() => control.mutate({ id: batch.id, action: 'cancel' })}>Cancelar reserva</Button>}
          <ul>{batch.letters.map(letter => <li key={letter.tracking}><strong>{letter.recipient}</strong> · {letter.quantity}× {letter.name} <code>{letter.itemCode}</code> · {states[letter.state] ?? letter.state}{letter.sentAt ? ` · ${new Date(letter.sentAt * 1000).toLocaleTimeString('pt-BR', { timeZone: SCHEDULE_TIME_ZONE })}` : ''}</li>)}</ul>
        </details>)}
      </div>
    </ConfirmDialog>
}
