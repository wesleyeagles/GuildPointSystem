import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import {
  useAdjustPoints,
  useCurrentMember,
  useGrantManualEvent,
  useMember,
  useMemberClaims,
  useUpdateMemberRole,
  useUpdateProfile,
} from '@/Domain/Member/hooks/useMembers'
import { useDenyEventClaim } from '@/Domain/Event/hooks/useEvents'
import { useObjectives } from '@/Domain/Objective/hooks/useObjectives'
import { useRaces, useClasses } from '@/Domain/Seed/hooks/useSeeds'
import { useAuthContext } from '@/Features/Auth/contexts/AuthContext'
import type { PointsModality, Role } from '@/Domain/types/models'
import { Button } from '@/Shared/ui/components/Button/Button'
import { SeedOptionPicker } from '@/Shared/ui/components/SeedOptionPicker/SeedOptionPicker'
import { Panel } from '@/Shared/ui/components/Panel/Panel'
import { formatLastLogin } from '@/Shared/utils/memberLastLogin'
import { publicAssetUrl } from '@/Shared/utils/publicAssetUrl'
import './Profile.styles.scss'

const ROLE_OPTIONS: { value: Role; label: string }[] = [
  { value: 'MEMBRO', label: 'Membro' },
  { value: 'MODERADOR', label: 'Moderador' },
  { value: 'ADMINISTRADOR', label: 'Administrador' },
  { value: 'LIDER', label: 'Líder' },
]

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
  const [level, setLevel] = useState(1)
  const [pointsAmount, setPointsAmount] = useState(0)
  const [pointsReason, setPointsReason] = useState('')
  const [modality, setModality] = useState<PointsModality>('AJUSTE')
  const [manualObjectiveId, setManualObjectiveId] = useState(0)
  const [denyingClaimId, setDenyingClaimId] = useState<number | null>(null)
  const [denyReason, setDenyReason] = useState('')
  const [memberRole, setMemberRole] = useState<Role>('MEMBRO')

  const { data: races = [] } = useRaces()
  const { data: classes = [] } = useClasses(raceId)
  const updateProfile = useUpdateProfile()
  const adjustPoints = useAdjustPoints()
  const updateMemberRole = useUpdateMemberRole()
  const grantManualEvent = useGrantManualEvent()
  const denyClaim = useDenyEventClaim()
  const isStaff = hasRole('ADMINISTRADOR')
  const isLeader = hasRole('LIDER')
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
    setLevel(profile.level)
    setPointsAmount(0)
    setPointsReason('')
    setManualObjectiveId(0)
    setDenyingClaimId(null)
    setDenyReason('')
    setMemberRole(profile.role)
  }, [profile?.id, profile?.nickname, profile?.raceId, profile?.classId, profile?.level, profile?.role])

  if (!profile) return <p>Carregando perfil...</p>

  const handleSaveProfile = async (e: React.FormEvent) => {
    e.preventDefault()
    await updateProfile.mutateAsync({
      id: profile.id,
      nickname,
      raceId,
      classId,
      level,
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

  const handleUpdateRole = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!isLeader || isOwn) return
    await updateMemberRole.mutateAsync({
      id: profile.id,
      role: memberRole,
    })
  }

  return (
    <div className="profile-page">
      {!isOwn && (
        <Link to="/" className="profile-page__back">
          ← Voltar ao ranking
        </Link>
      )}

      <Panel
        title={isOwn ? 'Ficha do personagem' : `Ficha: ${profile.nickname}`}
        code={`ID ${String(profile.id).padStart(4, '0')}`}
        className="profile-sheet"
      >
        <div className="profile-sheet__body">
          <div className="profile-sheet__portrait">
            {profile.classImageUrl ? (
              <img src={publicAssetUrl(profile.classImageUrl)} alt={profile.className} />
            ) : (
              <span>{profile.nickname.slice(0, 1).toUpperCase()}</span>
            )}
          </div>
          <div className="profile-sheet__identity">
            <span className="profile-sheet__name">{profile.nickname}</span>
            <dl className="profile-sheet__attrs">
              <div>
                <dt>Raça</dt>
                <dd>{profile.raceName}</dd>
              </div>
              <div>
                <dt>Classe</dt>
                <dd>{profile.className}</dd>
              </div>
              <div>
                <dt>Level</dt>
                <dd>{profile.level}</dd>
              </div>
              <div>
                <dt>Papel</dt>
                <dd>{profile.role}</dd>
              </div>
              <div>
                <dt>Último login</dt>
                <dd>{formatLastLogin(profile.lastLoginAt)}</dd>
              </div>
            </dl>
          </div>
          <div className="profile-sheet__stats">
            <div className="profile-sheet__stat">
              <span>Pontos</span>
              <strong>{profile.points}</strong>
            </div>
            <div className="profile-sheet__stat profile-sheet__stat--cyan">
              <span>Disponível</span>
              <strong>{profile.availablePoints}</strong>
            </div>
          </div>
        </div>
      </Panel>

      {(isOwn || isStaff) && (
        <div className={`profile-page__grid${isStaff && !isOwn ? ' profile-page__grid--staff' : ''}`}>
          <form className="profile-form" onSubmit={handleSaveProfile}>
            <h3>Editar Perfil</h3>
            <label>
              Nickname
              <input value={nickname} onChange={(e) => setNickname(e.target.value)} required />
            </label>
            <label>
              Level
              <input
                type="number"
                min={1}
                max={99}
                value={level}
                onChange={(e) => setLevel(Number(e.target.value))}
                required
              />
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

          {isStaff && !isOwn && (
            <>
              {isLeader && (
                <form className="profile-form" onSubmit={handleUpdateRole}>
                  <h3>Papel do Membro</h3>
                  <label>
                    Papel
                    <select
                      value={memberRole}
                      onChange={(e) => setMemberRole(e.target.value as Role)}
                    >
                      {ROLE_OPTIONS.map((option) => (
                        <option key={option.value} value={option.value}>
                          {option.label}
                        </option>
                      ))}
                    </select>
                  </label>
                  {memberRole === 'LIDER' && profile.role !== 'LIDER' && (
                    <p className="profile-form__hint">
                      Ao promover a Líder, você passará a ser Administrador.
                    </p>
                  )}
                  <Button type="submit" loading={updateMemberRole.isPending}>
                    Salvar papel
                  </Button>
                </form>
              )}

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
                        {!claim.denied && denyingClaimId !== claim.id && (
                          <Button
                            variant="danger"
                            size="sm"
                            onClick={() => {
                              setDenyingClaimId(claim.id)
                              setDenyReason('')
                            }}
                          >
                            Remover resgate
                          </Button>
                        )}
                        {!claim.denied && denyingClaimId === claim.id && (
                          <div className="profile-claims__deny">
                            <textarea
                              value={denyReason}
                              onChange={(e) => setDenyReason(e.target.value)}
                              placeholder="Motivo da remoção..."
                              rows={2}
                            />
                            <div className="profile-claims__deny-actions">
                              <Button
                                variant="danger"
                                size="sm"
                                onClick={() => {
                                  setDenyingClaimId(null)
                                  setDenyReason('')
                                }}
                              >
                                Cancelar
                              </Button>
                              <Button
                                variant="danger"
                                size="sm"
                                loading={denyClaim.isPending}
                                disabled={!denyReason.trim()}
                                onClick={async () => {
                                  const reason = denyReason.trim()
                                  if (!reason) return
                                  await denyClaim.mutateAsync({ claimId: claim.id, reason })
                                  setDenyingClaimId(null)
                                  setDenyReason('')
                                }}
                              >
                                Confirmar
                              </Button>
                            </div>
                          </div>
                        )}
                      </li>
                    ))}
                  </ul>
                )}
              </section>

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
            </>
          )}
        </div>
      )}
    </div>
  )
}
