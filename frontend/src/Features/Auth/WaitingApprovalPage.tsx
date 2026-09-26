import { useAuthContext } from '@/Features/Auth/contexts/AuthContext'
import './Auth.styles.scss'

export function WaitingApprovalPage() {
  const { logout } = useAuthContext()

  return (
    <div className="auth-page">
      <div className="auth-card auth-card--center">
        <div className="auth-window-bar auth-window-bar--amber">
          <span>Acesso restrito</span>
          <span className="auth-window-bar__code">PND-00</span>
        </div>
        <div className="auth-card-content">
          <h1>Aguardando Aprovação</h1>
          <p>Seu cadastro foi recebido e está pendente de aprovação por um moderador.</p>
          <p className="auth-muted">Você será notificado quando for aprovado.</p>
          <div className="auth-status">
            <span className="auth-status-dot" />
            Cadastro pendente
          </div>
          <button type="button" className="auth-logout-btn" onClick={logout}>
            Sair
          </button>
        </div>
      </div>
    </div>
  )
}
