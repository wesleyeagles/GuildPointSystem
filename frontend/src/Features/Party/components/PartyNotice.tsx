import { useState } from 'react'
import { useSetPartyNotice } from '@/Domain/Party/hooks/useParty'
import { useAppToast } from '@/Shared/ui/components/AppToast/AppToast'
import { Button } from '@/Shared/ui/components/Button/Button'
import './PartyNotice.styles.scss'

export function PartyNotice({ text = '', canEdit }: { text?: string; canEdit: boolean }) {
  const save = useSetPartyNotice()
  const { showToast } = useAppToast()
  const [editing, setEditing] = useState(false)
  const [draft, setDraft] = useState('')
  if (!text && !canEdit) return null

  const publish = (value: string) => save.mutate(value, {
    onSuccess: () => setEditing(false),
    onError: error => showToast(error.message, 'error'),
  })

  return <section className="party-notice" aria-label="Aviso fixado">
    <div className="party-notice__header">
      <strong>Aviso fixado</strong>
      {canEdit && !editing && <Button size="sm" variant="secondary" onClick={() => {
        setDraft(text)
        setEditing(true)
      }}>{text ? 'Editar aviso' : 'Adicionar aviso'}</Button>}
    </div>
    {text && <p className="party-notice__text" data-no-i18n>{text}</p>}
    {editing && <form className="party-notice__editor" onSubmit={event => {
      event.preventDefault()
      publish(draft)
    }}>
      <label htmlFor="party-notice-text">Mensagem para todos na party</label>
      <textarea id="party-notice-text" value={draft} onChange={event => setDraft(event.target.value)}
        maxLength={1000} rows={3} disabled={save.isPending} autoFocus />
      <div className="party-notice__actions">
        <Button type="submit" size="sm" loading={save.isPending}>Salvar aviso</Button>
        <Button type="button" size="sm" variant="secondary" disabled={save.isPending}
          onClick={() => setEditing(false)}>Cancelar</Button>
        {text && <Button type="button" size="sm" variant="danger" disabled={save.isPending}
          onClick={() => publish('')}>Remover aviso</Button>}
      </div>
    </form>}
  </section>
}
