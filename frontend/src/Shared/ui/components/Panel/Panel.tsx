import type { PanelProps } from './Panel.types'
import './Panel.styles.scss'

export function Panel({
  title,
  actions,
  variant = 'default',
  code,
  flush = false,
  className = '',
  children,
  ...rest
}: PanelProps) {
  return (
    <section
      className={`panel panel--${variant}${flush ? ' panel--flush' : ''} ${className}`.trim()}
      {...rest}
    >
      {(title || actions || code) && (
        <header className="panel__titlebar">
          {title && <h2 className="panel__title">{title}</h2>}
          {code && <span className="panel__code">{code}</span>}
          {actions && <div className="panel__actions">{actions}</div>}
        </header>
      )}
      <div className="panel__body">{children}</div>
    </section>
  )
}
