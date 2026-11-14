import { useEffect, useState, type FormEvent } from 'react'
import { useMemberRanking } from '@/Domain/Member/hooks/useMembers'
import {
  useAddMarketWatch,
  useMarketWatchItems,
  useMarketWatches,
  useRemoveMarketWatch,
  type MarketAlert,
  type MarketWatch,
} from '@/Domain/MarketPrice/hooks/useMarketWatch'
import { Button } from '@/Shared/ui/components/Button/Button'
import { Panel } from '@/Shared/ui/components/Panel/Panel'
import { useAppToast } from '@/Shared/ui/components/AppToast/AppToast'

const cs = (n: number) => `${n.toLocaleString('pt-BR')} CS`

/**
 * Admin/Líder: "membro quer o item X". O site confere o market da Cerberus a cada ~2 min e, quando aparece um
 * anúncio que serve, a DLL desse membro mostra um card no jogo com o item e o preço.
 */
export function MarketWatchSettings() {
  const { data } = useMarketWatches()
  if (!data?.canConfigure) return null
  return <MarketWatchPanel watches={data.watches} alerts={data.recentAlerts} />
}

function MarketWatchPanel({ watches, alerts }: { watches: MarketWatch[]; alerts: MarketAlert[] }) {
  const { showToast } = useAppToast()
  const { data: members = [] } = useMemberRanking()
  const add = useAddMarketWatch()
  const remove = useRemoveMarketWatch()

  const [member, setMember] = useState<{ id: number; nickname: string } | null>(null)
  const [memberSearch, setMemberSearch] = useState('')
  const [item, setItem] = useState<{ code: string; name: string } | null>(null)
  const [itemSearch, setItemSearch] = useState('')
  const [debounced, setDebounced] = useState('')
  const [minUpgrade, setMinUpgrade] = useState('')
  const [maxPrice, setMaxPrice] = useState('')

  useEffect(() => {
    const t = setTimeout(() => setDebounced(itemSearch), 300)
    return () => clearTimeout(t)
  }, [itemSearch])
  const { data: items = [], isFetching } = useMarketWatchItems(item ? '' : debounced)

  const term = memberSearch.trim().toLocaleLowerCase('pt-BR')
  const memberSuggestions = term && !member
    ? members.filter((m) => m.nickname.toLocaleLowerCase('pt-BR').includes(term)).slice(0, 8)
    : []

  const fail = (e: unknown) => showToast(e instanceof Error ? e.message : 'Não foi possível salvar.', 'error')

  const submit = (e: FormEvent) => {
    e.preventDefault()
    if (!member || !item) return
    const price = maxPrice.replace(/\D/g, '')
    add.mutate({
      memberId: member.id,
      itemCode: item.code,
      itemName: item.name,
      minUpgrade: minUpgrade ? Number(minUpgrade) : null,
      maxPrice: price ? Number(price) : null,
    }, {
      onSuccess: () => {
        showToast(`Alerta criado: ${member.nickname} → ${item.name}.`, 'success')
        setItem(null)
        setItemSearch('')
        setMinUpgrade('')
        setMaxPrice('')
      },
      onError: fail,
    })
  }

  return (
    <Panel title="Alertas de item no market" code={`${watches.length}`} className="market-watch">
      <p className="dll-admin__muted">
        Quando um item da lista aparece à venda no market da Cerberus, a DLL do membro escolhido mostra um aviso no
        jogo com o item e o preço. O site confere o market a cada ~2 minutos; cada anúncio avisa uma vez. Upgrade
        mínimo e preço máximo (por unidade) são opcionais. Só Admin e Líder editam esta lista.
      </p>

      <form className="market-watch__form" onSubmit={submit}>
        <div className="market-watch__field">
          {member ? (
            <span className="chat-translator__chip">
              {member.nickname}
              <button type="button" title="Trocar membro" onClick={() => { setMember(null); setMemberSearch('') }}>✕</button>
            </span>
          ) : (
            <input
              className="chat-translator__search"
              type="search"
              placeholder="Membro (nick)"
              aria-label="Membro que recebe o alerta"
              value={memberSearch}
              onChange={(e) => setMemberSearch(e.target.value)}
            />
          )}
          {memberSuggestions.length > 0 && (
            <ul className="chat-translator__suggestions market-watch__suggestions">
              {memberSuggestions.map((m) => (
                <li key={m.id}>
                  <button type="button" onClick={() => setMember({ id: m.id, nickname: m.nickname })}>{m.nickname}</button>
                </li>
              ))}
            </ul>
          )}
        </div>

        <div className="market-watch__field market-watch__field--item">
          {item ? (
            <span className="chat-translator__chip">
              {item.name} <small>({item.code})</small>
              <button type="button" title="Trocar item" onClick={() => { setItem(null); setItemSearch('') }}>✕</button>
            </span>
          ) : (
            <input
              className="chat-translator__search"
              type="search"
              placeholder="Item (nome ou código)"
              aria-label="Item"
              value={itemSearch}
              onChange={(e) => setItemSearch(e.target.value)}
            />
          )}
          {!item && debounced.trim().length >= 2 && (
            <ul className="chat-translator__suggestions market-watch__suggestions">
              {items.length === 0 && <li className="market-watch__empty">{isFetching ? 'Procurando...' : 'Nenhum item.'}</li>}
              {items.map((i) => (
                <li key={i.code}>
                  <button type="button" onClick={() => setItem(i)}>{i.name} <small>({i.code})</small></button>
                </li>
              ))}
            </ul>
          )}
        </div>

        <select
          className="market-watch__small"
          aria-label="Upgrade mínimo"
          value={minUpgrade}
          onChange={(e) => setMinUpgrade(e.target.value)}
        >
          <option value="">Qualquer upgrade</option>
          {[1, 2, 3, 4, 5, 6, 7].map((u) => <option key={u} value={u}>+{u} ou mais</option>)}
        </select>
        <input
          className="market-watch__small"
          inputMode="numeric"
          placeholder="Preço máx. (CS/un.)"
          aria-label="Preço máximo por unidade em CS"
          value={maxPrice}
          onChange={(e) => setMaxPrice(e.target.value.replace(/[^\d.]/g, ''))}
        />
        <Button size="sm" type="submit" loading={add.isPending} disabled={!member || !item}>Adicionar</Button>
      </form>

      {watches.length === 0 ? (
        <p className="dll-admin__muted">Nenhum alerta.</p>
      ) : (
        <table className="market-watch__table">
          <thead>
            <tr><th>Membro</th><th>Item</th><th>Upgrade</th><th>Preço máx.</th><th /></tr>
          </thead>
          <tbody>
            {watches.map((w) => (
              <tr key={w.id}>
                <td>{w.nickname}</td>
                <td>{w.itemName} <small className="dll-admin__muted">({w.itemCode})</small></td>
                <td>{w.minUpgrade ? `+${w.minUpgrade} ou mais` : 'Qualquer'}</td>
                <td>{w.maxPrice ? `${cs(w.maxPrice)}/un.` : 'Qualquer'}</td>
                <td>
                  <button
                    type="button"
                    className="market-watch__remove"
                    title={`Tirar ${w.itemName} de ${w.nickname}`}
                    disabled={remove.isPending}
                    onClick={() => remove.mutate(w.id, { onError: fail })}
                  >
                    ✕
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}

      {alerts.length > 0 && (
        <>
          <h4 className="chat-glossary__heading">Últimos avisos</h4>
          <ul className="market-watch__alerts">
            {alerts.map((a) => (
              <li key={a.id}>
                <span className="dll-admin__muted">{new Date(a.createdAt).toLocaleString('pt-BR', { dateStyle: 'short', timeStyle: 'short' })}</span>
                {' '}{a.nickname} · {a.itemName}{a.upgrade > 0 ? ` +${a.upgrade}` : ''}{a.quantity > 1 ? ` x${a.quantity}` : ''} · {cs(a.price)}
              </li>
            ))}
          </ul>
        </>
      )}
    </Panel>
  )
}
