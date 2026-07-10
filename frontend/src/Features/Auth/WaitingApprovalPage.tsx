import { useAuthContext } from '@/Features/Auth/contexts/AuthContext'
import './Auth.styles.scss'

export function WaitingApprovalPage() {
  const { logout } = useAuthContext()

  return (
    <div className="auth-page">
      <div className="auth-card auth-card--center">
        <h1>Aguardando Aprovação</h1>
        <p>Seu cadastro foi recebido e está pendente de aprovação por um moderador.</p>
        <p className="auth-muted">Você será notificado quando for aprovado.</p>
        <div className="auth-status" style={{ justifyContent: 'center', marginTop: '24px' }}>
          <span className="auth-status-dot" />
          Cadastro pendente
        </div>
        <button type="button" className="auth-logout-btn" onClick={logout}>
          Sair
        </button>
      </div>
    </div>
  )
}
