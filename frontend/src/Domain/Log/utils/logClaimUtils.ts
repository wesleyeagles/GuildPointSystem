import type { AuditLogEntry } from '@/Domain/types/models'

export function getPayloadClaimId(payload: Record<string, unknown>): number | null {
  const id = payload.claimId
  if (typeof id === 'number' && Number.isFinite(id)) return id
  return null
}

/** Claim IDs with a matching EVENT_DENIED log on the current page. */
export function getDeniedClaimIds(logs: AuditLogEntry[]): Set<number> {
  const ids = new Set<number>()
  for (const log of logs) {
    if (log.type !== 'EVENT_DENIED') continue
    const id = getPayloadClaimId(log.payload)
    if (id !== null) ids.add(id)
  }
  return ids
}

export function isClaimDenied(
  log: AuditLogEntry,
  deniedClaimIds: Set<number>,
): boolean {
  if (log.payload.claimDenied === true) return true
  const claimId = getPayloadClaimId(log.payload)
  return claimId !== null && deniedClaimIds.has(claimId)
}

export function canDenyEventClaim(
  log: AuditLogEntry,
  userMemberId: number | undefined,
  isAdmin: boolean,
  deniedClaimIds: Set<number>,
): boolean {
  if (!isAdmin || log.type !== 'EVENT_CLAIMED') return false
  if (getPayloadClaimId(log.payload) === null) return false
  if (isClaimDenied(log, deniedClaimIds)) return false
  if (log.targetId === userMemberId) return false
  return true
}
