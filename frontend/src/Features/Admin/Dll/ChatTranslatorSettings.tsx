import { useState } from 'react'
import { useMemberRanking } from '@/Domain/Member/hooks/useMembers'
import { useChatTranslateAccess, useUpdateChatTranslateAccess, type ChatTranslateAccess } from '@/Domain/ChatTranslate/hooks/useChatTranslate'
import { Button } from '@/Shared/ui/components/Button/Button'
import { Panel } from '@/Shared/ui/components/Panel/Panel'
import { useAppToast } from '@/Shared/ui/components/AppToast/AppToast'
import { ChatGlossarySettings } from './ChatGlossarySettings'

/**
 * Administrador+: quem usa o tradutor do chat no jogo. A DLL desses membros mostra, no próprio chat, a
 * tradução em inglês das mensagens em russo (só na tela deles). Os demais não têm o recurso.
 */
export function ChatTranslatorSettings() {
  const { data: access } = useChatTranslateAccess()
  if (!access?.canConfigure) return null
  // A chave recria o formulário com a lista salva sempre que ela muda no servidor.
  return (
    <>
      <ChatTranslatorForm key={access.translators.map((t) => t.id).join(',')} access={access} />
      <ChatGlossarySettings access={access} />
    </>
  )
}

function ChatTranslatorForm({ access }: { access: ChatTranslateAccess }) {
  const { showToast } = useAppToast()
  const { data: members = [] } = useMemberRanking()
  const update = useUpdateChatTranslateAccess()
  const [translators, setTranslators] = useState(access.translators)
  const [search, setSearch] = useState('')

  const dirty = translators.map((t) => t.id).join(',') !== access.translators.map((t) => t.id).join(',')
  const term = search.trim().toLocaleLowerCase('pt-BR')
  const suggestions = term
    ? members.filter((m) => m.nickname.toLocaleLowerCase('pt-BR').includes(term) && !translators.some((t) => t.id === m.id)).slice(0, 8)
    : []

  const save = () => update.mutate({ memberIds: translators.map((t) => t.id) }, {
    onSuccess: () => showToast('Lista do tradutor atualizada.', 'success'),
    onError: (e) => showToast(e instanceof Error ? e.message : 'Não foi possível salvar.', 'error'),
  })

  return (
    <Panel title="Tradutor do chat (russo → inglês)" code={`${access.translators.length}`} className="chat-translator">
      <p className="dll-admin__muted">
        A DLL (v1.15.0 ou mais nova) desses membros traduz as mensagens em russo e mostra a tradução em verde no chat do
        jogo, só na tela deles. Mudanças valem em até 1 minuto.
      </p>
      <div className="chat-translator__chips">
        {translators.length === 0 && <em>Ninguém</em>}
        {translators.map((t) => (
          <span key={t.id} className="chat-translator__chip">
            {t.nickname}
            <button type="button" title={`Tirar ${t.nickname}`} onClick={() => setTranslators(translators.filter((x) => x.id !== t.id))}>✕</button>
          </span>
        ))}
      </div>
      <input
        className="chat-translator__search"
        type="search"
        placeholder="Adicionar membro pelo nick"
        aria-label="Adicionar membro ao tradutor"
        value={search}
        onChange={(e) => setSearch(e.target.value)}
      />
      {suggestions.length > 0 && (
        <ul className="chat-translator__suggestions">
          {suggestions.map((m) => (
            <li key={m.id}>
              <button type="button" onClick={() => { setTranslators([...translators, { id: m.id, nickname: m.nickname }]); setSearch('') }}>
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
