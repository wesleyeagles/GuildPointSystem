import { useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { useDenyEventClaim } from '@/Domain/Event/hooks/useEvents'
import { useLogs, useLogsSubscription } from '@/Domain/Log/hooks/useLogs'
import {
  formatLogActor,
  formatLogDetails,
  formatLogTarget,
  getLogTypeLabel,
} from '@/Domain/Log/utils/formatLogEntry'
import {
  canDenyEventClaim,
  getDeniedClaimIds,
  getPayloadClaimId,
  isClaimDenied,
} from '@/Domain/Log/utils/logClaimUtils'
import { useAuthContext } from '@/Features/Auth/contexts/AuthContext'
import type { AuditLogEntry } from '@/Domain/types/models'
import { Button } from '@/Shared/ui/components/Button/Button'
import { Panel } from '@/Shared/ui/components/Panel/Panel'
import './Logs.styles.scss'

const PAGE_SIZE = 50

export function LogsPage() {
  const [page, setPage] = useState(0)
  const [denyingClaimId, setDenyingClaimId] = useState<number | null>(null)
  const [denyReason, setDenyReason] = useState('')
  const { data, isLoading } = useLogs(page, PAGE_SIZE)
  useLogsSubscription()
  const denyClaim = useDenyEventClaim()
  const { user, hasRole } = useAuthContext()
  const isAdmin = hasRole('ADMINISTRADOR')

  const logs = data?.content ?? []
  const totalPages = data?.totalPages ?? 1
  const deniedClaimIds = useMemo(() => getDeniedClaimIds(logs), [logs])

  const handleDeny = async (claimId: number) => {
    const reason = denyReason.trim()
    if (!reason) return
    await denyClaim.mutateAsync({ claimId, reason })
    setDenyingClaimId(null)
    setDenyReason('')
  }

  if (isLoading) return <p>Carregando logs...</p>

  return (
    <Panel
      title="Registro de auditoria"
      code={`PG ${String(page + 1).padStart(2, '0')}`}
      className="logs-page"
      flush
    >
      {logs.length === 0 ? (
        <p className="logs-page__empty">Nenhum registro de auditoria.</p>
      ) : (
        <>
          <ul className="logs-list">
            {logs.map((log) => (
              <LogListItem
                key={log.id}
                log={log}
                isAdmin={isAdmin}
                userMemberId={user?.memberId}
                deniedClaimIds={deniedClaimIds}
                denyingClaimId={denyingClaimId}
                denyReason={denyReason}
                denyPending={denyClaim.isPending}
                onStartDeny={(claimId) => {
                  setDenyingClaimId(claimId)
                  setDenyReason('')
                }}
                onCancelDeny={() => {
                  setDenyingClaimId(null)
                  setDenyReason('')
                }}
                onDenyReasonChange={setDenyReason}
                onConfirmDeny={handleDeny}
              />
            ))}
          </ul>

          {totalPages > 1 && (
            <div className="logs-pagination">
              <button
                className="logs-pagination__btn"
                onClick={() => setPage((p) => Math.max(0, p - 1))}
                disabled={page === 0}
                type="button"
              >
                ‹
              </button>
              <span className="logs-pagination__info">
                {page + 1} <span className="logs-pagination__sep">/</span> {totalPages}
              </span>
              <button
                className="logs-pagination__btn"
                onClick={() => setPage((p) => Math.min(totalPages - 1, p + 1))}
                disabled={page >= totalPages - 1}
                type="button"
              >
                ›
              </button>
            </div>
          )}
        </>
      )}
    </Panel>
  )
}

interface LogListItemProps {
  log: AuditLogEntry
  isAdmin: boolean
  userMemberId: number | undefined
  deniedClaimIds: Set<number>
  denyingClaimId: number | null
  denyReason: string
  denyPending: boolean
  onStartDeny: (claimId: number) => void
  onCancelDeny: () => void
  onDenyReasonChange: (value: string) => void
  onConfirmDeny: (claimId: number) => Promise<void>
}

function LogListItem({
  log,
  isAdmin,
  userMemberId,
  deniedClaimIds,
  denyingClaimId,
  denyReason,
  denyPending,
  onStartDeny,
  onCancelDeny,
  onDenyReasonChange,
  onConfirmDeny,
}: LogListItemProps) {
  const claimId = getPayloadClaimId(log.payload)
  const denied =
    log.type === 'EVENT_DENIED' || isClaimDenied(log, deniedClaimIds)
  const showDeny =
    canDenyEventClaim(log, userMemberId, isAdmin, deniedClaimIds) &&
    claimId !== null
  const isDenying = claimId !== null && denyingClaimId === claimId

  return (
    <li
      className={[
        'logs-list__item',
        denied || log.type === 'EVENT_DENIED' ? 'logs-list__item--denied' : '',
      ]
        .filter(Boolean)
        .join(' ')}
    >
      <div className="logs-list__header">
        <time className="logs-list__time">{new Date(log.createdAt).toLocaleString()}</time>
        <span className="logs-list__type">{getLogTypeLabel(log.type)}</span>
      </div>
      <p className="logs-list__details">{formatLogDetails(log)}</p>
      <div className="logs-list__meta">
        <span>
          Por:{' '}
          {log.actorId ? (
            <Link to={`/profile/${log.actorId}`}>{formatLogActor(log)}</Link>
          ) : (
            formatLogActor(log)
          )}
        </span>
        {log.targetId && (
          <span>
            Membro:{' '}
            <Link to={`/profile/${log.targetId}`}>{formatLogTarget(log)}</Link>
          </span>
        )}
      </div>

      {showDeny && !isDenying && (
        <div className="logs-list__actions">
          <Button variant="danger" size="sm" onClick={() => onStartDeny(claimId!)}>
            Remover resgate
          </Button>
        </div>
      )}

      {showDeny && isDenying && (
        <div className="logs-list__deny">
          <textarea
            value={denyReason}
            onChange={(e) => onDenyReasonChange(e.target.value)}
            placeholder="Motivo da remoção..."
            rows={2}
          />
          <div className="logs-list__deny-actions">
            <Button variant="danger" size="sm" onClick={onCancelDeny}>
              Cancelar
            </Button>
            <Button
              variant="danger"
              size="sm"
              loading={denyPending}
              disabled={!denyReason.trim()}
              onClick={() => onConfirmDeny(claimId!)}
            >
              Confirmar
            </Button>
          </div>
        </div>
      )}
    </li>
  )
}
