import { useCallback, useMemo, useState } from 'react'
import { useDeletePhrase, usePhraseBoard, useTogglePhraseVote } from '@/Domain/Phrase/hooks/usePhrases'
import type { Phrase } from '@/Domain/types/models'
import { useAuthContext } from '@/Features/Auth/contexts/AuthContext'
import { PhraseEditorModal } from '@/Features/Phrases/components/PhraseEditorModal'
import { useAppToast } from '@/Shared/ui/components/AppToast/AppToast'
import { Button } from '@/Shared/ui/components/Button/Button'
import { ConfirmDialog } from '@/Shared/ui/components/ConfirmDialog/ConfirmDialog'
import { Panel } from '@/Shared/ui/components/Panel/Panel'
import { SCHEDULE_TIME_ZONE } from '@/Shared/utils/scheduleTimeZone'
import './Phrases.styles.scss'

const formatStamp = (iso: string) =>
  new Date(iso).toLocaleString('pt-BR', {
    timeZone: SCHEDULE_TIME_ZONE,
    day: '2-digit',
    month: '2-digit',
    year: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  })

/** Podium order on screen: 2nd, 1st, 3rd. */
const PODIUM_SLOTS = [1, 0, 2] as const
const MEDALS = ['🥇', '🥈', '🥉']

function votesLabel(n: number) {
  return `${n} ${n === 1 ? 'voto' : 'votos'}`
}

