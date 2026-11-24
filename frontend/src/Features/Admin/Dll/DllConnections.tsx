import { useState } from 'react'
import { useDllConnections } from '@/Domain/Dll/hooks/useDll'
import { Button } from '@/Shared/ui/components/Button/Button'
import { Panel } from '@/Shared/ui/components/Panel/Panel'

export function DllConnections() {
  const query = useDllConnections()
  const [sharedOnly, setSharedOnly] = useState(true)
  const connections = query.data ?? []
  const visible = sharedOnly ? connections.filter(row => row.sameIpCharacters > 1) : connections
  return <Panel title="Conexões recentes da DLL" code="ADM" actions={
    <Button size="sm" variant="secondary" disabled={query.isFetching} onClick={() => void query.refetch()}>Atualizar conexões</Button>
  }>
    <p className="dll-admin__muted">Uploads nos 3 minutos anteriores à consulta. IP compartilhado não comprova que seja a mesma pessoa.</p>
    <label className="dll-admin__filter"><input type="checkbox" checked={sharedOnly} onChange={event => setSharedOnly(event.target.checked)} />Somente IPs compartilhados</label>
    {query.dataUpdatedAt > 0 && <p className="dll-admin__muted">Consulta: {new Date(query.dataUpdatedAt).toLocaleString('pt-BR', { timeZone: 'America/Sao_Paulo' })} (Brasília) · {connections.length} personagens</p>}
    {query.isPending ? <p>Carregando conexões...</p> : query.error ? <p role="alert">Não foi possível consultar os IPs. {query.error.message}</p> : visible.length === 0 ?
      <p>{connections.length === 0 ? 'Nenhum upload recente com IP identificado. Aguarde novos envios e confira a configuração do proxy no backend.' : 'Nenhum IP compartilhado nesta consulta.'}</p> :
      <div className="dll-admin__connections"><table className="dll-admin__table" aria-label="Conexões recentes da DLL"><thead><tr>
        <th>Personagem</th><th>IP do upload</th><th>Último envio (Brasília)</th><th>Personagens neste IP</th>
      </tr></thead><tbody>{visible.map(row => <tr key={row.character.toLowerCase()}>
        <td>{row.character}{row.nickname.toLowerCase() !== row.character.toLowerCase() && <small> · {row.nickname}</small>}</td>
        <td><code>{row.ip}</code></td>
        <td>{new Date(row.lastUploadAt).toLocaleString('pt-BR', { timeZone: 'America/Sao_Paulo' })}</td>
        <td>{row.sameIpCharacters}</td>
      </tr>)}</tbody></table></div>}
  </Panel>
}
