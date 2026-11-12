import { useState, type FormEvent } from 'react'
import {
  useDeleteChatGlossaryTerm,
  useSaveChatGlossaryTerm,
  type ChatGlossaryTerm,
  type ChatTranslateAccess,
} from '@/Domain/ChatTranslate/hooks/useChatTranslate'
import { Button } from '@/Shared/ui/components/Button/Button'
import { Panel } from '@/Shared/ui/components/Panel/Panel'
import { useAppToast } from '@/Shared/ui/components/AppToast/AppToast'

const SHOWN = 100

/**
 * Administrador+: glossário do tradutor do chat. Gírias e termos do jogo têm prioridade sobre o MyMemory
 * (que erra coisas como "хил" = heal); mensagem só com termos do glossário nem gasta a cota da API.
 * Também aprende sozinho: palavra solta traduzida pela API vira termo "aprendido", e frase em que todas as
 * palavras já são conhecidas é montada sem a API. Editar um aprendido e salvar torna o termo manual.
 */
export function ChatGlossarySettings({ access }: { access: ChatTranslateAccess }) {
  const { showToast } = useAppToast()
  const save = useSaveChatGlossaryTerm()
  const remove = useDeleteChatGlossaryTerm()
  const [source, setSource] = useState('')
  const [translation, setTranslation] = useState('')
  const [filter, setFilter] = useState('')

  const term = filter.trim().toLocaleLowerCase()
  const matches = term
    ? access.glossary.filter((t) => t.source.includes(term) || t.translation.toLocaleLowerCase().includes(term))
    : access.glossary
  const manual = matches.filter((t) => !t.learned)
  const learned = matches.filter((t) => t.learned)
  const learnedTotal = access.glossary.filter((t) => t.learned).length
  const fail = (e: unknown) => showToast(e instanceof Error ? e.message : 'Não foi possível salvar.', 'error')

  const edit = (t: ChatGlossaryTerm) => { setSource(t.source); setTranslation(t.translation) }
  const drop = (t: ChatGlossaryTerm) => remove.mutate(t.id, { onError: fail })

  const submit = (e: FormEvent) => {
    e.preventDefault()
    save.mutate({ source, translation }, {
      onSuccess: () => {
        showToast('Termo salvo no glossário.', 'success')
        setSource('')
        setTranslation('')
      },
      onError: fail,
    })
  }

  return (
    <Panel title="Glossário do tradutor" code={`${access.glossary.length - learnedTotal}`} className="chat-glossary">
      <p className="dll-admin__muted">
        Gírias e termos do jogo em russo com a tradução certa. Eles têm prioridade sobre o tradutor automático, e uma
        mensagem só com termos daqui nem gasta a cota. Vale palavra inteira ou expressão (ex.: «кинь инв»). Frases
        traduzidas pela API ficam salvas e não gastam a cota de novo:{' '}
        <strong>{access.savedTranslations.toLocaleString('pt-BR')}</strong> até agora.
      </p>
      <p className="dll-admin__muted">
        O glossário também aprende sozinho: quando a API traduz uma mensagem de uma palavra só, a palavra entra como
        aprendida (<strong>{learnedTotal.toLocaleString('pt-BR')}</strong> até agora), e uma frase em que todas as
        palavras já são conhecidas é montada sem gastar a cota. Clique num termo para corrigir: ao salvar, ele passa
        a ser manual.
      </p>

      <form className="chat-glossary__form" onSubmit={submit}>
        <input
          className="chat-glossary__input"
          placeholder="Termo em russo (ex.: хил)"
          aria-label="Termo em russo"
          maxLength={100}
          value={source}
          onChange={(e) => setSource(e.target.value)}
        />
        <span aria-hidden>→</span>
        <input
          className="chat-glossary__input"
          placeholder="Tradução em inglês (ex.: heal)"
          aria-label="Tradução em inglês"
          maxLength={100}
          value={translation}
          onChange={(e) => setTranslation(e.target.value)}
        />
        <Button size="sm" type="submit" loading={save.isPending} disabled={!source.trim() || !translation.trim()}>
          Salvar termo
        </Button>
      </form>

      <input
        className="chat-glossary__input chat-glossary__filter"
        type="search"
        placeholder="Procurar no glossário"
        aria-label="Procurar no glossário"
        value={filter}
        onChange={(e) => setFilter(e.target.value)}
      />

      <h4 className="chat-glossary__heading">Manuais ({manual.length.toLocaleString('pt-BR')})</h4>
      <TermList terms={manual} onEdit={edit} onRemove={drop} removing={remove.isPending} />
      <h4 className="chat-glossary__heading">Aprendidos ({learned.length.toLocaleString('pt-BR')})</h4>
      <TermList terms={learned} onEdit={edit} onRemove={drop} removing={remove.isPending} />
    </Panel>
  )
}

function TermList({ terms, onEdit, onRemove, removing }: {
  terms: ChatGlossaryTerm[]
  onEdit: (t: ChatGlossaryTerm) => void
  onRemove: (t: ChatGlossaryTerm) => void
  removing: boolean
}) {
  return (
    <>
      {terms.length === 0 ? (
        <p className="dll-admin__muted">Nenhum termo.</p>
      ) : (
        <ul className="chat-glossary__list">
          {terms.slice(0, SHOWN).map((t) => (
            <li key={t.id}>
              <button
                type="button"
                className="chat-glossary__term"
                title="Editar"
                onClick={() => onEdit(t)}
              >
                <span>{t.source}</span> → {t.translation}
              </button>
              <button
                type="button"
                className="chat-glossary__remove"
                title={`Tirar ${t.source}`}
                disabled={removing}
                onClick={() => onRemove(t)}
              >
                ✕
              </button>
            </li>
          ))}
        </ul>
      )}
      {terms.length > SHOWN && (
        <p className="dll-admin__muted">Mostrando {SHOWN} de {terms.length}; use a busca para achar os outros.</p>
      )}
    </>
  )
}
