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
import { Panel } from '@/Shared/ui/components/Panel/Panel'
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
      {isAdmin && (
        <Panel title="Novo objetivo" variant="amber" code="ADM">
          <form className="objectives-form" onSubmit={handleCreate}>
            <label className="objectives-form__field objectives-form__field--name">
              <span>Nome</span>
              <input
                id="objective-name"
                placeholder="Ex.: Boss semanal"
                value={name}
                onChange={(e) => setName(e.target.value)}
                required
              />
            </label>
            <label className="objectives-form__field objectives-form__field--points">
              <span>Pontos</span>
              <input
                id="objective-points"
                type="number"
                min={1}
                value={points}
                onChange={(e) => setPoints(Number(e.target.value))}
              />
            </label>
            <label className="objectives-form__field objectives-form__field--type">
              <span>Tipo</span>
              <select
                id="objective-type"
                value={type}
                onChange={(e) => setType(e.target.value as ObjectiveType)}
              >
                <option value="NORMAL">Normal</option>
                <option value="LIMITADO">Limitado</option>
              </select>
            </label>
            {type === 'LIMITADO' && (
              <label className="objectives-form__field objectives-form__field--limit">
                <span>Limite/dia</span>
                <input
                  id="objective-daily-limit"
                  type="number"
                  min={1}
                  value={dailyLimit}
                  onChange={(e) => setDailyLimit(Number(e.target.value))}
                />
              </label>
            )}
            <Button type="submit" loading={createObjective.isPending}>
              Criar
            </Button>
          </form>
        </Panel>
      )}

      <Panel title="Objetivos" code={`${objectives.length} OBJ`} flush>
        {objectives.length === 0 && (
          <p className="objectives-list__empty">Nenhum objetivo cadastrado.</p>
        )}
        <ul className="objectives-list">
          {objectives.map((obj) => (
            <li key={obj.id} className="objectives-list__item">
              <span className="objectives-list__pts">{obj.points}</span>
              <div className="objectives-list__info">
                <strong>{obj.name}</strong>
                <span
                  className={`objectives-list__type objectives-list__type--${obj.type.toLowerCase()}`}
                >
                  {obj.type}
                  {obj.dailyLimit ? ` · ${obj.dailyLimit}/dia` : ''}
                </span>
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
      </Panel>
    </div>
  )
}
