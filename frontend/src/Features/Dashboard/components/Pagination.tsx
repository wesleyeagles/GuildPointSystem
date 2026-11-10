/** Paginador ‹ 1 / 3 › do dashboard (ranking e últimos pontos). Some com uma página só. */
export interface PaginationProps {
  page: number
  total: number
  onChange: (page: number) => void
  ref?: React.Ref<HTMLDivElement>
}

export function Pagination({ page, total, onChange, ref }: PaginationProps) {
  if (total <= 1) return null
  return (
    <div className="dash-pagination" ref={ref}>
      <button
        className="dash-pagination__btn"
        onClick={() => onChange(Math.max(0, page - 1))}
        disabled={page === 0}
      >
        ‹
      </button>
      <span className="dash-pagination__info">
        {page + 1} <span className="dash-pagination__sep">/</span> {total}
      </span>
      <button
        className="dash-pagination__btn"
        onClick={() => onChange(Math.min(total - 1, page + 1))}
        disabled={page === total - 1}
      >
        ›
      </button>
    </div>
  )
}

