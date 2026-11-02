import { useCallRaidInGame, useRaidGameInvites, type RaidGroupInvite } from '@/Domain/Raid/hooks/useRaids'
import { useAppToast } from '@/Shared/ui/components/AppToast/AppToast'
import { Button } from '@/Shared/ui/components/Button/Button'

const STATUS: Record<RaidGroupInvite['state'], string> = {
  WAITING: 'aguardando o jogo do líder (online, com a DLL)',
  DELIVERED: 'enviando convites no jogo…',
  COMPLETE: 'convites enviados',
  EXPIRED: 'o jogo do líder não respondeu a tempo',
}

/** Um chamado por subgrupo: a DLL do líder de cada subgrupo digita "@party Nick" para os outros. */
export function RaidGameInvite({ raidId, canManage }: { raidId: number; canManage: boolean }) {
  const { showToast } = useAppToast()
  const invites = useRaidGameInvites(raidId, canManage)
  const call = useCallRaidInGame(raidId)
  if (!canManage) return null
  const current = invites.data ?? []
  const busy = current.some((i) => i.state === 'WAITING' || i.state === 'DELIVERED')
  const onCall = () => call.mutate(undefined, {
    onError: (error) => showToast(error instanceof Error ? error.message : 'Não foi possível chamar a raid no jogo.', 'error'),
  })
  return (
    <div className="raid-invite">
      <Button size="sm" variant="secondary" loading={call.isPending} disabled={busy} onClick={onCall}>
        {busy ? 'Chamado no jogo em andamento' : 'Chamar todos no jogo'}
      </Button>
      {current.length > 0 && (
        <ul className="raid-invite__list">
          {current.map((i) => (
            <li key={i.group} className={`raid-invite__item raid-invite__item--${i.state.toLowerCase()}`}>
              Subgrupo {i.group} · {i.leader}: {STATUS[i.state]}
              {i.state !== 'EXPIRED' && ` → ${i.nicks.join(', ')}`}
              {i.skipped.length > 0 && ` · Fora do chamado: ${i.skipped.join(', ')}`}
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}
