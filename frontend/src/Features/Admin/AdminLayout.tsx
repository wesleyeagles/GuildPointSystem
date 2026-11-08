import { Link, Navigate, Outlet, useLocation } from 'react-router-dom'
import { useAuthContext } from '@/Features/Auth/contexts/AuthContext'
import { ApprovalsNavBadge } from '@/Features/Admin/Approvals/components/ApprovalsNavBadge/ApprovalsNavBadge'
import type { Role } from '@/Domain/types/models'
import './AdminLayout.styles.scss'

/** Abas da Administração (cada uma continua com o próprio endereço, /admin/...). */
const ADMIN_TABS: { to: string; label: string; minRole: Role }[] = [
  { to: '/admin/approvals', label: 'Aprovações', minRole: 'MODERADOR' },
  { to: '/admin/online', label: 'Online', minRole: 'ADMINISTRADOR' },
  { to: '/admin/dll', label: 'DLL', minRole: 'ADMINISTRADOR' },
  { to: '/admin/resources', label: 'Recursos', minRole: 'ADMINISTRADOR' },
  { to: '/admin/pb-history', label: 'Histórico de PB', minRole: 'ADMINISTRADOR' },
  { to: '/admin/cw', label: 'Presença em CW', minRole: 'ADMINISTRADOR' },
]

function useAdminTabs() {
  const { hasRole } = useAuthContext()
  return ADMIN_TABS.filter((t) => hasRole(t.minRole))
}

/** Menu "Administração": uma rota com abas; cada aba mostra a página que antes era um item do menu. */
export function AdminLayout() {
  const tabs = useAdminTabs()
  const { pathname } = useLocation()
  return (
    <div className="admin-layout">
      <nav className="admin-tabs" role="tablist" aria-label="Administração">
        {tabs.map((t) => {
          const active = pathname === t.to || pathname.startsWith(`${t.to}/`)
          return (
            <Link
              key={t.to}
              to={t.to}
              role="tab"
              aria-selected={active}
              className={`admin-tabs__btn${active ? ' admin-tabs__btn--active' : ''}`}
            >
              {t.label}
              {t.to === '/admin/approvals' && <ApprovalsNavBadge />}
            </Link>
          )
        })}
      </nav>
      <div className="admin-layout__body">
        <Outlet />
      </div>
    </div>
  )
}

/** /admin sozinho: abre a primeira aba que a pessoa pode ver. */
export function AdminIndex() {
  const tabs = useAdminTabs()
  return <Navigate to={tabs[0]?.to ?? '/'} replace />
}
