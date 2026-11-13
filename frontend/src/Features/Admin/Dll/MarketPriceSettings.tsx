import { useState } from 'react'
import { useMemberRanking } from '@/Domain/Member/hooks/useMembers'
import { useMarketPriceAccess, useUpdateMarketPriceAccess, type MarketPriceAccess } from '@/Domain/MarketPrice/hooks/useMarketPrice'
import { Button } from '@/Shared/ui/components/Button/Button'
import { Panel } from '@/Shared/ui/components/Panel/Panel'
import { useAppToast } from '@/Shared/ui/components/AppToast/AppToast'

/**
 * Líder: quem vê o preço do market da Cerberus no tooltip de item do jogo. A DLL desses membros acrescenta
 * duas linhas ao tooltip (menor preço e média, do mesmo upgrade). Os demais não têm o recurso.
 */
export function MarketPriceSettings() {
  const { data: access } = useMarketPriceAccess()
  if (!access?.canConfigure) return null
  // A chave recria o formulário com a lista salva sempre que ela muda no servidor.
  return <MarketPriceForm key={access.viewers.map((v) => v.id).join(',')} access={access} />
}

function MarketPriceForm({ access }: { access: MarketPriceAccess }) {
  const { showToast } = useAppToast()
  const { data: members = [] } = useMemberRanking()
  const update = useUpdateMarketPriceAccess()
  const [viewers, setViewers] = useState(access.viewers)
  const [search, setSearch] = useState('')

  const dirty = viewers.map((v) => v.id).join(',') !== access.viewers.map((v) => v.id).join(',')
  const term = search.trim().toLocaleLowerCase('pt-BR')
  const suggestions = term
    ? members.filter((m) => m.nickname.toLocaleLowerCase('pt-BR').includes(term) && !viewers.some((v) => v.id === m.id)).slice(0, 8)
    : []

  const save = () => update.mutate({ memberIds: viewers.map((v) => v.id) }, {
    onSuccess: () => showToast('Lista do preço do market atualizada.', 'success'),
    onError: (e) => showToast(e instanceof Error ? e.message : 'Não foi possível salvar.', 'error'),
  })

  return (
    <Panel title="Preço do market no tooltip" code={`${access.viewers.length}`} className="chat-translator">
      <p className="dll-admin__muted">
        A DLL (v1.37.0 ou mais nova) desses membros mostra, no tooltip do item, o menor preço e a média do market da
        Cerberus para o mesmo upgrade. Só você edita esta lista. Mudanças valem quando o jogo for reaberto.
      </p>
      <div className="chat-translator__chips">
        {viewers.length === 0 && <em>Ninguém</em>}
        {viewers.map((v) => (
          <span key={v.id} className="chat-translator__chip">
            {v.nickname}
            <button type="button" title={`Tirar ${v.nickname}`} onClick={() => setViewers(viewers.filter((x) => x.id !== v.id))}>✕</button>
          </span>
        ))}
      </div>
      <input
        className="chat-translator__search"
        type="search"
        placeholder="Adicionar membro pelo nick"
        aria-label="Adicionar membro ao preço do market"
        value={search}
        onChange={(e) => setSearch(e.target.value)}
      />
      {suggestions.length > 0 && (
        <ul className="chat-translator__suggestions">
          {suggestions.map((m) => (
            <li key={m.id}>
              <button type="button" onClick={() => { setViewers([...viewers, { id: m.id, nickname: m.nickname }]); setSearch('') }}>
                {m.nickname}
              </button>
            </li>
          ))}
        </ul>
      )}
      <div className="chat-translator__actions">
        <Button size="sm" loading={update.isPending} disabled={!dirty} onClick={save}>Salvar lista</Button>
      </div>
    </Panel>
  )
}
