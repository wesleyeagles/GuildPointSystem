import { useCurrentMember } from '@/Domain/Member/hooks/useMembers'
import { useEquipmentRelics } from '@/Domain/Equipment/hooks/useEquipment'
import { Panel } from '@/Shared/ui/components/Panel/Panel'
import { Button } from '@/Shared/ui/components/Button/Button'
import type { DamageMode, WeaponClass } from './DamageCalc.utils'
import { DAMAGE_RELICS, relicBonus, relicDamageText, type DamageRelic } from './relics'

const ICON = 40
/** A folha /relics/item-1-6.jpg tem células de 64 px (2048×4096). */
const SCALE = ICON / 64
const scaled = (position: string) => position.split(' ').map(p => `${parseFloat(p) * SCALE}px`).join(' ')

const groups = [...new Set(DAMAGE_RELICS.map(r => r.group))]

function RelicTile({ relic, upgrade, onChange, weaponClass, mode }: {
  relic: DamageRelic
  /** undefined = não tem */
  upgrade: number | undefined
  onChange: (upgrade: number | undefined) => void
  weaponClass: WeaponClass | undefined
  mode: DamageMode
}) {
  const owned = upgrade !== undefined
  const bonus = relicBonus(relic.id, upgrade ?? 0, weaponClass, mode)
  const off = owned && weaponClass !== undefined && bonus.atk === 0 && bonus.crit === 0
  return (
    <div className={`damage-calc__relic${owned ? ' is-owned' : ''}${off ? ' damage-calc__relic--off' : ''}`}
      title={off ? 'Não soma com esta arma / tipo de dano' : undefined}>
      <label className="damage-calc__relic-main">
        <input type="checkbox" checked={owned} onChange={() => onChange(owned ? undefined : 0)} />
        <span className="damage-calc__relic-icon" aria-hidden="true" style={{
          backgroundSize: `${2048 * SCALE}px ${4096 * SCALE}px`, backgroundPosition: scaled(relic.position),
        }} />
        <span className="damage-calc__relic-name">
          {relic.name}
          <small>{relicDamageText(relic.id, upgrade ?? 0)}</small>
        </span>
      </label>
      {owned && (
        <select aria-label={`Upgrade de ${relic.name}`} value={upgrade} onChange={e => onChange(Number(e.target.value))}>
          {[0, 1, 2, 3].map(u => <option key={u} value={u}>{u === 0 ? 'Base' : `+${u}`}</option>)}
        </select>
      )}
    </div>
  )
}

/** Relics que dão dano: escolha manual ou as sincronizadas do personagem pela DLL. */
export function RelicsPanel({ value, onChange, weaponClass, mode }: {
  value: Record<string, number>
  onChange: (relics: Record<string, number>) => void
  weaponClass: WeaponClass | undefined
  mode: DamageMode
}) {
  const me = useCurrentMember().data
  const mine = useEquipmentRelics(me?.id ?? 0).data
  const myDamageRelics = (mine?.relics ?? []).filter(r => DAMAGE_RELICS.some(d => d.id === r.id))
  const set = (id: number, upgrade: number | undefined) => {
    const next = { ...value }
    if (upgrade === undefined) delete next[id]
    else next[id] = upgrade
    onChange(next)
  }
  return (
    <Panel title="Relics" actions={<>
      {myDamageRelics.length > 0 && (
        <Button size="sm" variant="ghost" title="As relics que a DLL sincronizou do seu personagem"
          onClick={() => onChange(Object.fromEntries(myDamageRelics.map(r => [r.id, r.upgrade])))}>
          Usar minhas relics
        </Button>
      )}
      {Object.keys(value).length > 0 && <Button size="sm" variant="ghost" onClick={() => onChange({})}>Limpar</Button>}
    </>}>
      <p className="damage-calc__hint">Só as que dão dano. Melee, Range e Launcher valem só para a arma dessa classe; Força, só no dano de força.</p>
      {groups.map(group => (
        <section key={group} className="damage-calc__relic-group" aria-label={`Relics ${group}`}>
          <h4>{group}</h4>
          <div className="damage-calc__relics">
            {DAMAGE_RELICS.filter(r => r.group === group).map(r => (
              <RelicTile key={r.id} relic={r} upgrade={value[r.id]} onChange={u => set(r.id, u)} weaponClass={weaponClass} mode={mode} />
            ))}
          </div>
        </section>
      ))}
    </Panel>
  )
}
