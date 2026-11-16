import { useState, type FormEvent } from 'react'
import { useCurrentMember, useMemberRanking } from '@/Domain/Member/hooks/useMembers'
import { useCreateRoulette, useRoulettes, type SentRoulette } from '@/Domain/Roulette/hooks/useRoulette'
import { Button } from '@/Shared/ui/components/Button/Button'
import { Panel } from '@/Shared/ui/components/Panel/Panel'
import { useAppToast } from '@/Shared/ui/components/AppToast/AppToast'

const MAX_ENTRIES = 40
const MAX_PRIZE = 60

type Chosen = { id: number; nickname: string }

function result(r: SentRoulette) {
  if (r.spinning) return 'girando...'
  return r.winner ? `🏆 ${r.winner}` : '?'
}

function audience(r: SentRoulette) {
  if (r.allMembers) return 'todos veem'
  return r.viewers.length === 1 ? `só ${r.viewers[0]} vê` : `${r.viewers.length} veem`
}

/** Lista de membros com busca: chips com ✕ e sugestões pelo nick. */
function MemberPicker({ label, placeholder, picked, onChange, max, disabled }: {
  label: string
  placeholder: string
  picked: Chosen[]
  onChange: (next: Chosen[]) => void
  max?: number
  disabled?: boolean
}) {
  const { data: members = [] } = useMemberRanking()
  const [search, setSearch] = useState('')
  const chosen = new Set(picked.map((m) => m.id))
  const full = max !== undefined && picked.length >= max
  const term = search.trim().toLocaleLowerCase('pt-BR')
  const suggestions = term && !full && !disabled
    ? members.filter((m) => !chosen.has(m.id) && m.nickname.toLocaleLowerCase('pt-BR').includes(term)).slice(0, 8)
    : []

  const add = (m: Chosen) => {
    if (full || chosen.has(m.id)) return
    onChange([...picked, { id: m.id, nickname: m.nickname }])
    setSearch('')
  }

  return (
    <>
      <div className="market-watch__field">
        <input
          className="chat-translator__search"
          type="search"
          placeholder={full ? `Máximo de ${max} membros` : placeholder}
          aria-label={label}
          value={search}
          disabled={full || disabled}
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
      {picked.length > 0 && !disabled && (
        <ul className="chat-glossary__list roulette__chips">
          {picked.map((m) => (
            <li key={m.id}>
              {m.nickname}
              <button
                type="button"
                className="chat-glossary__remove"
                title="Tirar"
                onClick={() => onChange(picked.filter((x) => x.id !== m.id))}
              >
                ✕
              </button>
            </li>
          ))}
        </ul>
      )}
    </>
  )
}

/**
 * Admin/Líder: escolhe os membros, o prêmio e quem vê; o site sorteia e a DLL mostra a roleta girando no meio do jogo.
 */
export function RouletteSettings() {
  const { data } = useRoulettes()
  if (!data?.canCreate) return null
  return <RoulettePanel recent={data.recent} />
}

