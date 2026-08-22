import { useEffect, useState } from 'react'
import {
  useCatalogAccessories,
  useCatalogSets,
} from '@/Domain/Catalog/hooks/useCatalog'
import { civilMaskLabel, CIVIL_MASK_OPTIONS } from '@/Domain/Catalog/utils/civilMaskUtils'
import {
  elementSummary,
  gradeColor,
  gradeLabel,
} from '@/Domain/Catalog/utils/catalogUtils'
import type { GameAccessory } from '@/Domain/types/models'
import { SpriteIcon } from '@/Shared/ui/components/SpriteIcon/SpriteIcon'
import './AccessoryCatalog.styles.scss'

type SubtypeFilter = 'ALL' | 'RING' | 'AMULET'
type GradeFilter = 'ALL' | '0' | '7'

export function AccessoryCatalog() {
  const [subtype, setSubtype] = useState<SubtypeFilter>('ALL')
  const [grade, setGrade] = useState<GradeFilter>('ALL')
  const [civilMask, setCivilMask] = useState('')
  const [search, setSearch] = useState('')
  const [debouncedSearch, setDebouncedSearch] = useState('')
  const [selectedCode, setSelectedCode] = useState<string | null>(null)

  useEffect(() => {
    const timer = window.setTimeout(() => setDebouncedSearch(search.trim()), 300)
    return () => window.clearTimeout(timer)
  }, [search])

  const filters = {
    subtype: subtype === 'ALL' ? undefined : subtype,
    grade: grade === 'ALL' ? undefined : Number(grade),
    civilMask: civilMask || undefined,
    search: debouncedSearch || undefined,
  }

  const { data: accessories = [], isLoading } = useCatalogAccessories(filters)
  const { data: sets = [] } = useCatalogSets(selectedCode)

  const selected = accessories.find((a) => a.gameCode === selectedCode) ?? null

  return (
    <div className="accessory-catalog">
      <div className="accessory-catalog__filters">
        <input
          type="search"
          className="accessory-catalog__search"
          placeholder="Buscar por nome ou código..."
          value={search}
          onChange={(e) => setSearch(e.target.value)}
        />

        <select
          className="accessory-catalog__select"
          value={subtype}
          onChange={(e) => setSubtype(e.target.value as SubtypeFilter)}
        >
          <option value="ALL">Todos os tipos</option>
          <option value="RING">Ring</option>
          <option value="AMULET">Amulet</option>
        </select>

        <select
          className="accessory-catalog__select"
          value={grade}
          onChange={(e) => setGrade(e.target.value as GradeFilter)}
        >
          <option value="ALL">Todas as qualidades</option>
          <option value="0">Normal</option>
          <option value="7">Hero</option>
        </select>

        <select
          className="accessory-catalog__select"
          value={civilMask}
          onChange={(e) => setCivilMask(e.target.value)}
        >
          <option value="">Todas as raças</option>
          {CIVIL_MASK_OPTIONS.map((opt) => (
            <option key={opt.value} value={opt.value}>{opt.label}</option>
          ))}
        </select>
      </div>

      {isLoading ? (
        <p className="accessory-catalog__status">Carregando catálogo...</p>
      ) : accessories.length === 0 ? (
        <p className="accessory-catalog__status">Nenhum acessório encontrado.</p>
      ) : (
        <div className="accessory-catalog__layout">
          <div className="accessory-catalog__grid">
            {accessories.map((accessory) => (
              <AccessoryCard
                key={accessory.gameCode}
                accessory={accessory}
                selected={selectedCode === accessory.gameCode}
                onSelect={() =>
                  setSelectedCode(
                    selectedCode === accessory.gameCode ? null : accessory.gameCode
                  )
                }
              />
            ))}
          </div>

          {selected && (
            <aside className="accessory-catalog__detail">
              <AccessoryDetail accessory={selected} sets={sets} />
            </aside>
          )}
        </div>
      )}
    </div>
  )
}

function AccessoryCard({
  accessory,
  selected,
  onSelect,
}: {
  accessory: GameAccessory
  selected: boolean
  onSelect: () => void
}) {
  const elements = elementSummary(
    accessory.fire,
    accessory.water,
    accessory.soil,
    accessory.wind
  )

  return (
    <button
      type="button"
      className={`accessory-card${selected ? ' accessory-card--selected' : ''}`}
      onClick={onSelect}
    >
      <SpriteIcon iconId={accessory.iconId} spriteSheet={accessory.spriteSheet} size={48} />
      <div className="accessory-card__body">
        <span className="accessory-card__name" style={{ color: gradeColor(accessory.grade) }}>
          {accessory.name}
        </span>
        <span className="accessory-card__meta">
          {accessory.subtype} · {gradeLabel(accessory.grade)} · Lv {accessory.levelRequired}
        </span>
        <span className="accessory-card__meta">{civilMaskLabel(accessory.civilMask)}</span>
        {elements && <span className="accessory-card__elements">{elements}</span>}
        {accessory.effects.length > 0 && (
          <ul className="accessory-card__effects">
            {accessory.effects.map((eff) => (
              <li key={eff.code}>
                {eff.name}: {eff.displayType === 'BOOLEAN' ? 'Yes' : eff.displayValue}
              </li>
            ))}
          </ul>
        )}
      </div>
    </button>
  )
}

function AccessoryDetail({
  accessory,
  sets,
}: {
  accessory: GameAccessory
  sets: import('@/Domain/types/models').ItemSet[]
}) {
  const elements = elementSummary(
    accessory.fire,
    accessory.water,
    accessory.soil,
    accessory.wind
  )

  return (
    <div className="accessory-detail">
      <div className="accessory-detail__header">
        <SpriteIcon iconId={accessory.iconId} spriteSheet={accessory.spriteSheet} size={64} />
        <div>
          <h3 style={{ color: gradeColor(accessory.grade) }}>{accessory.name}</h3>
          <p className="accessory-detail__code">{accessory.gameCode}</p>
        </div>
      </div>

      <dl className="accessory-detail__stats">
        <div><dt>Tipo</dt><dd>{accessory.subtype}</dd></div>
        <div><dt>Qualidade</dt><dd style={{ color: gradeColor(accessory.grade) }}>{gradeLabel(accessory.grade)}</dd></div>
        <div><dt>Raça</dt><dd>{civilMaskLabel(accessory.civilMask)}</dd></div>
        <div><dt>Level</dt><dd>{accessory.levelRequired}</dd></div>
        {elements && <div><dt>Elementos</dt><dd>{elements}</dd></div>}
      </dl>

      {accessory.effects.length > 0 && (
        <section className="accessory-detail__section">
          <h4>Efeitos</h4>
          <ul>
            {accessory.effects.map((eff) => (
              <li key={eff.code}>
                {eff.name}: {eff.displayType === 'BOOLEAN' ? 'Yes' : eff.displayValue}
              </li>
            ))}
          </ul>
        </section>
      )}

      {sets.length > 0 && (
        <section className="accessory-detail__section">
          <h4>Set Bonus</h4>
          {sets.map((set) => (
            <div key={set.id} className="accessory-detail__set">
              <p className="accessory-detail__set-code">{set.setCode}</p>
              <ul>
                {set.effects.map((eff) => (
                  <li key={`${set.id}-${eff.code}`}>
                    {eff.name}: {eff.displayType === 'BOOLEAN' ? 'Yes' : eff.displayValue}
                  </li>
                ))}
              </ul>
            </div>
          ))}
        </section>
      )}
    </div>
  )
}
