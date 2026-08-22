import { useState, useEffect } from 'react'
import { Link, Navigate, useSearchParams } from 'react-router-dom'
import { useLogin } from '@/Domain/Auth/hooks/useAuth'
import { useAuthContext } from '@/Features/Auth/contexts/AuthContext'
import { getOAuthLoginUrl } from '@/Shared/utils/env'

const DISCORD_ICON = (
  <svg width="18" height="18" viewBox="0 0 24 24" fill="currentColor">
    <path d="M20.317 4.37a19.79 19.79 0 0 0-4.885-1.515.07.07 0 0 0-.075.035c-.21.375-.444.864-.608 1.25a18.27 18.27 0 0 0-5.487 0 12.64 12.64 0 0 0-.617-1.25.077.077 0 0 0-.075-.035A19.736 19.736 0 0 0 3.677 4.37a.07.07 0 0 0-.032.027C.533 9.046-.32 13.58.099 18.057a.082.082 0 0 0 .031.057 19.9 19.9 0 0 0 5.993 3.03.078.078 0 0 0 .084-.028 14.09 14.09 0 0 0 1.226-1.994.076.076 0 0 0-.041-.106 13.107 13.107 0 0 1-1.872-.892.077.077 0 0 1-.008-.128 10.2 10.2 0 0 0 .372-.292.074.074 0 0 1 .077-.01c3.928 1.793 8.18 1.793 12.062 0a.074.074 0 0 1 .078.01c.12.098.246.198.373.292a.077.077 0 0 1-.006.127 12.299 12.299 0 0 1-1.873.892.076.076 0 0 0-.04.107c.36.698.772 1.362 1.225 1.993a.076.076 0 0 0 .084.028 19.838 19.838 0 0 0 6.002-3.03.077.077 0 0 0 .032-.054c.5-5.177-.838-9.674-3.549-13.66a.06.06 0 0 0-.031-.03z" />
  </svg>
)

export function LoginPage() {
  const { isAuthenticated } = useAuthContext()
  const login = useLogin()
  const [searchParams] = useSearchParams()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState('')

  useEffect(() => {
    if (searchParams.get('error') === 'discord') {
      setError(
        searchParams.get('message') ??
          'Não foi possível entrar com Discord. Tente novamente.',
      )
    }
  }, [searchParams])

  if (isAuthenticated) return <Navigate to="/" replace />

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    setError('')
    try {
      await login.mutateAsync({ email, password })
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Erro ao entrar')
    }
  }

  return (
    <form onSubmit={handleSubmit}>
      <div className="auth-logo-wrap">
        <img src="/Logo-Blacklist.png" alt="Blacklist" className="auth-logo" />
      </div>

      <div className="auth-field">
        <label htmlFor="login-email">Email</label>
        <input
          id="login-email"
          type="email"
          placeholder="seuemail@dominio.com"
          value={email}
          onChange={(e) => setEmail(e.target.value)}
          required
        />
      </div>

      <div className="auth-field">
        <label htmlFor="login-password">Senha</label>
        <input
          id="login-password"
          type="password"
          placeholder="••••••••"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          required
        />
      </div>

      {error && <p className="auth-error">{error}</p>}

      <button
        type="submit"
        className="auth-btn-primary"
        disabled={login.isPending}
      >
        {login.isPending ? 'Entrando...' : 'Entrar no sistema'}
      </button>

      <div className="auth-divider">OU</div>

      <button
        type="button"
        className="auth-btn-discord"
        onClick={() => {
          window.location.href = getOAuthLoginUrl()
        }}
      >
        {DISCORD_ICON}
        Continuar com Discord
      </button>

      <p className="auth-switch">
        Não tem conta? <Link to="/register">Cadastre-se</Link>
      </p>
    </form>
  )
}
