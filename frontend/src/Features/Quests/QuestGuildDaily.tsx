import { useState } from 'react'
import { useGuildBd } from '@/Domain/Quest/hooks/useGuildBd'
import { Panel } from '@/Shared/ui/components/Panel/Panel'
import { Button } from '@/Shared/ui/components/Button/Button'
import { serverNow } from '@/Shared/utils/serverClock'
import './QuestGuildDaily.styles.scss'

export function QuestGuildDaily() {
  const [day, setDay] = useState(() => new Intl.DateTimeFormat('en-CA', { timeZone: 'America/Sao_Paulo', year: 'numeric', month: '2-digit', day: '2-digit' }).format(new Date(serverNow())))
  const history = useGuildBd(day)
  const rows = history.data?.members ?? []
  return <Panel title="BD da guilda">
    <div className="quests__date-controls">
      <label>Dia <input type="date" value={day} onChange={e => { if (e.target.value) setDay(e.target.value) }} /></label>
      <Button size="sm" disabled={history.isFetching} onClick={() => void history.refetch()}>Atualizar</Button>
    </div>
    <p>Dia civil de Brasília · BD 50–65: {rows.filter(r => r.completedAt).length} conclusões observadas.</p>
    <p>BD 50–55 pendente de validação. Sem registro não significa que a quest não foi feita.</p>
    {history.isError && <p role="alert">Não foi possível atualizar: {history.error.message}</p>}
    {history.isLoading && <p role="status">Carregando…</p>}
    <div className="guild-bd-table"><table><thead><tr><th>Personagem</th><th>BD 50–55</th><th>BD 50–65</th></tr></thead>
      <tbody>{rows.map(r => <tr key={r.character}><th scope="row">{r.character}</th><td>Pendente de validação</td>
        <td>{r.completedAt ? <>Concluída · <time dateTime={r.completedAt}>{new Date(r.completedAt).toLocaleTimeString('pt-BR', { timeZone: 'America/Sao_Paulo' })}</time></> : 'Sem registro'}</td></tr>)}</tbody>
    </table></div>
    <p>Horário da primeira observação do sinal antes do OK. O histórico permanece após fechar a confirmação ou reconectar; não confirma o horário exato do loot nem o reset da quest.</p>
  </Panel>
}
