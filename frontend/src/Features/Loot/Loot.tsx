import { useDeferredValue, useState } from 'react'
import { Link } from 'react-router-dom'
import { LOOT_PAGE_SIZE, useLoot, type LootKind, type LootQuery } from '@/Domain/Loot/hooks/useLoot'
import { Button } from '@/Shared/ui/components/Button/Button'
import { SCHEDULE_TIME_ZONE } from '@/Shared/utils/scheduleTimeZone'
import { Panel } from '@/Shared/ui/components/Panel/Panel'
import './Loot.styles.scss'

const KIND_LABELS: Record<LootKind, string> = { DROP: 'Drop', PARTY: 'PT', REWARD: 'Recompensa' }
const KIND_TITLES: Record<LootKind, string> = {
  DROP: 'Anúncio do servidor ("has acquired")',
  PARTY: 'Loot da PT ("has obtained")',
  REWARD: 'Recompensa de dungeon/Citadel ("Compensation item")',
}
const DAYS = [1, 7, 30, 90]

const when = (iso: string) =>
  new Date(iso).toLocaleString('pt-BR', { timeZone: SCHEDULE_TIME_ZONE, dateStyle: 'short', timeStyle: 'medium' })

/**
 * Admin → Loot: o que os membros pegaram, lido pela DLL (1.49.0+) no chat do sistema do jogo. Só aparece o loot
 * que algum membro com a DLL viu no chat (drop anunciado pelo servidor, loot da PT e recompensas).
 */
export function LootPage() {
  const [nickname, setNickname] = useState('')
  const [item, setItem] = useState('')
  const [kind, setKind] = useState<LootKind | ''>('')
  const [days, setDays] = useState(7)
  const [page, setPage] = useState(0)
  const query: LootQuery = {
    nickname: useDeferredValue(nickname),
    item: useDeferredValue(item),
    kind,
    days,
    page,
  }
  const { data, isLoading, error } = useLoot(query)
  const rows = data?.items ?? []
  const total = data?.total ?? 0
  const pages = Math.max(1, Math.ceil(total / LOOT_PAGE_SIZE))
  const current = Math.min(page, pages - 1)
  // Filtro mudou: volta para a primeira página.
  const filter = <T,>(set: (v: T) => void) => (v: T) => { set(v); setPage(0) }

  return (
    <div className="loot-page">
      <Panel title="Loot dos membros" code={data ? `${total}` : '…'}>
        <div className="loot-page__form">
          <label>Membro<input value={nickname} onChange={(e) => filter(setNickname)(e.target.value)} placeholder="Todos" /></label>
          <label>Item<input value={item} onChange={(e) => filter(setItem)(e.target.value)} placeholder="Qualquer item" /></label>
          <label>
            Tipo
            <select value={kind} onChange={(e) => filter(setKind)(e.target.value as LootKind | '')}>
              <option value="">Todos</option>
              {(Object.keys(KIND_LABELS) as LootKind[]).map((k) => <option key={k} value={k}>{KIND_LABELS[k]}</option>)}
            </select>
          </label>
          <label>
            Período
            <select value={days} onChange={(e) => filter(setDays)(Number(e.target.value))}>
              {DAYS.map((d) => <option key={d} value={d}>{d === 1 ? 'Últimas 24 h' : `Últimos ${d} dias`}</option>)}
            </select>
          </label>
        </div>
        <p className="loot-page__muted">
          Lido pela DLL (1.49.0 ou mais nova) no chat do sistema do jogo: drops anunciados pelo servidor, loot da PT e
          recompensas de dungeon/Citadel. Só entra o que algum membro com a DLL viu. Horário de Brasília; guardado por 90 dias.
        </p>
        {error ? <p className="loot-page__warn">{(error as Error).message}</p>
          : isLoading ? <p className="loot-page__muted">Carregando…</p>
          : rows.length === 0 ? <p className="loot-page__muted">Nenhum loot nesse período.</p>
          : (
            <>
            <table className="loot-page__table">
              <thead>
                <tr><th>Data e hora</th><th>Membro</th><th>Item</th><th>Qtd.</th><th>Tipo</th></tr>
              </thead>
              <tbody>
                {rows.map((r) => (
                  <tr key={r.id}>
                    <td className="loot-page__when">{when(r.lootedAt)}</td>
                    <td><Link to={`/profile/${r.memberId}`}>{r.nickname}</Link></td>
                    <td>{r.item}</td>
                    <td>{r.quantity}</td>
                    <td>
                      <span className={`loot-page__kind loot-page__kind--${r.kind.toLowerCase()}`} title={KIND_TITLES[r.kind]}>
                        {KIND_LABELS[r.kind]}
                      </span>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
            {pages > 1 && (
              <nav className="loot-page__pager" aria-label="Páginas do loot">
                <Button size="sm" variant="ghost" type="button" disabled={current === 0} onClick={() => setPage(0)}>«</Button>
                <Button size="sm" variant="ghost" type="button" disabled={current === 0} onClick={() => setPage(current - 1)}>‹ Anterior</Button>
                <span className="loot-page__muted">
                  Página {current + 1} de {pages} · {total} loots
                </span>
                <Button size="sm" variant="ghost" type="button" disabled={current >= pages - 1} onClick={() => setPage(current + 1)}>Próxima ›</Button>
                <Button size="sm" variant="ghost" type="button" disabled={current >= pages - 1} onClick={() => setPage(pages - 1)}>»</Button>
              </nav>
            )}
            </>
          )}
      </Panel>
    </div>
  )
}
