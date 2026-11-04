import { useEffect, useState } from 'react'
import { createPortal } from 'react-dom'
import { useQueryClient } from '@tanstack/react-query'
import { useMemberRanking } from '@/Domain/Member/hooks/useMembers'
import { useCharacterRanking, useCharacterStatusSubscription } from '@/Domain/CharacterStatus/hooks/useCharacterStatus'
import { dllAtLeast } from '@/Domain/Dll/hooks/useDll'
import { nextFreeGroup, raidGroups, raidKeys, useAddRaidMembers, useNowTick, type Raid } from '@/Domain/Raid/hooks/useRaids'
import { LOCATION_FRESH_MS } from '@/Domain/Location/gameMaps'
import { serverNow } from '@/Shared/utils/serverClock'
import { Button } from '@/Shared/ui/components/Button/Button'
import { MemberClassIcon } from '@/Shared/ui/components/MemberClassIcon/MemberClassIcon'
import { useAppToast } from '@/Shared/ui/components/AppToast/AppToast'
import '@/Features/Auth/Auth.styles.scss'

/**
 * Escolhe vários membros de uma vez, sem limite. Só aparece quem está no jogo agora (a DLL reenvia o local a
 * cada 60 s com o jogo aberto; vale o mesmo prazo do local no ranking) e envia com a DLL 1.7.0 ou mais nova
 * (a primeira com o painel de raid completo: buffs, marcas, oculto). Fica de fora quem já está em alguma raid.
 * "Automático" põe cada um no primeiro subgrupo com vaga. Vai para o body (portal): dentro do
 * painel da raid, que corta o conteúdo, a sobreposição ficaria presa ao painel.
 */
const RAID_MIN_DLL = [1, 7, 0] as const

export function RaidAddMembersModal({ raid, onClose }: { raid: Raid; onClose: () => void }) {
  const { showToast } = useAppToast()
  const queryClient = useQueryClient()
  const { data: members = [], isLoading } = useMemberRanking()
  const { data: statuses = [] } = useCharacterRanking()
  useCharacterStatusSubscription()  // status novo da DLL atualiza quem está no jogo
  const add = useAddRaidMembers()
  useNowTick(15_000)  // quem fecha ou abre o jogo entra/sai da lista sem reabrir
  const now = serverNow()
  const [search, setSearch] = useState('')
  const [selected, setSelected] = useState<Set<number>>(new Set())
  const [group, setGroup] = useState<number | 'auto'>('auto')
  // Padrão: entram ocultos (não veem a raid no jogo nem no site e não recebem convites).
  const [visible, setVisible] = useState(false)

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => { if (e.key === 'Escape') onClose() }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [onClose])

  const busy = new Set<number>()
  for (const r of queryClient.getQueryData<Raid[]>(raidKeys.all) ?? [raid]) r.members.forEach((m) => busy.add(m.id))
  // Level e versão da DLL do último status enviado, como na Party; sem status, o level do cadastro.
  const gameLevel = new Map<number, { level: number; at: string; dll: string | null; locationAt: string | null }>()
  for (const s of statuses) {
    const current = gameLevel.get(s.memberId)
    if (!current || s.updatedAt > current.at)
      gameLevel.set(s.memberId, { level: s.level, at: s.updatedAt, dll: s.dllVersion, locationAt: s.locationAt })
  }
  const inGame = (id: number) => {
    const at = gameLevel.get(id)?.locationAt
    return !!at && now - Date.parse(at) <= LOCATION_FRESH_MS
  }
  const free = members.filter((m) => !busy.has(m.id) && inGame(m.id))
  const eligible = free.filter((m) => dllAtLeast(gameLevel.get(m.id)?.dll, RAID_MIN_DLL))
  const oldDll = free.length - eligible.length
  const term = search.trim().toLocaleLowerCase('pt-BR')
  const available = eligible
    .filter((m) => !term || m.nickname.toLocaleLowerCase('pt-BR').includes(term))
    .sort((a, b) => a.nickname.localeCompare(b.nickname, 'pt-BR'))

  const groups = raidGroups(raid)
  // Subgrupos sem limite: qualquer um aceita mais gente.
  const options = [...groups.map((g) => ({ group: g.group, count: g.members.length })), { group: nextFreeGroup(raid), count: 0 }]

  const toggle = (id: number) => setSelected((prev) => {
    const next = new Set(prev)
    if (next.has(id)) next.delete(id)
    else next.add(id)
    return next
  })

  const submit = () => add.mutate(
    { raidId: raid.id, memberIds: [...selected], group: group === 'auto' ? undefined : group, hidden: !visible },
    {
      onSuccess: () => { showToast(`${selected.size} membro(s) adicionado(s) à raid.`, 'success'); onClose() },
      onError: (e) => showToast(e instanceof Error ? e.message : 'Não foi possível adicionar.', 'error'),
    },
  )

  return createPortal(
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
                <option key={o.group} value={o.group}>
                  Subgrupo {o.group} ({o.count === 0 ? 'novo' : `${o.count} integrante${o.count === 1 ? '' : 's'}`})
                </option>
              ))}
            </select>
          </div>
          {isLoading ? (
            <p className="raid-add__empty">Carregando membros...</p>
          ) : available.length === 0 ? (
            <p className="raid-add__empty">
              {term ? 'Nenhum membro encontrado.' : oldDll ? 'Ninguém no jogo, fora de raid, com a DLL 1.7.0 ou mais nova.' : 'Ninguém no jogo agora fora de uma raid.'}
            </p>
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
                    <span className="raid-add__level">L{gameLevel.get(m.id)?.level ?? m.level}</span>
                  </label>
                </li>
              ))}
            </ul>
          )}
          {oldDll > 0 && (
            <p className="raid-add__hint">{oldDll} membro(s) sem a DLL 1.7.0 ou mais nova não aparecem na lista.</p>
          )}
          <label className="raid-add__visible">
            <input type="checkbox" checked={visible} onChange={(e) => setVisible(e.target.checked)} />
            Mostrar a raid para eles (veem o painel no jogo e recebem os convites)
          </label>
          {!visible && <p className="raid-add__hint">Entram ocultos: a liderança acompanha HP e buffs, e eles não veem a raid.</p>}
          <div className="raid-add__actions">
            <Button variant="secondary" onClick={onClose}>Fechar</Button>
            <Button loading={add.isPending} disabled={selected.size === 0} onClick={submit}>
              Adicionar {selected.size > 0 ? selected.size : ''}
            </Button>
          </div>
        </div>
      </div>
    </div>,
    document.body,
  )
}
