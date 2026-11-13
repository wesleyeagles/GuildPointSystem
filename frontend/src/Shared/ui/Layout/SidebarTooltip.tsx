import { useCallback, useState, type FocusEvent, type MouseEvent, type ReactNode } from 'react'
import { createPortal } from 'react-dom'

type Anchor = { label: string; kind?: string; top: number; left: number }

/**
 * Tooltip dos ícones do trilho: lê `data-tooltip` do item sob o mouse (ou com foco);
 * `data-tooltip-kind` deixa quem chama acrescentar linhas (ex.: pontos no perfil).
 * Vai para um portal porque o trilho corta o que passa da borda (overflow).
 */
export function useSidebarTooltip(renderExtra?: (kind: string | undefined) => ReactNode) {
  const [anchor, setAnchor] = useState<Anchor | null>(null)

  const show = useCallback((target: EventTarget) => {
    if (!(target instanceof Element)) return
    // A bolinha de contagem tem o próprio popover: os dois juntos ficariam um por cima do outro.
    if (target.closest('.nav-badge')) return setAnchor(null)
    const item = target.closest<HTMLElement>('[data-tooltip]')
    if (!item) return setAnchor(null)
    const rect = item.getBoundingClientRect()
    setAnchor({ label: item.dataset.tooltip ?? '', kind: item.dataset.tooltipKind, top: rect.top + rect.height / 2, left: rect.right + 12 })
  }, [])

  const handlers = {
    onMouseOver: (event: MouseEvent) => show(event.target),
    onMouseLeave: () => setAnchor(null),
    onFocus: (event: FocusEvent) => show(event.target),
    onBlur: () => setAnchor(null),
    onClick: () => setAnchor(null),
    onScroll: () => setAnchor(null),
  }

  const tooltip = anchor
    ? createPortal(
        <div className="sidebar-tooltip" role="tooltip" style={{ top: anchor.top, left: anchor.left }}>
          <span className="sidebar-tooltip__label">{anchor.label}</span>
          {renderExtra?.(anchor.kind)}
        </div>,
        document.body,
      )
    : null

  return { handlers, tooltip }
}
