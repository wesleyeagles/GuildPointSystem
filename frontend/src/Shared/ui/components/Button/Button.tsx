import type { ButtonProps } from './Button.types'
import './Button.styles.scss'

export function Button({
  variant = 'primary',
  size = 'md',
  type = 'button',
  loading = false,
  disabled,
  className = '',
  children,
  ...rest
}: ButtonProps) {
  return (
    <button
      type={type}
      className={`btn btn--${variant} btn--${size} ${className}`.trim()}
      disabled={disabled || loading}
      {...rest}
    >
      {loading ? '...' : children}
    </button>
  )
}
