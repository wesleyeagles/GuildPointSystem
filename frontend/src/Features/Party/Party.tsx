import { useState } from 'react'
import { useAuthContext } from '@/Features/Auth/contexts/AuthContext'
import {
  useAcceptPartyPending,
  useCancelPartyPending,
  useCreateParty,
  useDisbandParty,
  useInviteToParty,
  useLeaveParty,
  useLeavePartyLfg,
  usePartyBoard,
  useRejectPartyPending,
  useRequestJoinParty,
  useUpsertPartyLfg,
} from '@/Domain/Party/hooks/useParty'
import { Button } from '@/Shared/ui/components/Button/Button'
import { Panel } from '@/Shared/ui/components/Panel/Panel'
import { MemberClassIcon } from '@/Shared/ui/components/MemberClassIcon/MemberClassIcon'
import {
  PARTY_MAP_TABS,
  PARTY_MAX_MEMBERS,
  type MyPartyState,
  type PartyMap,
  type PartyPendingItem,
  type PartySummary,
} from '@/Features/Party/Party.types'
import './Party.styles.scss'

function pendingDescription(p: PartyPendingItem, my: MyPartyState): string {
  if (p.kind === 'JOIN_REQUEST' && my.leader && my.partyId === p.partyId) {
    return `${p.otherMember.nickname} pediu para entrar na sua PT`
  }
  if (p.kind === 'INVITE' && !my.partyId) {
    return `Convite para PT (${p.partyMap}) de ${p.otherMember.nickname}`
  }
  if (p.kind === 'JOIN_REQUEST') {
    return `Pedido enviado — aguardando ${p.otherMember.nickname}`
  }
  return `Convite enviado para ${p.otherMember.nickname}`
}

function isIncomingPending(p: PartyPendingItem, my: MyPartyState): boolean {
  if (p.kind === 'JOIN_REQUEST') return my.leader && my.partyId === p.partyId
  if (p.kind === 'INVITE') return !my.partyId
  return false
}

function isOutgoingPending(p: PartyPendingItem, my: MyPartyState): boolean {
  if (p.kind === 'JOIN_REQUEST') return !my.partyId
  if (p.kind === 'INVITE') return my.leader && my.partyId === p.partyId
  return false
}

function hasPendingJoinToParty(partyId: number, pending: PartyPendingItem[]): boolean {
  return pending.some((p) => p.kind === 'JOIN_REQUEST' && p.partyId === partyId)
}

function PartyRoster({ party }: { party: PartySummary }) {
  const slots = Array.from({ length: PARTY_MAX_MEMBERS }, (_, i) => party.members[i] ?? null)
  return (
    <ul className="party-card__roster">
      {slots.map((member, index) => (
        <li
          key={member?.id ?? `empty-${index}`}
          className={`party-card__roster-row${member ? '' : ' party-card__roster-row--empty'}`}
        >
          {member ? (
            <>
              {member.classImageUrl ? (
                <MemberClassIcon
                  classImageUrl={member.classImageUrl}
                  classLabel={member.className ?? member.nickname}
                  size="sm"
                />
              ) : (
                <span className="party-card__roster-fallback" aria-hidden="true">
                  {member.nickname.slice(0, 1).toUpperCase()}
                </span>
              )}
              <span className="party-card__roster-name" title={member.nickname}>
                {member.leader ? '★ ' : ''}
                {member.nickname}
              </span>
              <span className="party-card__roster-meta">
                {member.className ? `${member.className} · ` : ''}
                Lv {member.level}
              </span>
            </>
          ) : (
            <span className="party-card__roster-vago">Vago</span>
          )}
        </li>
      ))}
    </ul>
  )
}

