import { useApproveMember, usePendingMembers } from '@/Domain/Member/hooks/useMembers'
import { Button } from '@/Shared/ui/components/Button/Button'
import { Panel } from '@/Shared/ui/components/Panel/Panel'
import './Approvals.styles.scss'

export function ApprovalsPage() {
  const { data: pending = [], isLoading } = usePendingMembers()
  const approveMember = useApproveMember()

  if (isLoading) return <p>Carregando pendentes...</p>

  return (
    <Panel
      title="Aprovações pendentes"
      variant="danger"
      code={`${pending.length} PND`}
      className="approvals-page"
      flush
    >
      {pending.length === 0 ? (
        <p className="approvals-page__empty">Nenhum cadastro pendente.</p>
      ) : (
        <ul className="approvals-list">
          {pending.map((member) => (
            <li key={member.id} className="approvals-list__item">
              <span className="approvals-list__slot" aria-hidden="true">
                {member.nickname.slice(0, 1).toUpperCase()}
              </span>
              <div className="approvals-list__info">
                <strong>{member.nickname}</strong>
                <small>
                  {member.email} · {member.raceName} / {member.className}
                </small>
              </div>
              <div className="approvals-list__actions">
                <Button
                  size="sm"
                  onClick={() => approveMember.mutate({ id: member.id, status: 'APROVADO' })}
                  loading={approveMember.isPending}
                >
                  Aprovar
                </Button>
                <Button
                  variant="danger"
                  size="sm"
                  onClick={() => approveMember.mutate({ id: member.id, status: 'REJEITADO' })}
                  loading={approveMember.isPending}
                >
                  Rejeitar
                </Button>
              </div>
            </li>
          ))}
        </ul>
      )}
    </Panel>
  )
}
