import { useMemo, useState } from 'react'
import { createPortal } from 'react-dom'
import { useMemberRanking } from '@/Domain/Member/hooks/useMembers'
import { useSavePhrase } from '@/Domain/Phrase/hooks/usePhrases'
import type { Phrase } from '@/Domain/types/models'
import { useAppToast } from '@/Shared/ui/components/AppToast/AppToast'
import { Button } from '@/Shared/ui/components/Button/Button'
import '@/Features/Auth/Auth.styles.scss'

/** ISO → value for <input type="datetime-local"> in the browser's time zone. */
function toLocalInput(iso: string | null) {
  const d = iso ? new Date(iso) : new Date()
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`
}

interface PhraseEditorModalProps {
  /** null = new phrase. */
  phrase: Phrase | null
  onClose: () => void
}

export function PhraseEditorModal({ phrase, onClose }: PhraseEditorModalProps) {
  const { showToast } = useAppToast()
  const { data: members = [] } = useMemberRanking()
  const save = useSavePhrase()

  const [text, setText] = useState(phrase?.text ?? '')
  const [memberId, setMemberId] = useState<number | ''>(phrase?.memberId ?? '')
  const [saidAt, setSaidAt] = useState(() => toLocalInput(phrase?.saidAt ?? null))

  const sortedMembers = useMemo(
    () => [...members].sort((a, b) => a.nickname.localeCompare(b.nickname, 'pt-BR', { sensitivity: 'base' })),
    [members],
  )
  const canSubmit = text.trim().length > 0 && memberId !== ''

  const submit = (e: React.FormEvent) => {
    e.preventDefault()
    if (!canSubmit) return
    save.mutate(
      {
        id: phrase?.id,
        text: text.trim(),
        memberId: Number(memberId),
        saidAt: saidAt ? new Date(saidAt).toISOString() : null,
      },
      {
        onSuccess: () => {
          showToast(phrase ? 'Frase atualizada.' : 'Frase cadastrada.', 'success')
          onClose()
        },
        onError: (err) => showToast(err instanceof Error ? err.message : 'Não foi possível salvar.', 'error'),
      },
    )
  }

  return createPortal(
    <div className="modal-overlay" role="presentation">
      <form
        className="modal-card phrase-editor"
        role="dialog"
        aria-modal="true"
        aria-labelledby="phrase-editor-title"
        onSubmit={submit}
      >
        <div className="auth-window-bar">
          <span id="phrase-editor-title">{phrase ? 'Editar frase' : 'Nova frase'}</span>
          <span className="auth-window-bar__code">FRASE</span>
        </div>
        <div className="auth-card-content phrase-editor__body">
          <label className="phrase-editor__field">
            <span>Frase</span>
            <textarea
              value={text}
              maxLength={300}
              rows={3}
              onChange={(e) => setText(e.target.value)}
              placeholder="Ex.: vou almoçar agora"
              autoFocus
              required
            />
          </label>
          <label className="phrase-editor__field">
            <span>Quem falou</span>
            <select
              value={memberId}
              onChange={(e) => setMemberId(e.target.value ? Number(e.target.value) : '')}
              required
            >
              <option value="">
                {phrase && !phrase.memberId && phrase.author ? `Escolha (anotado como "${phrase.author}")` : 'Escolha o membro'}
              </option>
              {sortedMembers.map((m) => (
                <option key={m.id} value={m.id}>
                  {m.nickname}
                </option>
              ))}
            </select>
          </label>
          <label className="phrase-editor__field">
            <span>Quando</span>
            <input type="datetime-local" value={saidAt} onChange={(e) => setSaidAt(e.target.value)} />
          </label>
          <div className="phrase-editor__actions">
            <Button type="button" variant="secondary" onClick={onClose} disabled={save.isPending}>
              Cancelar
            </Button>
            <Button type="submit" loading={save.isPending} disabled={!canSubmit}>
              {phrase ? 'Salvar alterações' : 'Cadastrar'}
            </Button>
          </div>
        </div>
      </form>
    </div>,
    document.body,
  )
}
