import { useState, type DragEvent, type FormEvent } from 'react'
import { useAuthContext } from '@/Features/Auth/contexts/AuthContext'
import {
  nextFreeGroup,
  RAID_GROUP_SIZE,
  raidGroups,
  useCreateRaid,
  useDisbandRaid,
  useMoveRaidMember,
  useRaids,
  useRemoveRaidMember,
  useSetRaidGroupLeader,
  useSetRaidMark,
  useSetRaidHidden,
  useRaidAccess,
  useTransferRaidLeader,
  effectIconStyle,
  effectIconUrl,
  isRaidMemberOnline,
  useNowTick,
  useRaidLive,
  type Raid,
  type RaidEffect,
  type RaidLive,
  type RaidMember,
} from '@/Domain/Raid/hooks/useRaids'
import { Button } from '@/Shared/ui/components/Button/Button'
import { Panel } from '@/Shared/ui/components/Panel/Panel'
import { MemberClassIcon } from '@/Shared/ui/components/MemberClassIcon/MemberClassIcon'
import { useAppToast } from '@/Shared/ui/components/AppToast/AppToast'
import { ConfirmDialog } from '@/Shared/ui/components/ConfirmDialog/ConfirmDialog'
import { RaidAddMembersModal } from './components/RaidAddMembersModal'
import { RaidGameInvite } from './components/RaidGameInvite'
import { RaidMarkIcon, RaidMarkPicker } from './components/RaidMark'
import { RaidAccessSettings } from './components/RaidAccessSettings'
import './Raid.styles.scss'

const DRAG_TYPE = 'application/x-raid-member'

function errorMessage(error: unknown, fallback: string) {
  return error instanceof Error ? error.message : fallback
}

function Bar({ kind, value, max }: { kind: 'hp' | 'fp' | 'sp'; value: number; max: number }) {
  const pct = max > 0 ? Math.max(0, Math.min(100, (value / max) * 100)) : 0
  return (
    <span className={`raid-bar raid-bar--${kind}`} title={`${kind.toUpperCase()} ${value}/${max}`}>
      <span className="raid-bar__fill" style={{ width: `${pct}%` }} />
    </span>
  )
}

/** Lado do ícone de efeito na página, em px (o CSS de .raid-effect usa o mesmo). */
const EFFECT_ICON_SIZE = 18

/** Buffs (verde) e debuffs (vermelho), como a fileira de ícones da janela de PT do jogo. */
function EffectList({ effects }: { effects: RaidEffect[] }) {
  return (
    <span className="raid-effects">
      {effects.map((effect, i) => {
        const sprite = effectIconStyle(effect, EFFECT_ICON_SIZE)
        const icon = sprite ? null : effectIconUrl(effect)
        const title = `${effect.name || 'Efeito'}${effect.level ? ` (nível ${effect.level})` : ''}${effect.debuff ? ' — debuff' : ''}`
        const classes = `raid-effect${effect.debuff ? ' raid-effect--debuff' : ''}${sprite || icon ? ' raid-effect--icon' : ''}`
        return (
          <span key={`${effect.type}-${effect.index}-${i}`} className={classes} title={title} style={sprite ?? undefined}>
            {sprite ? null : icon ? <img src={icon} alt="" /> : (effect.name[0] ?? '?')}
          </span>
        )
      })}
    </span>
  )
}

