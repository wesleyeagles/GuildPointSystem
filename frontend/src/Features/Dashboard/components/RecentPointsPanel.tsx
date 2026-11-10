import { useLayoutEffect, useRef, useState } from 'react'
import { useRecentPoints, type RecentPointsEntry } from '@/Domain/Log/hooks/useLogs'
import { Panel } from '@/Shared/ui/components/Panel/Panel'
import { formatPointsTime, splitPages } from '@/Features/Dashboard/utils/recentPoints'
import { Pagination } from '@/Features/Dashboard/components/Pagination'

/** O backend devolve no máximo 50 rodadas; o painel pagina o que não cabe na altura. */
const RECENT_LIMIT = 50
const ROW_GAP = 4

const KIND_LABEL: Record<string, string> = {
  EVENT: 'Evento',
  RG: 'RG',
  // A rodada "CW" é o bônus de quem está em Crag 10 min antes da CW: um RG. A CW em si vem como Evento.
  CW: 'RG',
  PROXIMITY: 'Proximidade',
}

const fmt = (n: number) => Number(n).toLocaleString('pt-BR')

/** Cor da borda: verde = recebeu, vermelho = barrado pelo limite, cinza = não participou. */
function statusOf(entry: RecentPointsEntry) {
  return entry.received ? 'got' : entry.limited ? 'limit' : 'none'
}

function Row({ entry }: { entry: RecentPointsEntry }) {
  const title = entry.kind === 'RG' && entry.detail ? entry.detail : entry.name
  const status = statusOf(entry)
  return (
    <li className={`recent-points__row recent-points__row--${entry.kind.toLowerCase()} recent-points__row--${status}`}>
      <span className="recent-points__kind">{KIND_LABEL[entry.kind] ?? entry.kind}</span>
      <span className="recent-points__name" title={title}>{title}</span>
      <span className="recent-points__points">+{fmt(entry.points)}</span>
      <span className="recent-points__meta">
        {entry.members} {entry.members === 1 ? 'membro' : 'membros'} · {formatPointsTime(entry.at)}
      </span>
      {entry.limited && !entry.received ? (
        <span className="recent-points__mine recent-points__mine--limit">
          {entry.kind === 'RG' ? 'Você não recebeu: limite de RG da CW (2x dia)' : 'Você não recebeu: limite diário atingido'}
        </span>
      ) : null}
    </li>
  )
}

/**
 * Mede a altura de cada linha numa lista invisível do mesmo tamanho e reparte em páginas que
 * cabem na área visível (linhas com "Você não recebeu" são mais altas). Sem medida (testes): uma página.
 */
function usePagedRows(count: number) {
  const viewportRef = useRef<HTMLDivElement>(null)
  const measureRef = useRef<HTMLUListElement>(null)
  const [layout, setLayout] = useState<{ available: number; heights: number[] }>({ available: 0, heights: [] })

  useLayoutEffect(() => {
    const viewport = viewportRef.current
    const measureList = measureRef.current
    if (!viewport || !measureList) return
    const measure = () => {
      const available = viewport.clientHeight
      const heights = Array.from(measureList.children, (child) => (child as HTMLElement).offsetHeight)
      setLayout((prev) =>
        prev.available === available && prev.heights.join() === heights.join() ? prev : { available, heights },
      )
    }
    measure()
    if (typeof ResizeObserver === 'undefined') return
    const observer = new ResizeObserver(measure)
    observer.observe(viewport)
    observer.observe(measureList)
    return () => observer.disconnect()
  }, [count])

  const pages = splitPages(layout.heights.length === count ? layout.heights : [], layout.available, ROW_GAP, count)
  return { viewportRef, measureRef, pages }
}

/** Últimas coisas que deram pontos para a guild (eventos, RG, CW), sem nome de jogador; marca o que o membro logado recebeu ou perdeu pelo limite. */
export function RecentPointsPanel({ className }: { className?: string }) {
  const { data, isPending, isError } = useRecentPoints(RECENT_LIMIT)
  const entries = data ?? []
  const { viewportRef, measureRef, pages } = usePagedRows(entries.length)
  const [page, setPage] = useState(0)
  const current = Math.min(page, Math.max(0, pages.length - 1))
  const [from, to] = pages[current] ?? [0, entries.length]
  const rowKey = (entry: RecentPointsEntry, i: number) => `${entry.kind}-${entry.name}-${entry.detail}-${entry.at}-${i}`

  return (
    <Panel title="Últimos pontos" variant="amber" className={className}>
      {isPending ? (
        <p className="dashboard__empty">Carregando…</p>
      ) : isError ? (
        <p className="dashboard__empty">Não deu para carregar os últimos pontos.</p>
      ) : !entries.length ? (
        <p className="dashboard__empty">Nenhum ponto dado nos últimos 30 dias.</p>
      ) : (
        <>
          <div className="recent-points__viewport" ref={viewportRef}>
            <ul className="recent-points">
              {entries.slice(from, to).map((entry, i) => <Row key={rowKey(entry, from + i)} entry={entry} />)}
            </ul>
            {/* Cópia invisível só para medir a altura de cada linha. */}
            <ul className="recent-points recent-points--measure" ref={measureRef} aria-hidden="true">
              {entries.map((entry, i) => <Row key={rowKey(entry, i)} entry={entry} />)}
            </ul>
          </div>
          <Pagination page={current} total={pages.length} onChange={setPage} />
        </>
      )}
    </Panel>
  )
}
