import type { HTMLAttributes, ReactNode } from 'react'

export type PanelVariant = 'default' | 'amber' | 'danger'

export interface PanelProps extends Omit<HTMLAttributes<HTMLElement>, 'title'> {
  title?: ReactNode
  actions?: ReactNode
  variant?: PanelVariant
  /** Small technical tag shown in the title bar, e.g. "WND-04". */
  code?: string
  /** Removes inner padding so content (tables, lists) can reach the frame. */
  flush?: boolean
}