function RaidMemberRow({ raid, member, live, now, onAction, onMark }: {
  raid: Raid
  member: RaidMember
  live: RaidLive | undefined
  now: number
  onAction: (action: 'groupLeader' | 'raidLeader' | 'remove' | 'hide', member: RaidMember) => void
  onMark: (member: RaidMember, mark: number | null) => void
}) {
  const vitals = live?.members.get(member.id)
  const online = live ? isRaidMemberOnline(vitals, live.receivedAt, now) : false
  const dead = online && vitals?.hp === 0
  const onDragStart = (e: DragEvent) => {
    e.dataTransfer.setData(DRAG_TYPE, String(member.id))
    e.dataTransfer.effectAllowed = 'move'
  }
  return (
    <li
      className={`raid-member${online ? '' : ' raid-member--offline'}${dead ? ' raid-member--dead' : ''}`}
      draggable={raid.canManage}
      onDragStart={raid.canManage ? onDragStart : undefined}
    >
      <span className="raid-member__head">
        {member.classImageUrl && member.className ? (
          <MemberClassIcon classImageUrl={member.classImageUrl} classLabel={member.className} size="sm" />
        ) : (
          <span className="raid-member__fallback">?</span>
        )}
        <span className="raid-member__level">L{online && vitals ? vitals.level : member.level}</span>
        {member.mark ? <RaidMarkIcon mark={member.mark} size={14} /> : null}
        <span className="raid-member__name" title={member.nickname}>{member.nickname}</span>
        {member.raidLeader && <span className="raid-member__badge raid-member__badge--raid" title="Líder da raid">Líder</span>}
        {member.groupLeader && <span className="raid-member__badge" title="Chama este subgrupo no jogo">PT</span>}
        {member.hidden && (
          <span className="raid-member__badge raid-member__badge--hidden"
            title="Oculto: não vê a raid no jogo nem no site e não é convidado; a DLL dele continua enviando">Oculto</span>
        )}
        {raid.canManage && (
          <span className="raid-member__actions">
            <RaidMarkPicker current={member.mark} onPick={(mark) => onMark(member, mark)} />
            <button type="button" title={member.hidden ? 'Mostrar a raid para ele' : 'Ocultar: acompanhar sem ele ver a raid'}
              onClick={() => onAction('hide', member)}>{member.hidden ? '◉' : '◌'}</button>
            {!member.groupLeader && (
              <button type="button" title="Tornar líder do subgrupo (chama a PT no jogo)" onClick={() => onAction('groupLeader', member)}>PT</button>
            )}
            {!member.raidLeader && (
              <button type="button" title="Passar a liderança da raid" onClick={() => onAction('raidLeader', member)}>♛</button>
            )}
            <button type="button" title="Tirar da raid" onClick={() => onAction('remove', member)}>✕</button>
          </span>
        )}
      </span>
      {online && vitals ? (
        <span className="raid-member__bars">
          <Bar kind="hp" value={vitals.hp} max={vitals.hpMax} />
          <Bar kind="fp" value={vitals.fp} max={vitals.fpMax} />
          <Bar kind="sp" value={vitals.sp} max={vitals.spMax} />
          {vitals.effects?.length > 0 && <EffectList effects={vitals.effects} />}
        </span>
      ) : (
        <span className="raid-member__status">Fora do jogo ou sem DLL</span>
      )}
    </li>
  )
}

function RaidCard({ raid, myId }: { raid: Raid; myId: number | undefined }) {
  const { showToast } = useAppToast()
  const move = useMoveRaidMember()
  const remove = useRemoveRaidMember()
  const groupLeader = useSetRaidGroupLeader()
  const raidLeader = useTransferRaidLeader()
  const setMark = useSetRaidMark()
  const setHidden = useSetRaidHidden()
  const disband = useDisbandRaid()
  const [adding, setAdding] = useState(false)
  const [dropTarget, setDropTarget] = useState<number | null>(null)
  const [confirm, setConfirm] = useState<'disband' | 'leave' | null>(null)
  const groups = raidGroups(raid)
  const freeGroup = nextFreeGroup(raid)
  const mine = raid.members.some((m) => m.id === myId)
  const { data: live } = useRaidLive(raid.id, raid.canManage)
  const now = useNowTick()
  const fail = (fallback: string) => ({ onError: (e: unknown) => showToast(errorMessage(e, fallback), 'error') })

  const onAction = (action: 'groupLeader' | 'raidLeader' | 'remove' | 'hide', member: RaidMember) => {
    const vars = { raidId: raid.id, memberId: member.id }
    if (action === 'hide') setHidden.mutate({ ...vars, hidden: !member.hidden }, fail('Não foi possível ocultar o membro.'))
    else if (action === 'groupLeader') groupLeader.mutate(vars, fail('Não foi possível trocar o líder do subgrupo.'))
    else if (action === 'raidLeader') raidLeader.mutate(vars, fail('Não foi possível passar a liderança.'))
    else remove.mutate(vars, fail('Não foi possível tirar o membro.'))
  }

  const onMark = (member: RaidMember, mark: number | null) =>
    setMark.mutate({ raidId: raid.id, memberId: member.id, mark }, fail('Não foi possível marcar o membro.'))

  const dropProps = (group: number) => raid.canManage ? {
    onDragOver: (e: DragEvent) => {
      if (!e.dataTransfer.types.includes(DRAG_TYPE)) return
      e.preventDefault()
      setDropTarget(group)
    },
    onDragLeave: () => setDropTarget((g) => (g === group ? null : g)),
    onDrop: (e: DragEvent) => {
      e.preventDefault()
      setDropTarget(null)
      const memberId = Number(e.dataTransfer.getData(DRAG_TYPE))
      const member = raid.members.find((m) => m.id === memberId)
      if (!member || member.group === group) return
      move.mutate({ raidId: raid.id, memberId, group }, fail('Não foi possível mover o membro.'))
    },
  } : {}

  return (
    <Panel
      className="raid-card"
      title={raid.name}
      code={`${raid.members.length} MEMBROS`}
      actions={
        <div className="raid-card__actions">
          {raid.canManage && <Button size="sm" onClick={() => setAdding(true)}>Adicionar membros</Button>}
          {raid.canManage && <Button size="sm" variant="danger" onClick={() => setConfirm('disband')}>Desfazer</Button>}
          {mine && <Button size="sm" variant="secondary" onClick={() => setConfirm('leave')}>Sair da raid</Button>}
        </div>
      }
    >
      <div className="raid-card__meta">
        Líder: <strong>{raid.leader}</strong>
      </div>
      <RaidGameInvite raidId={raid.id} canManage={raid.canManage} />
      <div className="raid-groups">
        {groups.map(({ group, members }) => (
          <section
            key={group}
            className={`raid-group${dropTarget === group ? ' raid-group--drop' : ''}`}
            {...dropProps(group)}
          >
            <header className="raid-group__title">
              Subgrupo {group} <span>{members.length}/{RAID_GROUP_SIZE}</span>
            </header>
            <ul className="raid-group__list">
              {members.map((m) => <RaidMemberRow key={m.id} raid={raid} member={m} live={live} now={now} onAction={onAction} onMark={onMark} />)}
            </ul>
          </section>
        ))}
        {raid.canManage && (
          <section
            className={`raid-group raid-group--new${dropTarget === freeGroup ? ' raid-group--drop' : ''}`}
            {...dropProps(freeGroup)}
          >
            <header className="raid-group__title">Subgrupo {freeGroup}</header>
            <p className="raid-group__hint">Arraste alguém para cá para abrir um subgrupo novo.</p>
          </section>
        )}
      </div>

      {adding && <RaidAddMembersModal raid={raid} onClose={() => setAdding(false)} />}
      <ConfirmDialog
        open={confirm !== null}
        title={confirm === 'disband' ? 'Desfazer raid' : 'Sair da raid'}
        confirmLabel={confirm === 'disband' ? 'Desfazer' : 'Sair'}
        variant="danger"
        loading={disband.isPending || remove.isPending}
        onCancel={() => setConfirm(null)}
        onConfirm={() => {
          const done = { ...fail('Não foi possível concluir.'), onSettled: () => setConfirm(null) }
          if (confirm === 'disband') disband.mutate(raid.id, done)
          else if (myId) remove.mutate({ raidId: raid.id, memberId: myId }, done)
        }}
      >
        {confirm === 'disband'
          ? `Todos os ${raid.members.length} integrantes saem da raid "${raid.name}".`
          : raid.leaderId === myId
            ? 'A liderança passa para quem entrou primeiro. Se ninguém sobrar, a raid acaba.'
            : `Você sai da raid "${raid.name}".`}
      </ConfirmDialog>
    </Panel>
  )
}