function RoulettePanel({ recent }: { recent: SentRoulette[] }) {
  const { showToast } = useAppToast()
  const { data: me } = useCurrentMember()
  const create = useCreateRoulette()
  const [entrants, setEntrants] = useState<Chosen[]>([])
  // null = ainda não mexeu: começa só com você (teste).
  const [viewersPicked, setViewers] = useState<Chosen[] | null>(null)
  const [allMembers, setAllMembers] = useState(false)
  const [prize, setPrize] = useState('')

  const viewers = viewersPicked ?? (me ? [{ id: me.id, nickname: me.nickname }] : [])
  const spinning = recent.some((r) => r.spinning)
  const canSpin = entrants.length >= 2 && prize.trim() !== '' && (allMembers || viewers.length > 0) && !spinning

  const addEntrantsToViewers = () => {
    const have = new Set(viewers.map((v) => v.id))
    setViewers([...viewers, ...entrants.filter((e) => !have.has(e.id))])
  }

  const submit = (e: FormEvent) => {
    e.preventDefault()
    if (!canSpin) return
    create.mutate({
      prize: prize.trim(),
      memberIds: entrants.map((m) => m.id),
      viewerIds: allMembers ? [] : viewers.map((m) => m.id),
      allMembers,
    }, {
      onSuccess: () => showToast('Roleta enviada para o jogo. Gira em ~7 s.', 'success'),
      onError: (err) => showToast(err instanceof Error ? err.message : 'Não foi possível criar a roleta.', 'error'),
    })
  }

  return (
    <Panel title="Roleta no jogo" code={`${recent.length}`} className="market-watch">
      <p className="dll-admin__muted">
        Escolha os membros (2 a {MAX_ENTRIES}), o prêmio ou motivo e quem vê. O site sorteia na hora e a roleta aparece
        no meio da tela do jogo de quem vê (DLL 1.47.0 ou mais nova): conta 7 s, gira 10 s e mostra o vencedor. Chega
        em até ~5 s, então todos veem girar junto. Uma roleta por vez.
      </p>

      <form onSubmit={submit}>
        <h4 className="chat-glossary__heading">Na roleta ({entrants.length})</h4>
        <div className="market-watch__form roulette__row">
          <MemberPicker
            label="Adicionar membro à roleta"
            placeholder="Adicionar membro (nick)"
            picked={entrants}
            onChange={setEntrants}
            max={MAX_ENTRIES}
          />
          {entrants.length > 0 && (
            <Button size="sm" variant="ghost" type="button" onClick={() => setEntrants([])}>Limpar</Button>
          )}
        </div>

        <h4 className="chat-glossary__heading">Quem vê no jogo ({allMembers ? 'todos' : viewers.length})</h4>
        <div className="market-watch__form roulette__row">
          <MemberPicker
            label="Adicionar quem vê a roleta"
            placeholder="Adicionar quem vê (nick)"
            picked={viewers}
            onChange={setViewers}
            disabled={allMembers}
          />
          <Button
            size="sm"
            variant="secondary"
            type="button"
            disabled={allMembers || entrants.length === 0}
            onClick={addEntrantsToViewers}
            title="Coloca todos os participantes da roleta na lista de quem vê"
          >
            + Todos da roleta
          </Button>
          {viewers.length > 0 && !allMembers && (
            <Button size="sm" variant="ghost" type="button" onClick={() => setViewers([])}>Limpar</Button>
          )}
          <label className="dll-admin__filter">
            <input type="checkbox" checked={allMembers} onChange={(e) => setAllMembers(e.target.checked)} />
            Todos os membros
          </label>
        </div>

        <div className="market-watch__form">
          <input
            className="chat-translator__search market-watch__field--item"
            placeholder="Prêmio ou motivo (ex.: Arma +7, quem paga o BD...)"
            aria-label="Prêmio ou motivo da roleta"
            maxLength={MAX_PRIZE}
            value={prize}
            onChange={(e) => setPrize(e.target.value)}
          />
          <Button size="sm" type="submit" loading={create.isPending} disabled={!canSpin}>Girar</Button>
        </div>
      </form>

      {recent.length > 0 && (
        <>
          <h4 className="chat-glossary__heading">Últimas roletas</h4>
          <ul className="market-watch__alerts">
            {recent.map((r) => (
              <li key={r.id} title={`Na roleta: ${r.entries.join(', ')}${r.viewers.length ? `\nVeem: ${r.viewers.join(', ')}` : ''}`}>
                <span className="dll-admin__muted">
                  {new Date(r.createdAt).toLocaleString('pt-BR', { dateStyle: 'short', timeStyle: 'short' })}
                </span>
                {' '}{r.createdBy ?? '?'} · <strong>{r.prize}</strong> · {r.entries.length} membros
                <span className="dll-admin__muted"> · {audience(r)}</span>
                {' '}→ {result(r)}
              </li>
            ))}
          </ul>
        </>
      )}
    </Panel>
  )
}
