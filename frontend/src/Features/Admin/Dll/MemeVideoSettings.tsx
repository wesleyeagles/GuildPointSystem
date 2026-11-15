import { useState, type FormEvent } from 'react'
import { useMemberRanking } from '@/Domain/Member/hooks/useMembers'
import { useMemeVideos, useSendMemeVideo, type SentMemeVideo } from '@/Domain/MemeVideo/hooks/useMemeVideo'
import { Button } from '@/Shared/ui/components/Button/Button'
import { Panel } from '@/Shared/ui/components/Panel/Panel'
import { useAppToast } from '@/Shared/ui/components/AppToast/AppToast'

function status(v: SentMemeVideo) {
  if (v.playedAt) return `abriu ${new Date(v.playedAt).toLocaleTimeString('pt-BR', { timeStyle: 'short' })}`
  return v.expired ? 'não abriu (expirou)' : 'esperando'
}

/**
 * Admin/Líder: manda um vídeo do YouTube para a DLL de um membro, que abre o vídeo numa janela no meio do jogo.
 */
export function MemeVideoSettings() {
  const { data } = useMemeVideos()
  if (!data?.canSend) return null
  return <MemeVideoPanel recent={data.recent} />
}

function MemeVideoPanel({ recent }: { recent: SentMemeVideo[] }) {
  const { showToast } = useAppToast()
  const { data: members = [] } = useMemberRanking()
  const send = useSendMemeVideo()
  const [member, setMember] = useState<{ id: number; nickname: string } | null>(null)
  const [memberSearch, setMemberSearch] = useState('')
  const [url, setUrl] = useState('')

  const term = memberSearch.trim().toLocaleLowerCase('pt-BR')
  const suggestions = term && !member
    ? members.filter((m) => m.nickname.toLocaleLowerCase('pt-BR').includes(term)).slice(0, 8)
    : []

  const submit = (e: FormEvent) => {
    e.preventDefault()
    if (!member || !url.trim()) return
    send.mutate({ memberId: member.id, url: url.trim() }, {
      onSuccess: () => {
        showToast(`Vídeo enviado para ${member.nickname}.`, 'success')
        setUrl('')
      },
      onError: (err) => showToast(err instanceof Error ? err.message : 'Não foi possível enviar.', 'error'),
    })
  }

  return (
    <Panel title="Vídeo no jogo" code={`${recent.length}`} className="market-watch">
      <p className="dll-admin__muted">
        Abre um vídeo do YouTube numa janela no meio do jogo de quem você escolher (DLL 1.45.0 ou mais nova, jogo em
        modo janela). O vídeo chega em até ~5 s; não chega durante a CW, em raid ou para quem desligou no perfil, e
        some se não abrir em 10 minutos. Um vídeo por membro a cada 10 minutos.
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
              aria-label="Membro que recebe o vídeo"
              value={memberSearch}
              onChange={(e) => setMemberSearch(e.target.value)}
            />
          )}
          {suggestions.length > 0 && (
            <ul className="chat-translator__suggestions market-watch__suggestions">
              {suggestions.map((m) => (
                <li key={m.id}>
                  <button type="button" onClick={() => setMember({ id: m.id, nickname: m.nickname })}>{m.nickname}</button>
                </li>
              ))}
            </ul>
          )}
        </div>
        <input
          className="chat-translator__search market-watch__field--item"
          placeholder="Link do YouTube (pode ter ?t= para começar no meio)"
          aria-label="Link do YouTube"
          value={url}
          onChange={(e) => setUrl(e.target.value)}
        />
        <Button size="sm" type="submit" loading={send.isPending} disabled={!member || !url.trim()}>Enviar</Button>
      </form>

      {recent.length > 0 && (
        <>
          <h4 className="chat-glossary__heading">Últimos envios</h4>
          <ul className="market-watch__alerts">
            {recent.map((v) => (
              <li key={v.id}>
                <span className="dll-admin__muted">
                  {new Date(v.createdAt).toLocaleString('pt-BR', { dateStyle: 'short', timeStyle: 'short' })}
                </span>
                {' '}{v.sentBy ?? '?'} → {v.nickname} ·{' '}
                <a href={`https://youtu.be/${v.youtubeId}${v.startSecond ? `?t=${v.startSecond}` : ''}`} target="_blank" rel="noreferrer">
                  {v.youtubeId}
                </a>
                {' '}· <span className="dll-admin__muted">{status(v)}</span>
              </li>
            ))}
          </ul>
        </>
      )}
    </Panel>
  )
}
