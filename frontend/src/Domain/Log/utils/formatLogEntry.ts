import type { AuditLogEntry, AuditLogType } from '@/Domain/types/models'

const LOG_TYPE_LABELS: Record<AuditLogType, string> = {
  PARTY_DISCORD_CALLED: 'PT chamada no Discord',
  PARTY_GAME_INVITE: 'PT chamada no jogo',
  SHOUT: 'Shout no jogo',
  RAID_CREATED: 'Raid criada',
  RAID_DISBANDED: 'Raid desfeita',
  RAID_GAME_INVITE: 'Raid chamada no jogo',
  RAID_ACCESS_UPDATED: 'Acesso à Raid alterado',
  CHAT_TRANSLATE_ACCESS_UPDATED: 'Lista do tradutor do chat alterada',
  CHAT_TRANSLATE_GLOSSARY_UPDATED: 'Glossário do tradutor alterado',
  RESOURCE_ACCESS_UPDATED: 'Lista do mapa de recursos alterada',
  RESOURCE_MOB_UPDATED: 'Grau de mob de recurso alterado',
  PB_BOSS_UPDATED: 'Boss do histórico de PB alterado',
  MARKET_PRICE_ACCESS_UPDATED: 'Lista do preço do market alterada',
  MARKET_WATCH_UPDATED: 'Alerta de item no market alterado',
  DLL_RELEASED: 'Nova versão da DLL',
  PARTY_QUEUE_UPDATED: 'Fila de PT atualizada',
  ROTATION_UPDATED: 'Rotação atualizada',
  QUEST_CATALOG_UPDATED: 'Lista de quests revisada',
  EQUIPMENT_CHANGED: 'Equipamento atualizado',
  EQUIPMENT_CATALOG_IMPORTED: 'Catálogo EDF importado',
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
  AUCTION_CANCELLED: 'Leilão cancelado',
  AUCTION_DELETED: 'Leilão excluído',
  PROFILE_UPDATED: 'Perfil alterado',
  MEMBER_APPROVED: 'Cadastro aprovado',
  MEMBER_REJECTED: 'Cadastro rejeitado',
  MEMBER_REGISTERED: 'Novo cadastro',
  MEMBER_ROLE_CHANGED: 'Papel alterado',
  MEMBER_DELETED: 'Conta excluída',
  MEMBER_SUSPENDED: 'Conta suspensa',
  MEMBER_UNSUSPENDED: 'Suspensão retirada',
  MEMBER_SELL_BLOCK_CHANGED: 'Bloqueio de venda da conta',
  MEMBER_ENGLISH_PLAYER_CHANGED: 'Tag English Player',
  MEMBER_PASSWORD_RESET: 'Senha redefinida',
  MEMBER_PASSWORD_CHANGED: 'Senha alterada',
  NEWS_CREATED: 'Notícia publicada',
  NEWS_UPDATED: 'Notícia editada',
  NEWS_DELETED: 'Notícia excluída',
  DONATION_ACCEPTED: 'Doação aceita',
  DONATION_REJECTED: 'Doação recusada',
  DONATION_RATE_UPDATED: 'Valor da doação alterado',
  EMOJI_CREATED: 'Emoji criado',
  EMOJI_DELETED: 'Emoji excluído',
}

const ROLE_LABELS: Record<string, string> = {
  MEMBRO: 'Membro',
  MODERADOR: 'Moderador',
  ADMINISTRADOR: 'Administrador',
  LIDER: 'Líder',
}

const PROFILE_FIELD_LABELS: Record<string, string> = {
  nickname: 'Nickname',
  classId: 'Classe',
  raceId: 'Raça',
  level: 'Level',
}

function str(value: unknown): string {
  if (value === null || value === undefined) return ''
  return String(value)
}

/** Tipos para o filtro da aba Logs, em ordem alfabética do nome. Equipamento só aparece para admin. */
export function getLogTypeOptions(isAdmin: boolean): { value: AuditLogType; label: string }[] {
  return (Object.entries(LOG_TYPE_LABELS) as [AuditLogType, string][])
    .filter(([type]) => isAdmin || (type !== 'EQUIPMENT_CHANGED' && type !== 'EQUIPMENT_CATALOG_IMPORTED'))
    .map(([value, label]) => ({ value, label }))
    .sort((a, b) => a.label.localeCompare(b.label, 'pt-BR'))
}

