import { useState } from 'react'
import { Link, Navigate } from 'react-router-dom'
import { useRegister } from '@/Domain/Auth/hooks/useAuth'
import { findCoraRaceId } from '@/Domain/Seed/constants/guildRace'
import { useRaces, useClasses } from '@/Domain/Seed/hooks/useSeeds'
import { useAuthContext } from '@/Features/Auth/contexts/AuthContext'
import { SeedOptionPicker } from '@/Shared/ui/components/SeedOptionPicker/SeedOptionPicker'

export function RegisterPage() {
  const { isAuthenticated } = useAuthContext()
  const register = useRegister()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [nickname, setNickname] = useState('')
  const [classId, setClassId] = useState<number>(0)
  const [error, setError] = useState('')

  const { data: races = [] } = useRaces()
  const coraRaceId = findCoraRaceId(races)
  const { data: classes = [] } = useClasses(coraRaceId)

  if (isAuthenticated) return <Navigate to="/" replace />

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    setError('')
    if (coraRaceId <= 0) {
      setError('Raça Cora indisponível no momento. Tente novamente.')
      return
    }
    try {
      await register.mutateAsync({ email, password, nickname, raceId: coraRaceId, classId })
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Erro ao cadastrar')
    }
  }

  return (
    <form onSubmit={handleSubmit}>
      <div className="auth-field">
        <label htmlFor="reg-email">Email</label>
        <input
          id="reg-email"
          type="email"
          placeholder="seuemail@dominio.com"
          value={email}
          onChange={(e) => setEmail(e.target.value)}
          required
        />
      </div>

      <div className="auth-field">
        <label htmlFor="reg-password">Senha</label>
        <input
          id="reg-password"
          type="password"
          placeholder="••••••••"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          minLength={6}
          required
        />
      </div>

      <div className="auth-field">
        <label htmlFor="reg-nickname">Nickname</label>
        <input
          id="reg-nickname"
          placeholder="Seu nick no jogo"
          value={nickname}
          onChange={(e) => setNickname(e.target.value)}
          required
        />
      </div>

      {coraRaceId > 0 ? (
        <SeedOptionPicker
          label="Classe"
          options={classes}
          value={classId}
          onChange={setClassId}
          required
          showImages
        />
      ) : (
        <p className="auth-muted">Carregando classes...</p>
      )}

      {error && <p className="auth-error">{error}</p>}

      <button
        type="submit"
        className="auth-btn-primary"
        disabled={register.isPending || coraRaceId <= 0 || classId <= 0}
      >
        {register.isPending ? 'Cadastrando...' : 'Criar conta'}
      </button>

      <p className="auth-switch">
        Já tem conta? <Link to="/login">Entrar</Link>
      </p>
    </form>
  )
}
