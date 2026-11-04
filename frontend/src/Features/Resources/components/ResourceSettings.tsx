import { useState } from 'react'
import { useMemberRanking } from '@/Domain/Member/hooks/useMembers'
import {
  TIER_LABEL,
  useSetResourceTier,
  useUpdateResourceAccess,
  type ResourceOverview,
} from '@/Domain/Resource/hooks/useResources'
import { mapLabel } from '@/Domain/Location/gameMaps'
import { Button } from '@/Shared/ui/components/Button/Button'
import { Panel } from '@/Shared/ui/components/Panel/Panel'
import { useAppToast } from '@/Shared/ui/components/AppToast/AppToast'

/** Administrador+: grau de cada mob e quem vê o mapa de recursos (no site e no jogo). */
export function ResourceSettings({ overview }: { overview: ResourceOverview }) {
  return (
    <>
      <ResourceMobs overview={overview} />
      {/* A chave recria o formulário com a lista salva sempre que ela muda no servidor. */}
      <ResourceAccess key={overview.trackers.map((t) => t.id).join(',')} overview={overview} />
    </>
  )
}

function ResourceMobs({ overview }: { overview: ResourceOverview }) {
  const { showToast } = useAppToast()
  const setTier = useSetResourceTier()
  const unknown = overview.mobs.filter((m) => m.tier == null).length
  const save = (name: string, tier: number) => setTier.mutate({ name, tier }, {
    onSuccess: () => showToast(`${name}: ${tier ? TIER_LABEL[tier] : 'não é recurso'}.`, 'success'),
    onError: (e) => showToast(e instanceof Error ? e.message : 'Não foi possível salvar.', 'error'),
  })
  return (
    <Panel title="Mobs de recurso" code={unknown ? `${unknown} desconhecido${unknown > 1 ? 's' : ''}` : undefined}>
      <p className="resources__muted">
        Nesses mapas a DLL envia todo mob sem nível no nome. Nome novo aparece aqui como desconhecido: escolha o grau,
        ou "Não é recurso" para esconder e parar de gravar.
      </p>
      <table className="resources__mobs">
        <thead>
          <tr><th>Mob</th><th>Mapa</th><th>Pontos</th><th>Grau</th></tr>
        </thead>
        <tbody>
          {overview.mobs.map((m) => (
            <tr key={m.name} className={m.tier == null ? 'resources__mob--unknown' : m.tier === 0 ? 'resources__mob--ignored' : ''}>
              <td>{m.name}</td>
              <td>{mapLabel(overview.maps.find((x) => x.code === m.map)?.name ?? String(m.map))}</td>
              <td>{m.spawns}</td>
              <td>
                <select
                  value={m.tier == null ? '' : String(m.tier)}
                  disabled={setTier.isPending}
                  aria-label={`Grau de ${m.name}`}
                  onChange={(e) => e.target.value !== '' && save(m.name, Number(e.target.value))}
                >
                  {m.tier == null && <option value="">Desconhecido</option>}
                  <option value="1">{TIER_LABEL[1]}</option>
                  <option value="2">{TIER_LABEL[2]}</option>
                  <option value="3">{TIER_LABEL[3]}</option>
                  <option value="0">Não é recurso</option>
                </select>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </Panel>
  )
}

function ResourceAccess({ overview }: { overview: ResourceOverview }) {
  const { showToast } = useAppToast()
  const { data: members = [] } = useMemberRanking()
  const update = useUpdateResourceAccess()
  const [trackers, setTrackers] = useState(overview.trackers)
  const [search, setSearch] = useState('')

  const dirty = trackers.map((t) => t.id).join(',') !== overview.trackers.map((t) => t.id).join(',')
  const term = search.trim().toLocaleLowerCase('pt-BR')
  const suggestions = term
    ? members.filter((m) => m.nickname.toLocaleLowerCase('pt-BR').includes(term) && !trackers.some((t) => t.id === m.id)).slice(0, 8)
    : []

  const save = () => update.mutate({ memberIds: trackers.map((t) => t.id) }, {
    onSuccess: () => showToast('Lista do mapa de recursos atualizada.', 'success'),
    onError: (e) => showToast(e instanceof Error ? e.message : 'Não foi possível salvar.', 'error'),
  })

  return (
    <Panel title="Quem vê o mapa de recursos" code={`${overview.trackers.length}`}>
      <p className="resources__muted">
        Todos os membros com a DLL enviam os recursos que veem. Só os membros desta lista (e Administradores) veem os
        pontos aqui no site, e só os desta lista veem as bolinhas no mapa do jogo.
      </p>
      <div className="resources__chips">
        {trackers.length === 0 && <em>Ninguém</em>}
        {trackers.map((t) => (
          <span key={t.id} className="resources__chip">
            {t.nickname}
            <button type="button" title={`Tirar ${t.nickname}`} onClick={() => setTrackers(trackers.filter((x) => x.id !== t.id))}>✕</button>
          </span>
        ))}
      </div>
      <input
        className="resources__search"
        type="search"
        placeholder="Adicionar membro pelo nick"
        aria-label="Adicionar membro ao mapa de recursos"
        value={search}
        onChange={(e) => setSearch(e.target.value)}
      />
      {suggestions.length > 0 && (
        <ul className="resources__suggestions">
          {suggestions.map((m) => (
            <li key={m.id}>
              <button type="button" onClick={() => { setTrackers([...trackers, { id: m.id, nickname: m.nickname }]); setSearch('') }}>
                {m.nickname}
              </button>
            </li>
          ))}
        </ul>
      )}
      <div className="resources__actions">
        <Button size="sm" loading={update.isPending} disabled={!dirty} onClick={save}>Salvar lista</Button>
      </div>
    </Panel>
  )
}