function PartyCard({
  party,
  my,
  onRequestJoin,
  requestLoading,
}: {
  party: PartySummary
  my: MyPartyState
  onRequestJoin: (partyId: number) => void
  requestLoading: boolean
}) {
  const inThisParty = my.partyId === party.id
  const isLeaderHere = inThisParty && my.leader
  const full = party.memberCount >= PARTY_MAX_MEMBERS
  const canRequest =
    !my.partyId && !full && !hasPendingJoinToParty(party.id, my.pending)

  return (
    <li className="party-card">
      <div className="party-card__head">
        <h3 className="party-card__title">PT #{party.id}</h3>
        <span className="party-card__count">{party.memberCount}/{PARTY_MAX_MEMBERS}</span>
      </div>
      <PartyRoster party={party} />
      <div className="party-card__actions">
        {canRequest && (
          <Button size="sm" loading={requestLoading} onClick={() => onRequestJoin(party.id)}>
            Pedir entrada
          </Button>
        )}
        {isLeaderHere && (
          <span className="party-pending__label">Você é o líder desta PT</span>
        )}
      </div>
    </li>
  )
}

export function PartyPage() {
  const { user } = useAuthContext()
  const [map, setMap] = useState<PartyMap>('GERAL')
  const [lfgNote, setLfgNote] = useState('')

  const { data: board, isLoading } = usePartyBoard(map)
  const createParty = useCreateParty()
  const disbandParty = useDisbandParty()
  const leaveParty = useLeaveParty()
  const requestJoin = useRequestJoinParty()
  const inviteToParty = useInviteToParty()
  const acceptPending = useAcceptPartyPending()
  const rejectPending = useRejectPartyPending()
  const cancelPending = useCancelPartyPending()
  const upsertLfg = useUpsertPartyLfg()
  const leaveLfg = useLeavePartyLfg(map)

  const my = board?.my ?? {
    partyId: null,
    partyMap: null,
    leader: false,
    lfg: null,
    pending: [],
  }

  const myPartyOnOtherMap = my.partyId != null && my.partyMap != null && my.partyMap !== map
  const inParty = my.partyId != null
  const onLfgThisMap = my.lfg?.map === map
  const onLfgAny = my.lfg != null

  const incoming = my.pending.filter((p) => isIncomingPending(p, my))
  const outgoing = my.pending.filter((p) => isOutgoingPending(p, my))

  const handleCreateParty = () => {
    createParty.mutate({ map })
  }

  const handleLfgJoin = () => {
    upsertLfg.mutate({ map, note: lfgNote.trim() || undefined })
  }

  const leaderPartyId = my.leader ? my.partyId : null

  return (
    <div className="party-page">
      <div className="party-page__tabs" role="tablist" aria-label="Mapa">
        {PARTY_MAP_TABS.map((tab) => (
          <button
            key={tab.value}
            type="button"
            role="tab"
            aria-selected={map === tab.value}
            className={`party-page__tab${map === tab.value ? ' party-page__tab--active' : ''}`}
            onClick={() => setMap(tab.value)}
          >
            {tab.label}
          </button>
        ))}
      </div>

      {myPartyOnOtherMap && (
        <p className="party-page__banner">
          Sua PT está no mapa {my.partyMap}. Troque de aba para gerenciá-la.
        </p>
      )}

      {incoming.length > 0 && (
        <Panel title="Pendências" code={`${incoming.length} REQ`}>
          <div className="party-pending">
            {incoming.map((p) => (
              <div key={p.id} className="party-pending__item">
                <span className="party-pending__label">{pendingDescription(p, my)}</span>
                <div className="party-pending__actions">
                  <Button
                    size="sm"
                    loading={acceptPending.isPending}
                    onClick={() => acceptPending.mutate(p.id)}
                  >
                    Aceitar
                  </Button>
                  <Button
                    size="sm"
                    variant="secondary"
                    loading={rejectPending.isPending}
                    onClick={() => rejectPending.mutate(p.id)}
                  >
                    Recusar
                  </Button>
                </div>
              </div>
            ))}
          </div>
        </Panel>
      )}

      {outgoing.length > 0 && (
        <Panel title="Aguardando resposta" code={`${outgoing.length} OUT`}>
          <div className="party-pending">
            {outgoing.map((p) => (
              <div key={p.id} className="party-pending__item">
                <span className="party-pending__label">{pendingDescription(p, my)}</span>
                <Button
                  size="sm"
                  variant="secondary"
                  loading={cancelPending.isPending}
                  onClick={() => cancelPending.mutate(p.id)}
                >
                  Cancelar
                </Button>
              </div>
            ))}
          </div>
        </Panel>
      )}

      {inParty && my.partyMap === map && (
        <div className="party-my">
          <span>
            Você está na PT #{my.partyId}
            {my.leader ? ' (líder)' : ''}
          </span>
          <div className="party-card__actions">
            {my.leader && (
              <Button
                size="sm"
                variant="secondary"
                loading={disbandParty.isPending}
                onClick={() => my.partyId && disbandParty.mutate(my.partyId)}
              >
                Dissolver PT
              </Button>
            )}
            <Button
              size="sm"
              variant="secondary"
              loading={leaveParty.isPending}
              onClick={() => my.partyId && leaveParty.mutate(my.partyId)}
            >
              Sair da PT
            </Button>
          </div>
        </div>
      )}

      {!inParty && (
        <Panel title="Ações rápidas" code={map}>
          <div className="party-card__actions">
            <Button loading={createParty.isPending} onClick={handleCreateParty}>
              Criar PT
            </Button>
          </div>
          <div className="party-lfg-form" style={{ marginTop: '1rem' }}>
            <input
              type="text"
              maxLength={200}
              placeholder="Nota opcional (ex.: tank, heal)"
              value={lfgNote}
              onChange={(e) => setLfgNote(e.target.value)}
            />
            {onLfgThisMap ? (
              <Button variant="secondary" loading={leaveLfg.isPending} onClick={() => leaveLfg.mutate()}>
                Sair da lista de espera
              </Button>
            ) : (
              <Button loading={upsertLfg.isPending} onClick={handleLfgJoin}>
                {onLfgAny ? 'Mover para este mapa' : 'Entrar na lista de espera'}
              </Button>
            )}
          </div>
        </Panel>
      )}

      {isLoading && <p className="party-page__empty">Carregando...</p>}

      {board && (
        <div className="party-page__grid">
          <Panel title="PTs abertas" code={`${board.parties.length} PT`} flush>
            {board.parties.length === 0 ? (
              <p className="party-page__empty">Nenhuma PT neste mapa.</p>
            ) : (
              <ul style={{ listStyle: 'none', margin: 0, padding: 0, display: 'flex', flexDirection: 'column', gap: '0.75rem' }}>
                {board.parties.map((party) => (
                  <PartyCard
                    key={party.id}
                    party={party}
                    my={my}
                    onRequestJoin={(id) => requestJoin.mutate(id)}
                    requestLoading={requestJoin.isPending}
                  />
                ))}
              </ul>
            )}
          </Panel>

          <Panel title="Lista de espera" code={`${board.lfg.length} LFG`} flush>
            {board.lfg.length === 0 ? (
              <p className="party-page__empty">Ninguém procurando PT neste mapa.</p>
            ) : (
              <ul style={{ listStyle: 'none', margin: 0, padding: 0 }}>
                {board.lfg.map((entry) => (
                  <li key={entry.memberId} className="party-lfg-item">
                    {entry.classImageUrl ? (
                      <MemberClassIcon
                        classImageUrl={entry.classImageUrl}
                        classLabel={entry.className ?? ''}
                        size="sm"
                      />
                    ) : (
                      <span className="party-card__roster-fallback party-card__roster-fallback--muted">?</span>
                    )}
                    <div className="party-lfg-item__info">
                      <strong>{entry.nickname}</strong>
                      <span>
                        {entry.className ?? 'Classe'} · Lv {entry.level}
                        {entry.note ? ` · ${entry.note}` : ''}
                      </span>
                    </div>
                    {leaderPartyId != null && entry.memberId !== user?.memberId && (
                      <Button
                        size="sm"
                        loading={inviteToParty.isPending}
                        onClick={() =>
                          inviteToParty.mutate({ partyId: leaderPartyId, memberId: entry.memberId })
                        }
                      >
                        Convitar
                      </Button>
                    )}
                  </li>
                ))}
              </ul>
            )}
          </Panel>
        </div>
      )}
    </div>
  )
}
