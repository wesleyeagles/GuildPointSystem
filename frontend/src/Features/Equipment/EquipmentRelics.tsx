import { useMemo } from 'react'
import { useEquipmentRelics, useEquipmentRelicChange } from '@/Domain/Equipment/hooks/useEquipment'
import relicGroups from '@/Domain/Equipment/relics.json'
import { formatRelicEffect, relicEffectTotals, relicLevelEffects, relicName } from '@/Domain/Equipment/utils/relicEffects'
import { SCHEDULE_TIME_ZONE } from '@/Shared/utils/scheduleTimeZone'
import './EquipmentRelics.styles.scss'

type RelicGroup = { grade: number; label?: string; hidden?: boolean; items: { id: number; position: string }[] }

/** Mesmas abas do jogo (I a VI, Technical). Event / OLD fica de fora: no jogo também é só espaço vazio. */
const groups = (relicGroups as RelicGroup[]).filter(group => !group.hidden)
const groupName = (group: RelicGroup) => group.label ?? `Grade ${group.grade}`

function relicTitle(id: number, fallback: string, upgrade: number | undefined) {
  const name = relicName(id) ?? fallback
  if (upgrade === undefined) return `${name} · Não possui`
  const lines = relicLevelEffects(id, upgrade).map(([code, value]) => formatRelicEffect(code, value))
  return [`${name}${upgrade > 0 ? ` +${upgrade}` : ''}`, ...lines].join('\n')
}

export function EquipmentRelics({ memberId, editable }: { memberId: number; editable: boolean }) {
  const { data, isLoading, error, refetch } = useEquipmentRelics(memberId)
  const change = useEquipmentRelicChange(memberId)
  const owned = useMemo(() => new Map((data?.relics ?? []).map(r => [r.id, r.upgrade])), [data])
  const totals = useMemo(() => relicEffectTotals(data?.relics ?? []), [data])
  return <section className="equipment-relics" aria-label="Relics">
    <h3>Relics</h3>
    {data?.syncedAt
      ? <p className="equipment-relics__sync">Sincronizado pelo jogo · {new Date(data.syncedAt).toLocaleString('pt-BR', { timeZone: SCHEDULE_TIME_ZONE })}</p>
      : !isLoading && !error && <p className="equipment-relics__sync">Ainda não sincronizado pelo jogo. Abra a janela Relics no jogo com a DLL ativa.</p>}
    {isLoading && <p role="status">Carregando Relics…</p>}
    {error && <p role="alert">{error.message} <button type="button" onClick={() => void refetch()}>Tentar novamente</button></p>}
    {change.error && <p role="alert">Não foi possível salvar a Relic. Tente novamente.</p>}
    {totals.length > 0 && <section className="equipment-relics__effects" aria-label="Efeitos atuais">
      <h4>Efeitos atuais</h4>
      <ul>{totals.map(t => <li key={t.code}>{t.text}</li>)}</ul>
    </section>}
    <div className="equipment-relics__grades">{groups.map(group => <section key={group.grade} className="equipment-relics__grade" aria-label={groupName(group)}>
      <h4>{groupName(group)}</h4>
      <div className="equipment-relics__list">{group.items.map((item, index) => {
        const upgrade = owned.get(item.id)
        const active = upgrade !== undefined
        const label = `Relic ${index + 1} · ${groupName(group)}`
        return <button key={item.id} type="button"
          className={`equipment-relics__icon${active ? ' is-active' : ''}`}
          style={{ backgroundPosition: item.position }} aria-label={label}
          aria-pressed={active} title={relicTitle(item.id, label, upgrade)}
          disabled={!editable || isLoading || !!error || change.isPending}
          onClick={() => change.mutate({ relicId: item.id, active: !active })}>
          {upgrade !== undefined && upgrade > 0 && <span className="equipment-relics__upgrade" aria-hidden="true">+{upgrade}</span>}
        </button>
      })}</div>
    </section>)}</div>
  </section>
}
