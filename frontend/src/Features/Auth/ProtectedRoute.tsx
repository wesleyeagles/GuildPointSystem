import { Navigate, Outlet } from 'react-router-dom'
import { useAuthContext } from '@/Features/Auth/contexts/AuthContext'
import { DiscordProfileModal } from '@/Features/Auth/DiscordProfileModal'
import { WaitingApprovalPage } from '@/Features/Auth/WaitingApprovalPage'

export function ProtectedRoute() {
  const { isAuthenticated, isLoading, user } = useAuthContext()

  if (isLoading) {
    return (
      <div className="loading-screen">
        <p>Carregando...</p>
      </div>
    )
  }

  if (!isAuthenticated || !user) {
    return <Navigate to="/login" replace />
  }

  if (user.status === 'PENDENTE') {
    return <WaitingApprovalPage />
  }

  if (!user.profileComplete) {
    return <DiscordProfileModal />
  }

  return <Outlet />
}

export function ModeratorRoute() {
  const { hasRole } = useAuthContext()
  if (!hasRole('MODERADOR')) {
    return <Navigate to="/" replace />
  }
  return <Outlet />
}
