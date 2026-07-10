import { Link, Outlet, useLocation } from 'react-router-dom'
import { useAuthContext } from '@/Features/Auth/contexts/AuthContext'
import { useCurrentMember } from '@/Domain/Member/hooks/useMembers'
import { usePointsSubscription } from '@/Domain/Auction/hooks/useAuction'
import { EventToastProvider } from '@/Features/Events/components/EventToast/EventToast'
import { AppToastProvider } from '@/Shared/ui/components/AppToast/AppToast'
import './Layout.styles.scss'

const NAV_ITEMS = [
  { to: '/', label: 'Dashboard' },
  { to: '/logs', label: 'Logs' },
  { to: '/objectives', label: 'Objetivos' },
  { to: '/events', label: 'Eventos' },
  { to: '/items', label: 'Itens' },
  { to: '/auctions', label: 'Leilões' },
  { to: '/profile', label: 'Perfil' },
]

export function Layout() {
  const { logout, hasRole, user } = useAuthContext()
  const { data: member } = useCurrentMember()
  const location = useLocation()

  usePointsSubscription(user?.memberId)

  return (
    <AppToastProvider>
    <EventToastProvider>
      <div className="layout">
        <header className="layout__header">
          <h1 className="layout__title">Guild System</h1>
          <nav className="layout__nav">
            {NAV_ITEMS.map((item) => (
              <Link
                key={item.to}
                to={item.to}
                className={`layout__nav-link ${location.pathname === item.to || (item.to !== '/' && location.pathname.startsWith(item.to)) ? 'layout__nav-link--active' : ''}`}
              >
                {item.label}
              </Link>
            ))}
            {hasRole('MODERADOR') && (
              <Link
                to="/admin/approvals"
                className={`layout__nav-link ${location.pathname.startsWith('/admin') ? 'layout__nav-link--active' : ''}`}
              >
                Aprovações
              </Link>
            )}
          </nav>
          <div className="layout__user">
            {member && (
              <span className="layout__points">
                {member.points} pts ({member.availablePoints} disp.)
              </span>
            )}
            <span>{user?.nickname ?? 'Membro'}</span>
            <button type="button" className="layout__logout" onClick={logout}>
              Sair
            </button>
          </div>
        </header>
        <main className="layout__main">
          <Outlet />
        </main>
      </div>
    </EventToastProvider>
    </AppToastProvider>
  )
}
