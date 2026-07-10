import { useApproveMember, usePendingMembers } from '@/Domain/Member/hooks/useMembers'
import { Button } from '@/Shared/ui/components/Button/Button'
import './Approvals.styles.scss'

export function ApprovalsPage() {
  const { data: pending = [], isLoading } = usePendingMembers()
  const approveMember = useApproveMember()

  if (isLoading) return <p>Carregando pendentes...</p>

  return (
    <div className="approvals-page">
      <h2>Aprovações Pendentes</h2>
      {pending.length === 0 ? (
        <p className="approvals-page__empty">Nenhum cadastro pendente.</p>
      ) : (
        <ul className="approvals-list">
          {pending.map((member) => (
            <li key={member.id} className="approvals-list__item">
              <div>
                <strong>{member.nickname}</strong>
                <br />
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
    </div>
  )
}
