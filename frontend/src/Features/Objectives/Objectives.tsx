import { useState } from 'react'
import {
  useCreateObjective,
  useDeleteObjective,
  useObjectives,
  useUpdateObjective,
} from '@/Domain/Objective/hooks/useObjectives'
import { useAuthContext } from '@/Features/Auth/contexts/AuthContext'
import type { ObjectiveType } from '@/Domain/types/models'
import { Button } from '@/Shared/ui/components/Button/Button'
import './Objectives.styles.scss'

export function ObjectivesPage() {
  const { data: objectives = [], isLoading } = useObjectives()
  const createObjective = useCreateObjective()
  const updateObjective = useUpdateObjective()
  const deleteObjective = useDeleteObjective()
  const { hasRole } = useAuthContext()
  const isAdmin = hasRole('ADMINISTRADOR')

  const [name, setName] = useState('')
  const [points, setPoints] = useState(10)
  const [type, setType] = useState<ObjectiveType>('NORMAL')
  const [dailyLimit, setDailyLimit] = useState(1)

  const handleCreate = async (e: React.FormEvent) => {
    e.preventDefault()
    await createObjective.mutateAsync({
      name,
      points,
      type,
      dailyLimit: type === 'LIMITADO' ? dailyLimit : undefined,
    })
    setName('')
  }

  if (isLoading) return <p>Carregando objetivos...</p>

  return (
    <div className="objectives-page">
      <h2>Objetivos</h2>

      {isAdmin && (
        <form className="objectives-form" onSubmit={handleCreate}>
          <input
            placeholder="Nome"
            value={name}
            onChange={(e) => setName(e.target.value)}
            required
          />
          <input
            type="number"
            min={1}
            value={points}
            onChange={(e) => setPoints(Number(e.target.value))}
          />
          <select value={type} onChange={(e) => setType(e.target.value as ObjectiveType)}>
            <option value="NORMAL">Normal</option>
            <option value="LIMITADO">Limitado</option>
          </select>
          {type === 'LIMITADO' && (
            <input
              type="number"
              min={1}
              value={dailyLimit}
              onChange={(e) => setDailyLimit(Number(e.target.value))}
              placeholder="Limite/dia"
            />
          )}
          <Button type="submit" loading={createObjective.isPending}>
            Criar
          </Button>
        </form>
      )}

      <ul className="objectives-list">
        {objectives.map((obj) => (
          <li key={obj.id} className="objectives-list__item">
            <div>
              <strong>{obj.name}</strong> — {obj.points} pts
              <br />
              <small>
                {obj.type}
                {obj.dailyLimit ? ` (${obj.dailyLimit}/dia)` : ''}
              </small>
            </div>
            {isAdmin && (
              <div className="objectives-list__actions">
                <Button
                  variant="secondary"
                  size="sm"
                  onClick={() =>
                    updateObjective.mutate({
                      id: obj.id,
                      name: obj.name,
                      points: obj.points + 5,
                      type: obj.type,
                      dailyLimit: obj.dailyLimit ?? undefined,
                    })
                  }
                >
                  +5 pts
                </Button>
                <Button
                  variant="danger"
                  size="sm"
                  onClick={() => deleteObjective.mutate(obj.id)}
                >
                  Excluir
                </Button>
              </div>
            )}
          </li>
        ))}
      </ul>
    </div>
  )
}
