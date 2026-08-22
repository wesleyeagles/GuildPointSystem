import type { AuditLogEntry, AuditLogType } from '@/Domain/types/models'

const LOG_TYPE_LABELS: Record<AuditLogType, string> = {
  OBJECTIVE_CREATED: 'Objetivo criado',
  OBJECTIVE_UPDATED: 'Objetivo editado',
  OBJECTIVE_DELETED: 'Objetivo removido',
  EVENT_CREATED: 'Evento criado',
  EVENT_CLAIMED: 'Evento resgatado',
  EVENT_DENIED: 'Evento removido do membro',
  EVENT_CANCELLED: 'Evento cancelado',
  POINTS_ADJUSTED: 'Pontos ajustados',
  AUCTION_CREATED: 'Leilão criado',
  AUCTION_CLOSED: 'Leilão encerrado',
  PROFILE_UPDATED: 'Perfil alterado',
  MEMBER_APPROVED: 'Cadastro aprovado',
  MEMBER_REJECTED: 'Cadastro rejeitado',
  MEMBER_REGISTERED: 'Novo cadastro',
}

const PROFILE_FIELD_LABELS: Record<string, string> = {
  nickname: 'Nickname',
  classId: 'Classe',
  raceId: 'Raça',
}

function str(value: unknown): string {
  if (value === null || value === undefined) return ''
  return String(value)
}

export function getLogTypeLabel(type: AuditLogType): string {
  return LOG_TYPE_LABELS[type] ?? type
}

export function formatLogDetails(log: AuditLogEntry): string {
  const p = log.payload

  switch (log.type) {
    case 'OBJECTIVE_CREATED': {
      const limit =
        p.type === 'LIMITADO' && p.dailyLimit
          ? ` · limite ${p.dailyLimit}/dia`
          : ''
      return `"${str(p.name)}" · ${str(p.points)} pts${limit}`
    }
    case 'OBJECTIVE_UPDATED':
    case 'OBJECTIVE_DELETED':
      return `"${str(p.name)}"`
    case 'EVENT_CREATED':
      return `${str(p.objectiveName)} · ${str(p.durationMinutes)} min`
    case 'EVENT_CLAIMED':
      return `${str(p.objectiveName)} · +${str(p.points)} pts`
    case 'EVENT_DENIED': {
      const reason = str(p.reason)
      const points = str(p.pointsReverted)
      return reason ? `"${reason}" · -${points} pts` : `-${points} pts revertidos`
    }
    case 'EVENT_CANCELLED':
      return str(p.objectiveName)
    case 'POINTS_ADJUSTED': {
      const amount = Number(p.amount)
      const sign = amount >= 0 ? '+' : ''
      return `${sign}${amount} pts · ${str(p.reason)}`
    }
    case 'AUCTION_CREATED':
      return `#${str(p.auctionId)} · ${str(p.durationMinutes)} min`
    case 'AUCTION_CLOSED': {
      const winner =
        p.winnerId === 'none' || p.winnerId == null
          ? 'Sem vencedor'
          : `Vencedor #${str(p.winnerId)}`
      return `${winner} · ${str(p.amount)} pts`
    }
    case 'PROFILE_UPDATED': {
      const field = PROFILE_FIELD_LABELS[str(p.field)] ?? str(p.field)
      return `${field}: ${str(p.oldValue)} → ${str(p.newValue)}`
    }
    case 'MEMBER_APPROVED':
      return 'Membro aprovado na guild'
    case 'MEMBER_REJECTED':
      return 'Cadastro rejeitado'
    case 'MEMBER_REGISTERED':
      return str(p.nickname)
    default:
      return ''
  }
}

export function formatLogActor(log: AuditLogEntry): string {
  return log.actorNickname ?? '—'
}

export function formatLogTarget(log: AuditLogEntry): string {
  return log.targetNickname ?? '—'
}
