import { useState } from 'react'
import { useMemberRanking } from '@/Domain/Member/hooks/useMembers'
import { useUpdateRaidAccess, type RaidAccess } from '@/Domain/Raid/hooks/useRaids'
import type { Role } from '@/Domain/types/models'
import { Button } from '@/Shared/ui/components/Button/Button'
import { Panel } from '@/Shared/ui/components/Panel/Panel'
import { useAppToast } from '@/Shared/ui/components/AppToast/AppToast'

const ROLES: { role: Role; label: string }[] = [
  { role: 'MEMBRO', label: 'Todos os membros' },
  { role: 'MODERADOR', label: 'Moderador ou acima' },
  { role: 'ADMINISTRADOR', label: 'Administrador ou acima' },
  { role: 'LIDER', label: 'Só o Líder' },
]

/**
 * Administrador+: quem vê a aba Raid no site. Cargo mínimo e membros liberados pelo nome; Administrador e
 * Líder sempre veem. A DLL de todos continua enviando, e o painel no jogo não muda.
 */
export function RaidAccessSettings({ access }: { access: RaidAccess }) {
  const { showToast } = useAppToast()
  const { data: members = [] } = useMemberRanking()
  const update = useUpdateRaidAccess()
  const [minRole, setMinRole] = useState<Role>(access.minRole ?? 'MEMBRO')
  const [viewers, setViewers] = useState(access.viewers)
  const [search, setSearch] = useState('')
  const dirty = minRole !== access.minRole ||
    viewers.map((v) => v.id).join(',') !== access.viewers.map((v) => v.id).join(',')

  const term = search.trim().toLocaleLowerCase('pt-BR')
  const suggestions = term
    ? members.filter((m) => m.nickname.toLocaleLowerCase('pt-BR').includes(term) && !viewers.some((v) => v.id === m.id)).slice(0, 8)
    : []

  const save = () => update.mutate({ minRole, viewerIds: viewers.map((v) => v.id) }, {
    onSuccess: () => showToast('Acesso à Raid atualizado.', 'success'),
    onError: (e) => showToast(e instanceof Error ? e.message : 'Não foi possível salvar.', 'error'),
  })

  return (
    <Panel title="Quem vê a Raid" code="ACESSO" className="raid-access">
      <p className="raid-access__hint">
        Só muda o site: a DLL de todos continua enviando HP e buffs, e o painel no jogo aparece para quem está na raid.
        Administradores e o Líder sempre veem.
      </p>
      <label className="raid-access__field">
        <span>Cargo mínimo</span>
        <select value={minRole} onChange={(e) => setMinRole(e.target.value as Role)}>
          {ROLES.map((r) => <option key={r.role} value={r.role}>{r.label}</option>)}
        </select>
      </label>
      <div className="raid-access__field">
        <span>Liberados mesmo abaixo do cargo</span>
        <div className="raid-access__chips">
          {viewers.length === 0 && <em>Ninguém</em>}
          {viewers.map((v) => (
            <span key={v.id} className="raid-access__chip">
              {v.nickname}
              <button type="button" title={`Tirar ${v.nickname}`} onClick={() => setViewers(viewers.filter((x) => x.id !== v.id))}>✕</button>
            </span>
          ))}
        </div>
        <input
          type="search"
          placeholder="Adicionar membro pelo nick"
          aria-label="Adicionar membro liberado"
          value={search}
          onChange={(e) => setSearch(e.target.value)}
        />
        {suggestions.length > 0 && (
          <ul className="raid-access__suggestions">
            {suggestions.map((m) => (
              <li key={m.id}>
                <button type="button" onClick={() => { setViewers([...viewers, { id: m.id, nickname: m.nickname }]); setSearch('') }}>
                  {m.nickname}
                </button>
              </li>
            ))}
          </ul>
        )}
      </div>
      <div className="raid-access__actions">
        <Button size="sm" loading={update.isPending} disabled={!dirty} onClick={save}>Salvar acesso</Button>
      </div>
    </Panel>
  )
}