function RaidBoard() {
  const { user } = useAuthContext()
  const { showToast } = useAppToast()
  const { data: raids = [], isLoading } = useRaids()
  const create = useCreateRaid()
  const [name, setName] = useState('')
  const myId = user?.memberId
  const inRaid = raids.some((r) => r.members.some((m) => m.id === myId))
  const ordered = [...raids].sort((a, b) =>
    Number(b.members.some((m) => m.id === myId)) - Number(a.members.some((m) => m.id === myId)))

  const onCreate = (e: FormEvent) => {
    e.preventDefault()
    if (!name.trim()) return
    create.mutate(name.trim(), {
      onSuccess: () => setName(''),
      onError: (error) => showToast(errorMessage(error, 'Não foi possível criar a raid.'), 'error'),
    })
  }

  return (
    <div className="raid-page">
      <Panel title="Raid" code="RAID">
        <p className="raid-page__intro">
          Grupo sem limite de integrantes, dividido em subgrupos de até {RAID_GROUP_SIZE} — cada subgrupo vira uma PT no jogo.
          O líder de cada subgrupo recebe os convites pela DLL ao clicar em <strong>Chamar todos no jogo</strong>.
        </p>
        {!inRaid && (
          <form className="raid-page__create" onSubmit={onCreate}>
            <input
              className="raid-page__name"
              placeholder="Nome da raid (ex.: PB Hora 21)"
              aria-label="Nome da raid"
              maxLength={60}
              value={name}
              onChange={(e) => setName(e.target.value)}
            />
            <Button type="submit" loading={create.isPending} disabled={!name.trim()}>Criar raid</Button>
          </form>
        )}
      </Panel>
      {isLoading ? (
        <p className="raid-page__empty">Carregando raids...</p>
      ) : ordered.length === 0 ? (
        <p className="raid-page__empty">Nenhuma raid montada no momento.</p>
      ) : (
        ordered.map((raid) => <RaidCard key={raid.id} raid={raid} myId={myId} />)
      )}
    </div>
  )
}

/** Guarda da aba Raid: só quem tem acesso (RaidAccessService) carrega a raid e o canal ao vivo. */
export function RaidPage() {
  const { data: access, isLoading } = useRaidAccess()
  if (isLoading) return <p className="raid-page__empty">Carregando...</p>
  if (!access?.canView) {
    return (
      <div className="raid-page">
        <Panel title="Raid" code="RAID">
          <p className="raid-page__empty">Você não tem acesso à Raid. Peça a um Administrador para liberar.</p>
        </Panel>
      </div>
    )
  }
  return (
    <>
      <RaidBoard />
      {access.canConfigure && (
        <div className="raid-page raid-page--settings">
          <RaidAccessSettings key={`${access.minRole}-${access.viewers.map((v) => v.id).join(',')}`} access={access} />
        </div>
      )}
    </>
  )
}