/** Home: the guild's best quotes. Everyone votes on up to 3; admins add, edit and remove. */
export function PhrasesPage() {
  const { hasRole } = useAuthContext()
  const canManage = hasRole('ADMINISTRADOR')
  const { showToast } = useAppToast()
  const { data, isLoading } = usePhraseBoard()
  const toggleVote = useTogglePhraseVote()
  const deletePhrase = useDeletePhrase()

  const [editor, setEditor] = useState<{ phrase: Phrase | null } | null>(null)
  const [toDelete, setToDelete] = useState<Phrase | null>(null)
  const closeEditor = useCallback(() => setEditor(null), [])
  const closeDelete = useCallback(() => setToDelete(null), [])

  const phrases = useMemo(() => data?.phrases ?? [], [data])
  const myVotes = useMemo(() => new Set(data?.myVotes ?? []), [data])
  const maxVotes = data?.maxVotes ?? 3
  const votesLeft = Math.max(0, maxVotes - myVotes.size)

  /** Number shown on each card: its position in the order it was added. */
  const numberOf = useMemo(() => new Map(phrases.map((p, i) => [p.id, i + 1])), [phrases])
  const ranking = useMemo(
    () => phrases.filter((p) => p.votes > 0).sort((a, b) => b.votes - a.votes || a.id - b.id),
    [phrases],
  )
  const podium = ranking.slice(0, 3)

  const fail = (err: unknown) =>
    showToast(err instanceof Error ? err.message : 'Não foi possível concluir a ação.', 'error')

  const vote = (phrase: Phrase) => {
    const voted = myVotes.has(phrase.id)
    toggleVote.mutate({ id: phrase.id, voted }, { onError: fail })
  }

  const handleDelete = () => {
    if (!toDelete) return
    deletePhrase.mutate(toDelete.id, {
      onSuccess: () => {
        setToDelete(null)
        showToast('Frase excluída.', 'success')
      },
      onError: (err) => {
        setToDelete(null)
        fail(err)
      },
    })
  }

  return (
    <div className="phrases">
      <Panel title="Pódio das frases" code="TOP 3" className="phrases__podium-panel">
        {podium.length === 0 ? (
          <p className="phrases__empty">Ninguém votou ainda. Escolha suas 3 frases preferidas abaixo!</p>
        ) : (
          <div className="phrases__podium">
            {PODIUM_SLOTS.map((place) => {
              const phrase = podium[place]
              return (
                <div key={place} className={`phrases__step phrases__step--${place + 1}`}>
                  {phrase && (
                    <div className="phrases__step-card">
                      <span className="phrases__medal" aria-hidden="true">
                        {MEDALS[place]}
                      </span>
                      <p className="phrases__step-text">“{phrase.text}”</p>
                      <span className="phrases__step-author">{phrase.author ?? 'Desconhecido'}</span>
                      <span className="phrases__step-votes">{votesLabel(phrase.votes)}</span>
                    </div>
                  )}
                  <div className="phrases__step-base">
                    <span>{place + 1}º</span>
                  </div>
                </div>
              )
            })}
          </div>
        )}
      </Panel>

      <Panel
        title="Vote nas frases"
        code={`${phrases.length} FRASES`}
        actions={
          <div className="phrases__toolbar">
            <span className={`phrases__votes-left${votesLeft === 0 ? ' phrases__votes-left--out' : ''}`}>
              Seus votos: {myVotes.size}/{maxVotes}
            </span>
            {canManage && (
              <Button size="sm" onClick={() => setEditor({ phrase: null })}>
                + Nova frase
              </Button>
            )}
          </div>
        }
      >
        {isLoading && !data ? (
          <p className="phrases__empty">Carregando frases...</p>
        ) : phrases.length === 0 ? (
          <p className="phrases__empty">Nenhuma frase cadastrada.</p>
        ) : (
          <ul className="phrases__list">
            {phrases.map((phrase) => {
              const voted = myVotes.has(phrase.id)
              const blocked = !voted && votesLeft === 0
              const rank = ranking.indexOf(phrase)
              return (
                <li key={phrase.id} className={`phrases__card${voted ? ' phrases__card--voted' : ''}`}>
                  <span className="phrases__number">#{numberOf.get(phrase.id)}</span>
                  <div className="phrases__body">
                    <p className="phrases__text">
                      {rank >= 0 && rank < 3 && (
                        <span className="phrases__card-medal" title={`${rank + 1}º lugar`}>
                          {MEDALS[rank]}{' '}
                        </span>
                      )}
                      “{phrase.text}”
                    </p>
                    <p className="phrases__meta">
                      <span className={phrase.memberId ? 'phrases__author' : 'phrases__author phrases__author--unlinked'}>
                        {phrase.author ?? 'Desconhecido'}
                      </span>
                      {phrase.saidAt && <span> · {formatStamp(phrase.saidAt)}</span>}
                      {canManage && !phrase.memberId && <span className="phrases__warn"> · sem membro ligado</span>}
                    </p>
                    {phrase.voters.length > 0 && (
                      <p className="phrases__voters" title={phrase.voters.join(', ')}>
                        Votaram: {phrase.voters.join(', ')}
                      </p>
                    )}
                  </div>
                  <div className="phrases__side">
                    <span className="phrases__count">{votesLabel(phrase.votes)}</span>
                    <Button
                      size="sm"
                      variant={voted ? 'secondary' : 'primary'}
                      disabled={blocked || toggleVote.isPending}
                      title={blocked ? `Você já usou seus ${maxVotes} votos. Tire um voto para votar nesta.` : undefined}
                      onClick={() => vote(phrase)}
                    >
                      {voted ? 'Tirar voto' : 'Votar'}
                    </Button>
                    {canManage && (
                      <div className="phrases__admin">
                        <Button size="sm" variant="ghost" onClick={() => setEditor({ phrase })}>
                          Editar
                        </Button>
                        <Button size="sm" variant="danger" onClick={() => setToDelete(phrase)}>
                          Excluir
                        </Button>
                      </div>
                    )}
                  </div>
                </li>
              )
            })}
          </ul>
        )}
      </Panel>

      {editor && (
        <PhraseEditorModal key={editor.phrase?.id ?? 'new'} phrase={editor.phrase} onClose={closeEditor} />
      )}

      <ConfirmDialog
        open={toDelete !== null}
        title="Excluir frase"
        code="FRASE"
        confirmLabel="Excluir frase"
        loading={deletePhrase.isPending}
        onConfirm={handleDelete}
        onCancel={closeDelete}
      >
        <p>
          <strong>“{toDelete?.text}”</strong> será removida junto com os votos dela.
        </p>
      </ConfirmDialog>
    </div>
  )
}
