import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import {
  useAdjustPoints,
  useCurrentMember,
  useGrantManualEvent,
  useMember,
  useMemberClaims,
  useUpdateProfile,
} from '@/Domain/Member/hooks/useMembers'
import { useDenyEventClaim } from '@/Domain/Event/hooks/useEvents'
import { useObjectives } from '@/Domain/Objective/hooks/useObjectives'
import { useRaces, useClasses } from '@/Domain/Seed/hooks/useSeeds'
import { useAuthContext } from '@/Features/Auth/contexts/AuthContext'
import type { PointsModality } from '@/Domain/types/models'
import { Button } from '@/Shared/ui/components/Button/Button'
import { SeedOptionPicker } from '@/Shared/ui/components/SeedOptionPicker/SeedOptionPicker'
import './Profile.styles.scss'

export function ProfilePage() {
  const { id } = useParams<{ id: string }>()
  const { user, hasRole } = useAuthContext()
  const memberId = id ? Number(id) : user?.memberId
  const { data: ownProfile } = useCurrentMember()
  const { data: otherProfile } = useMember(memberId ?? 0)
  const profile = id ? otherProfile : ownProfile
  const { data: claims = [] } = useMemberClaims(id ? Number(id) : 0)
  const { data: objectives = [] } = useObjectives()
  const [nickname, setNickname] = useState('')
  const [raceId, setRaceId] = useState(0)
  const [classId, setClassId] = useState(0)
  const [pointsAmount, setPointsAmount] = useState(0)
  const [pointsReason, setPointsReason] = useState('')
  const [modality, setModality] = useState<PointsModality>('AJUSTE')
  const [manualObjectiveId, setManualObjectiveId] = useState(0)

  const { data: races = [] } = useRaces()
  const { data: classes = [] } = useClasses(raceId)
  const updateProfile = useUpdateProfile()
  const adjustPoints = useAdjustPoints()
  const grantManualEvent = useGrantManualEvent()
  const denyClaim = useDenyEventClaim()
  const isStaff = hasRole('ADMINISTRADOR')
  const isOwn = !id || Number(id) === user?.memberId

  const handleRaceChange = (nextRaceId: number) => {
    setRaceId(nextRaceId)
    setClassId(0)
  }

  useEffect(() => {
    if (!profile) return
    setNickname(profile.nickname)
    setRaceId(profile.raceId)
    setClassId(profile.classId)
    setPointsAmount(0)
    setPointsReason('')
    setManualObjectiveId(0)
  }, [profile?.id, profile?.nickname, profile?.raceId, profile?.classId])

  if (!profile) return <p>Carregando perfil...</p>

  const handleSaveProfile = async (e: React.FormEvent) => {
    e.preventDefault()
    await updateProfile.mutateAsync({
      id: profile.id,
      nickname,
      raceId,
      classId,
    })
  }

  const handleAdjustPoints = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!isStaff) return
    await adjustPoints.mutateAsync({
      id: profile.id,
      amount: pointsAmount,
      modality,
      reason: pointsReason,
    })
    setPointsAmount(0)
    setPointsReason('')
  }

  const handleGrantManualEvent = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!isStaff || manualObjectiveId <= 0) return
    await grantManualEvent.mutateAsync({
      memberId: profile.id,
      objectiveId: manualObjectiveId,
    })
    setManualObjectiveId(0)
  }

  return (
    <div className="profile-page">
      {!isOwn && (
        <Link to="/" className="profile-page__back">
          ← Voltar ao ranking
        </Link>
      )}

      <h2>{isOwn ? 'Meu Perfil' : `Perfil: ${profile.nickname}`}</h2>
      <div className="profile-page__stats">
        <span>Pontos: {profile.points}</span>
        <span>Disponível: {profile.availablePoints}</span>
        <span>
          {profile.raceName} / {profile.className}
        </span>
        {!isOwn && <span>Papel: {profile.role}</span>}
      </div>

      {(isOwn || isStaff) && (
        <form className="profile-form" onSubmit={handleSaveProfile}>
          <h3>Editar Perfil</h3>
          <label>
            Nickname
            <input value={nickname} onChange={(e) => setNickname(e.target.value)} required />
          </label>
          <label>
            Raça
            <select value={raceId} onChange={(e) => handleRaceChange(Number(e.target.value))}>
              {races.map((r) => (
                <option key={r.id} value={r.id}>
                  {r.name}
                </option>
              ))}
            </select>
          </label>
          {raceId > 0 && (
            <SeedOptionPicker
              label="Classe"
              options={classes}
              value={classId}
              onChange={setClassId}
              showImages
            />
          )}
          <Button type="submit" loading={updateProfile.isPending}>
            Salvar
          </Button>
        </form>
      )}

      {isStaff && !isOwn && (
        <>
          <form className="profile-form" onSubmit={handleAdjustPoints}>
            <h3>Ajustar Pontos</h3>
            <label>
              Quantidade (+/-)
              <input
                type="number"
                value={pointsAmount}
                onChange={(e) => setPointsAmount(Number(e.target.value))}
                required
              />
            </label>
            <label>
              Modalidade
              <select value={modality} onChange={(e) => setModality(e.target.value as PointsModality)}>
                <option value="AJUSTE">Ajuste</option>
                <option value="LEILAO">Leilão</option>
              </select>
            </label>
            <label>
              Motivo
              <textarea
                value={pointsReason}
                onChange={(e) => setPointsReason(e.target.value)}
                required
              />
            </label>
            <Button type="submit" loading={adjustPoints.isPending}>
              Aplicar
            </Button>
          </form>

          <form className="profile-form" onSubmit={handleGrantManualEvent}>
            <h3>Conceder Evento Manualmente</h3>
            <label>
              Objetivo
              <select
                value={manualObjectiveId}
                onChange={(e) => setManualObjectiveId(Number(e.target.value))}
                required
              >
                <option value={0}>Selecione...</option>
                {objectives.map((obj) => (
                  <option key={obj.id} value={obj.id}>
                    {obj.name} ({obj.points} pts)
                  </option>
                ))}
              </select>
            </label>
            <Button type="submit" loading={grantManualEvent.isPending} disabled={manualObjectiveId <= 0}>
              Conceder
            </Button>
          </form>

          <section className="profile-claims">
            <h3>Resgates de Eventos</h3>
            {claims.length === 0 ? (
              <p className="profile-claims__empty">Nenhum resgate registrado.</p>
            ) : (
              <ul className="profile-claims__list">
                {claims.map((claim) => (
                  <li key={claim.id} className={claim.denied ? 'profile-claims__item--denied' : ''}>
                    <div>
                      <strong>{claim.objectiveName}</strong>
                      <span>+{claim.points} pts</span>
                      <small>
                        {new Date(claim.claimedAt).toLocaleString()}
                        {claim.manual ? ' · manual' : ''}
                        {claim.denied ? ' · negado' : ''}
                      </small>
                    </div>
                    {!claim.denied && (
                      <Button
                        variant="danger"
                        size="sm"
                        onClick={() => denyClaim.mutate(claim.id)}
                        loading={denyClaim.isPending}
                      >
                        Negar
                      </Button>
                    )}
                  </li>
                ))}
              </ul>
            )}
          </section>
        </>
      )}
    </div>
  )
}
