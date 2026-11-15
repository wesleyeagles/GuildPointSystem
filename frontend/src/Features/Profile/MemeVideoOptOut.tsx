import { useMemeVideoOptOut, useSetMemeVideoOptOut } from '@/Domain/MemeVideo/hooks/useMemeVideo'
import { useAppToast } from '@/Shared/ui/components/AppToast/AppToast'

/** Opção do próprio membro: não receber os vídeos que o Admin/Líder manda para o jogo. */
export function MemeVideoOptOut() {
  const { data } = useMemeVideoOptOut()
  const save = useSetMemeVideoOptOut()
  const { showToast } = useAppToast()
  if (!data) return null
  return (
    <div className="profile-form">
      <h3>Vídeo no jogo</h3>
      <label className="profile-form__check">
        <input
          type="checkbox"
          checked={!data.optOut}
          disabled={save.isPending}
          onChange={(e) => save.mutate(!e.target.checked, {
            onError: (err) => showToast(err instanceof Error ? err.message : 'Não foi possível salvar.', 'error'),
          })}
        />
        Aceitar vídeos do Admin/Líder no meio do jogo (pela DLL)
      </label>
    </div>
  )
}
