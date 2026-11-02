import { useEffect, useState } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { useMemberRanking } from '@/Domain/Member/hooks/useMembers'
import { nextFreeGroup, raidGroups, raidKeys, RAID_GROUP_SIZE, useAddRaidMembers, type Raid } from '@/Domain/Raid/hooks/useRaids'
import { Button } from '@/Shared/ui/components/Button/Button'
import { MemberClassIcon } from '@/Shared/ui/components/MemberClassIcon/MemberClassIcon'
import { useAppToast } from '@/Shared/ui/components/AppToast/AppToast'
import '@/Features/Auth/Auth.styles.scss'

/**
 * Escolhe vários membros de uma vez, sem limite. Fica de fora quem já está em alguma raid.
 * "Automático" põe cada um no primeiro subgrupo com vaga.
 */
export function RaidAddMembersModal({ raid, onClose }: { raid: Raid; onClose: () => void }) {
  const { showToast } = useAppToast()
  const queryClient = useQueryClient()
  const { data: members = [], isLoading } = useMemberRanking()
  const add = useAddRaidMembers()
  const [search, setSearch] = useState('')
  const [selected, setSelected] = useState<Set<number>>(new Set())
  const [group, setGroup] = useState<number | 'auto'>('auto')

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => { if (e.key === 'Escape') onClose() }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [onClose])

  const busy = new Set<number>()
  for (const r of queryClient.getQueryData<Raid[]>(raidKeys.all) ?? [raid]) r.members.forEach((m) => busy.add(m.id))
  const term = search.trim().toLocaleLowerCase('pt-BR')
  const available = members
    .filter((m) => !busy.has(m.id))
    .filter((m) => !term || m.nickname.toLocaleLowerCase('pt-BR').includes(term))
    .sort((a, b) => a.nickname.localeCompare(b.nickname, 'pt-BR'))

  const groups = raidGroups(raid)
  const options = [...groups.map((g) => ({ group: g.group, free: RAID_GROUP_SIZE - g.members.length })),
    { group: nextFreeGroup(raid), free: RAID_GROUP_SIZE }]
  const chosenFree = group === 'auto' ? Infinity : options.find((o) => o.group === group)?.free ?? 0

  const toggle = (id: number) => setSelected((prev) => {
    const next = new Set(prev)
    if (next.has(id)) next.delete(id)
    else next.add(id)
    return next
  })

  const submit = () => add.mutate(
    { raidId: raid.id, memberIds: [...selected], group: group === 'auto' ? undefined : group },
    {
      onSuccess: () => { showToast(`${selected.size} membro(s) adicionado(s) à raid.`, 'success'); onClose() },
      onError: (e) => showToast(e instanceof Error ? e.message : 'Não foi possível adicionar.', 'error'),
    },
  )

  return (
    <div className="modal-overlay" role="presentation" onClick={onClose}>
      <div className="modal-card raid-add" role="dialog" aria-modal="true" onClick={(e) => e.stopPropagation()}>
        <div className="auth-window-bar">
          <span>Adicionar à raid {raid.name}</span>
          <span className="auth-window-bar__code">{selected.size} SEL</span>
        </div>
        <div className="auth-card-content raid-add__body">
          <div className="raid-add__filters">
            <input
              type="search"
              className="raid-add__search"
              placeholder="Buscar membro"
              aria-label="Buscar membro para adicionar"
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              autoFocus
            />
            <select
              className="raid-add__group"
              aria-label="Subgrupo"
              value={group}
              onChange={(e) => setGroup(e.target.value === 'auto' ? 'auto' : Number(e.target.value))}
            >
              <option value="auto">Subgrupo automático</option>
              {options.map((o) => (
                <option key={o.group} value={o.group} disabled={o.free <= 0}>
                  Subgrupo {o.group} ({o.free} vaga{o.free === 1 ? '' : 's'})
                </option>
              ))}
            </select>
          </div>
          {isLoading ? (
            <p className="raid-add__empty">Carregando membros...</p>
          ) : available.length === 0 ? (
            <p className="raid-add__empty">{term ? 'Nenhum membro encontrado.' : 'Todos os membros já estão em uma raid.'}</p>
          ) : (
            <ul className="raid-add__list">
              {available.map((m) => (
                <li key={m.id}>
                  <label className="raid-add__item">
                    <input type="checkbox" checked={selected.has(m.id)} onChange={() => toggle(m.id)} />
                    {m.classImageUrl && m.className ? (
                      <MemberClassIcon classImageUrl={m.classImageUrl} classLabel={m.className} size="sm" />
                    ) : (
                      <span className="raid-member__fallback">?</span>
                    )}
                    <span className="raid-add__name" title={m.nickname}>{m.nickname}</span>
                    <span className="raid-add__level">L{m.level}</span>
                  </label>
                </li>
              ))}
            </ul>
          )}
          {selected.size > chosenFree && (
            <p className="raid-add__warn">O subgrupo escolhido só tem {chosenFree} vaga(s).</p>
          )}
          <div className="raid-add__actions">
            <Button variant="secondary" onClick={onClose}>Fechar</Button>
            <Button loading={add.isPending} disabled={selected.size === 0 || selected.size > chosenFree} onClick={submit}>
              Adicionar {selected.size > 0 ? selected.size : ''}
            </Button>
          </div>
        </div>
      </div>
    </div>
  )
}