export function getLogTypeLabel(type: AuditLogType): string {
  return LOG_TYPE_LABELS[type] ?? type
}

export function formatLogDetails(log: AuditLogEntry): string {
  const p = log.payload

  switch (log.type) {
    case 'PARTY_DISCORD_CALLED':
      return str(p.party)
    case 'DLL_RELEASED':
      return `v${str(p.version)} · ${str(p.fileName)}`
    case 'SHOUT':
      return str(p.message)
    case 'RAID_CREATED':
    case 'RAID_DISBANDED':
      return str(p.name)
    case 'RAID_ACCESS_UPDATED':
      return `A partir de ${str(p.minRole)}${Array.isArray(p.viewers) && p.viewers.length ? ` · Liberados: ${p.viewers.join(', ')}` : ''}`
    case 'CHAT_TRANSLATE_ACCESS_UPDATED':
      return Array.isArray(p.translators) && p.translators.length ? `Liberados: ${p.translators.join(', ')}` : 'Ninguém'
    case 'MARKET_PRICE_ACCESS_UPDATED':
      return Array.isArray(p.viewers) && p.viewers.length ? `Liberados: ${p.viewers.join(', ')}` : 'Ninguém'
    case 'MARKET_WATCH_UPDATED': {
      const filters = [p.minUpgrade ? `+${str(p.minUpgrade)} ou mais` : '', p.maxPrice ? `até ${Number(p.maxPrice).toLocaleString('pt-BR')} CS/un.` : '']
        .filter(Boolean).join(', ')
      return `${p.removed ? 'Removido: ' : ''}${str(p.item)}${filters ? ` (${filters})` : ''}`
    }
    case 'RESOURCE_ACCESS_UPDATED':
      return Array.isArray(p.trackers) && p.trackers.length ? `Liberados: ${p.trackers.join(', ')}` : 'Ninguém'
    case 'RESOURCE_MOB_UPDATED':
      return `${str(p.name)} → ${Number(p.tier) ? `Grau ${str(p.tier)}` : 'Não é recurso'}`
    case 'PB_BOSS_UPDATED':
      return p.removed ? `Removido: ${str(p.name)}` : `${str(p.name)}${p.label ? ` (${str(p.label)})` : ''} · ${p.kind === 'ROTACAO' ? 'Rotação' : 'Extra'}`
    case 'CHAT_TRANSLATE_GLOSSARY_UPDATED':
      return p.removed ? `Removido: ${str(p.source)}` : `${str(p.source)} → ${str(p.translation)}`
    case 'RAID_GAME_INVITE':
      return Array.isArray(p.groups)
        ? `${str(p.name)} · ${p.groups.map((g: { group?: unknown; leader?: unknown }) => `Subgrupo ${str(g.group)}: ${str(g.leader)}`).join(' · ')}`
        : str(p.name)
    case 'PARTY_GAME_INVITE':
      return Array.isArray(p.nicks) ? `Convites: ${p.nicks.join(', ')}` : ''
    case 'QUEST_CATALOG_UPDATED':
      return `${str(p.code)} · ${p.hidden ? 'Ocultada da checklist' : 'Restaurada na checklist'}`
    case 'EQUIPMENT_CHANGED':
      if (p.action === 'RELIC') return `Relic #${str(p.relicId)} · ${p.active ? 'Ativada' : 'Desativada'}`
      if (p.action === 'CREATE' && p.quantity) return `${str(p.slot)} · Entrega de ${str(p.quantity)} unidade(s) · Registro #${str(p.assignmentId)}`
      if (p.action === 'CLIENT_SYNC' && Array.isArray(p.changes))
        return `Sincronizado pelo jogo · ${p.changes.map((c: { slot?: unknown; after?: { name?: unknown } | null }) =>
          `${str(c.slot)}: ${c.after?.name ? str(c.after.name) : 'vazio'}`).join(' · ')}`
      if (p.action === 'CLIENT_SYNC') {
        // Logs antigos: um por posição.
        const after = p.after as { name?: unknown } | null | undefined
        return `${str(p.slot)} · Sincronizado pelo jogo: ${after?.name ? str(after.name) : 'vazio'}`
      }
      if (p.action === 'REVIEW_REQUEST') return `Revisão de equipamentos solicitada · Prazo: ${str(p.dueAt)}`
      if (p.action === 'REVIEW_CONFIRM') return 'Revisão de equipamentos confirmada'
      if (p.action === 'REMOVE' || p.action === 'CLEAR' || p.action === 'UNDO_REDO')
        return `${str(p.slot)} · ${p.action === 'REMOVE' ? 'Equipamento removido' : p.action === 'CLEAR' ? 'Equipamentos limpos' : 'Remoção desfeita/refeita'}`
      return `${str(p.slot)} · Registro #${str(p.assignmentId)} · ${str(p.action)}${p.reason ? ` · ${str(p.reason)}` : ''}`
    case 'EQUIPMENT_CATALOG_IMPORTED':
      return `${str(p.count)} modelos · ${str(p.excluded)} códigos ambíguos bloqueados`
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
      return `${str(p.objectiveName)} · +${str(p.points)} pts${p.source === 'GAME' ? ' · pelo jogo' : ''}`
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
    case 'AUCTION_CANCELLED': {
      const released = Array.isArray(p.releasedMemberIds) ? p.releasedMemberIds.length : 0
      return `#${str(p.auctionId)} · lance ${str(p.amount)} pts · ${released} reserva(s) devolvida(s)`
    }
    case 'AUCTION_DELETED':
      return `#${str(p.auctionId)} · ${str(p.items)}`
    case 'PARTY_QUEUE_UPDATED':
      if (p.action === 'LOCKED') return 'Lista de espera bloqueada · ninguém entra por conta própria'
      if (p.action === 'UNLOCKED') return 'Lista de espera desbloqueada'
      if (p.action === 'AFK_STARTED') return 'AFK avisado · limite de 15 minutos'
      if (p.action === 'AFK_CANCELLED') return 'AFK cancelado · voltou antes do prazo'
      if (p.action === 'AFK_EXPIRED') return '15 minutos de AFK · enviado ao final da fila'
      if (p.action === 'AFK_RETURNED') return 'Retornou do AFK · permanece na fila'
      if (p.action === 'UNANSWERED') return 'Chamado, não respondeu · movido ao final entre os mesmos pontos'
      if (p.action === 'RESET') return `Entrou em uma PT · prioridade ${str(p.oldPoints)} → 0`
      return `Prioridade da fila: ${str(p.oldPoints)} → ${str(p.newPoints)}`
    case 'ROTATION_UPDATED':
      return `Versão ${str(p.version)} · ${str(p.slots)} horários`
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
    case 'MEMBER_ROLE_CHANGED':
      return `${ROLE_LABELS[str(p.oldRole)] ?? str(p.oldRole)} → ${ROLE_LABELS[str(p.newRole)] ?? str(p.newRole)}`
    case 'NEWS_CREATED':
    case 'NEWS_UPDATED':
    case 'NEWS_DELETED':
      return `"${str(p.title)}"`
    case 'DONATION_ACCEPTED':
      return `${str(p.amountKk)}kk de disena · +${str(p.points)} pts`
    case 'DONATION_REJECTED':
      return `${str(p.amountKk)}kk de disena`
    case 'EMOJI_CREATED':
    case 'EMOJI_DELETED':
      return `:${str(p.name)}:`
    case 'DONATION_RATE_UPDATED':
      return `1kk = ${str(p.oldPointsPerKk)} → ${str(p.newPointsPerKk)} pts`
    case 'MEMBER_DELETED':
      return `${str(p.nickname)} · ${ROLE_LABELS[str(p.role)] ?? str(p.role)} · ${str(p.points)} pts`
    case 'MEMBER_SUSPENDED':
      return p.reason ? `Motivo: ${str(p.reason)}` : ''
    case 'MEMBER_SELL_BLOCK_CHANGED':
      return p.blocked
        ? `Conta bloqueada para venda${p.until ? ` até ${str(p.until).split('-').reverse().join('/')}` : ''}`
        : 'Bloqueio de venda removido'
    case 'MEMBER_ENGLISH_PLAYER_CHANGED':
      return p.englishPlayer ? 'Marcado como English Player (DLL em inglês)' : 'Tag English Player removida (DLL em português)'
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
