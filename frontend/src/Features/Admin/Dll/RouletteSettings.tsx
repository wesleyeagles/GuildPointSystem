import { useState, type FormEvent } from 'react'
import { useMemberRanking } from '@/Domain/Member/hooks/useMembers'
import { useCreateRoulette, useRoulettes, type SentRoulette } from '@/Domain/Roulette/hooks/useRoulette'
import { Button } from '@/Shared/ui/components/Button/Button'
import { Panel } from '@/Shared/ui/components/Panel/Panel'
import { useAppToast } from '@/Shared/ui/components/AppToast/AppToast'

const MAX_ENTRIES = 40
const MAX_PRIZE = 60

type Entrant = { id: number; nickname: string }

function result(r: SentRoulette) {
  if (r.spinning) return 'girando...'
  return r.winner ? `🏆 ${r.winner}` : '?'
}

/**
 * Admin/Líder: escolhe os membros e o prêmio; o site sorteia e a DLL mostra a roleta girando no meio do jogo.
 */
export function RouletteSettings() {
  const { data } = useRoulettes()
  if (!data?.canCreate) return null
  return <RoulettePanel recent={data.recent} />
}

function RoulettePanel({ recent }: { recent: SentRoulette[] }) {
  const { showToast } = useAppToast()
  const { data: members = [] } = useMemberRanking()
  const create = useCreateRoulette()
  const [entrants, setEntrants] = useState<Entrant[]>([])
  const [search, setSearch] = useState('')
  const [prize, setPrize] = useState('')
  const [leadersOnly, setLeadersOnly] = useState(true)

  const chosen = new Set(entrants.map((e) => e.id))
  const term = search.trim().toLocaleLowerCase('pt-BR')
  const suggestions = term
    ? members.filter((m) => !chosen.has(m.id) && m.nickname.toLocaleLowerCase('pt-BR').includes(term)).slice(0, 8)
    : []
  const full = entrants.length >= MAX_ENTRIES
  const spinning = recent.some((r) => r.spinning)

  const add = (m: Entrant) => {
    if (full || chosen.has(m.id)) return
    setEntrants([...entrants, { id: m.id, nickname: m.nickname }])
    setSearch('')
  }

  const submit = (e: FormEvent) => {
    e.preventDefault()
    if (entrants.length < 2 || !prize.trim()) return
    create.mutate({ prize: prize.trim(), memberIds: entrants.map((m) => m.id), leadersOnly }, {
      onSuccess: () => showToast('Roleta enviada para o jogo. Gira em ~7 s.', 'success'),
      onError: (err) => showToast(err instanceof Error ? err.message : 'Não foi possível criar a roleta.', 'error'),
    })
  }

  return (
    <Panel title="Roleta no jogo" code={`${recent.length}`} className="market-watch">
      <p className="dll-admin__muted">
        Escolha os membros (2 a {MAX_ENTRIES}) e o prêmio ou motivo. O site sorteia na hora e a roleta aparece no meio
        da tela do jogo (DLL 1.47.0 ou mais nova): conta 7 s, gira 10 s e mostra o vencedor. Chega em até ~5 s, então
        quem estiver no jogo vê girar junto. Uma roleta por vez.
      </p>

      <form className="market-watch__form" onSubmit={submit}>
        <div className="market-watch__field">
          <input
            className="chat-translator__search"
            type="search"
            placeholder={full ? `Máximo de ${MAX_ENTRIES} membros` : 'Adicionar membro (nick)'}
            aria-label="Adicionar membro à roleta"
            value={search}
            disabled={full}
            onChange={(e) => setSearch(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === 'Enter' && suggestions.length > 0) {
                e.preventDefault()
                add(suggestions[0])
              }
            }}
          />
          {suggestions.length > 0 && (
            <ul className="chat-translator__suggestions market-watch__suggestions">
              {suggestions.map((m) => (
                <li key={m.id}>
                  <button type="button" onClick={() => add(m)}>{m.nickname}</button>
                </li>
              ))}
            </ul>
          )}
        </div>
        <input
          className="chat-translator__search market-watch__field--item"
          placeholder="Prêmio ou motivo (ex.: Arma +7, quem paga o BD...)"
          aria-label="Prêmio ou motivo da roleta"
          maxLength={MAX_PRIZE}
          value={prize}
          onChange={(e) => setPrize(e.target.value)}
        />
        <label className="dll-admin__filter">
          <input type="checkbox" checked={leadersOnly} onChange={(e) => setLeadersOnly(e.target.checked)} />
          Só o Líder vê (teste)
        </label>
        <Button
          size="sm"
          type="submit"
          loading={create.isPending}
          disabled={entrants.length < 2 || !prize.trim() || spinning}
        >
          Girar
        </Button>
      </form>

      <h4 className="chat-glossary__heading">Na roleta ({entrants.length})</h4>
      {entrants.length === 0 ? (
        <p className="dll-admin__muted">Ninguém ainda.</p>
      ) : (
        <div className="chat-glossary__list-row">
          <ul className="chat-glossary__list">
            {entrants.map((m) => (
              <li key={m.id}>
                {m.nickname}
                <button
                  type="button"
                  className="chat-glossary__remove"
                  title="Tirar da roleta"
                  onClick={() => setEntrants(entrants.filter((x) => x.id !== m.id))}
                >
                  ✕
                </button>
              </li>
            ))}
          </ul>
          <Button size="sm" variant="ghost" type="button" onClick={() => setEntrants([])}>Limpar</Button>
        </div>
      )}

      {recent.length > 0 && (
        <>
          <h4 className="chat-glossary__heading">Últimas roletas</h4>
          <ul className="market-watch__alerts">
            {recent.map((r) => (
              <li key={r.id} title={r.entries.join(', ')}>
                <span className="dll-admin__muted">
                  {new Date(r.createdAt).toLocaleString('pt-BR', { dateStyle: 'short', timeStyle: 'short' })}
                </span>
                {' '}{r.createdBy ?? '?'} · <strong>{r.prize}</strong> · {r.entries.length} membros
                {r.leadersOnly && <span className="dll-admin__muted"> · só Líder</span>}
                {' '}→ {result(r)}
              </li>
            ))}
          </ul>
        </>
      )}
    </Panel>
  )
}
