import { useState } from 'react'
import { useCompleteDiscordProfile } from '@/Domain/Auth/hooks/useAuth'
import { findCoraRaceId } from '@/Domain/Seed/constants/guildRace'
import { useRaces, useClasses } from '@/Domain/Seed/hooks/useSeeds'
import { useAuthContext } from '@/Features/Auth/contexts/AuthContext'
import { SeedOptionPicker } from '@/Shared/ui/components/SeedOptionPicker/SeedOptionPicker'
import './Auth.styles.scss'

export function DiscordProfileModal() {
  const { user, logout } = useAuthContext()
  const completeProfile = useCompleteDiscordProfile()
  const [nickname, setNickname] = useState(user?.nickname ?? '')
  const [classId, setClassId] = useState(0)
  const [error, setError] = useState('')

  const { data: races = [] } = useRaces()
  const coraRaceId = findCoraRaceId(races)
  const { data: classes = [] } = useClasses(coraRaceId)

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    setError('')
    if (coraRaceId <= 0) {
      setError('Raça Cora indisponível no momento. Tente novamente.')
      return
    }
    try {
      await completeProfile.mutateAsync({ nickname, raceId: coraRaceId, classId })
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Erro ao completar perfil')
    }
  }

  return (
    <div className="modal-overlay">
      <form className="modal-card" onSubmit={handleSubmit}>
        <div className="auth-window-bar">
          <span>Complete seu Perfil</span>
          <span className="auth-window-bar__code">CHR-01</span>
        </div>
        <div className="auth-card-content">
          <p className="auth-muted">Informe sua classe Cora para continuar.</p>

          <div className="auth-field">
            <label htmlFor="dp-nickname">Nickname</label>
            <input
              id="dp-nickname"
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
            disabled={completeProfile.isPending || coraRaceId <= 0 || classId <= 0}
          >
            {completeProfile.isPending ? 'Salvando...' : 'Salvar e Continuar'}
          </button>

          <button type="button" className="auth-logout-btn" onClick={logout}>
            Sair
          </button>
        </div>
      </form>
    </div>
  )
}
