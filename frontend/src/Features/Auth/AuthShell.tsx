import { useEffect, useLayoutEffect, useRef, useState } from 'react'
import { Link, Outlet, useLocation } from 'react-router-dom'
import './Auth.styles.scss'

/**
 * Layout route that wraps /login and /register.
 * Stays mounted during navigation between the two so CSS transitions
 * and the ResizeObserver height animation play smoothly.
 */
export function AuthShell() {
  const { pathname } = useLocation()
  const isRegister = pathname.includes('register')

  // ── Animated height ────────────────────────────────────────────────────────
  // The outer "body" wrapper gets an explicit height that CSS-transitions.
  // The inner "content" div is what ResizeObserver measures.
  const contentRef = useRef<HTMLDivElement>(null)
  const [bodyHeight, setBodyHeight] = useState<number | null>(null)

  // Set initial height before the browser paints so there's zero flash.
  useLayoutEffect(() => {
    if (contentRef.current) {
      setBodyHeight(contentRef.current.offsetHeight)
    }
  }, [])

  // Re-measure whenever anything inside the form changes height.
  useEffect(() => {
    const el = contentRef.current
    if (!el) return
    const ro = new ResizeObserver(() => {
      setBodyHeight(el.offsetHeight)
    })
    ro.observe(el)
    return () => ro.disconnect()
  }, [])

  return (
    <div className="auth-stage">
      <div className="auth-frame-corner fc-tl" />
      <div className="auth-frame-corner fc-br" />

      {/* ── Left lore panel ─────────────────────────────────────────────── */}
      <div className="auth-lore">
        <div className="auth-lore-logo">
          <img src="/Logo-Blacklist.png" alt="Blacklist" className="auth-logo" />
        </div>
        <div className="auth-lore-content">
          <p className="lore-eyebrow">RISING FORCE · GUILD NETWORK V2</p>
          <h1 className="lore-title">
            Acesse o<br />
            <span>terminal da guild</span>
          </h1>
          <p className="lore-desc">
            Reivindique seu posto na frente de batalha. Pontos, leilões e eventos
            sincronizados em tempo real entre os membros.
          </p>
          <div className="faction-strip">
            <div className="faction-chip">
              <div className="chip-name">ACCRETIA</div>
              <div className="chip-role">Império mecânico</div>
            </div>
            <div className="faction-chip">
              <div className="chip-name">BELLATO</div>
              <div className="chip-role">União industrial</div>
            </div>
            <div className="faction-chip">
              <div className="chip-name">CORA</div>
              <div className="chip-role">Ordem mística</div>
            </div>
          </div>
        </div>
      </div>

      {/* ── Right auth panel (width animates via CSS transition + class) ── */}
      <div className={`auth-panel${isRegister ? ' auth-panel--wide' : ''}`}>
        <div className="auth-card">
          {/*
           * This div's height is driven by JS (ResizeObserver) so CSS can
           * transition it. The inner div is what gets measured.
           */}
          <div
            className="auth-card-body"
            style={{
              height: bodyHeight !== null ? `${bodyHeight}px` : 'auto',
              overflow: 'hidden',
              transition: bodyHeight !== null
                ? 'height 0.42s cubic-bezier(0.4, 0, 0.2, 1)'
                : 'none',
            }}
          >
            <div ref={contentRef}>
              <div className="auth-tabs">
                <Link
                  to="/login"
                  className={`auth-tab${!isRegister ? ' active' : ''}`}
                >
                  Entrar
                </Link>
                <Link
                  to="/register"
                  className={`auth-tab${isRegister ? ' active' : ''}`}
                >
                  Cadastro
                </Link>
              </div>

              {/* Actual page content (LoginPage or RegisterPage) */}
              <Outlet />
            </div>
          </div>
        </div>
      </div>
    </div>
  )
}
