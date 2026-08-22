import { useEffect, useState } from 'react'
import {
  useCatalogAccessories,
} from '@/Domain/Catalog/hooks/useCatalog'
import { civilMaskLabel, CIVIL_MASK_OPTIONS } from '@/Domain/Catalog/utils/civilMaskUtils'
import {
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
      )}
    </div>
  )
}

function ElementBadges({ fire, water, soil, wind }: { fire: number; water: number; soil: number; wind: number }) {
  const elements = [
    { label: 'Fire', value: fire, cls: 'fire' },
    { label: 'Water', value: water, cls: 'water' },
    { label: 'Soil', value: soil, cls: 'soil' },
    { label: 'Wind', value: wind, cls: 'wind' },
  ].filter((e) => e.value > 0)

  if (elements.length === 0) return null

  return (
    <div className="accessory-card__elements">
      {elements.map((el) => (
        <span key={el.cls} className={`accessory-card__element accessory-card__element--${el.cls}`}>
          {el.label} {el.value}
        </span>
      ))}
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
  return (
    <button
      type="button"
      className={`accessory-card${selected ? ' accessory-card--selected' : ''}`}
      onClick={onSelect}
    >
      <div className="accessory-card__header">
        <div className="accessory-card__icon">
          <SpriteIcon iconId={accessory.iconId} spriteSheet={accessory.spriteSheet} size={36} />
        </div>
        <div className="accessory-card__title-group">
          <span className="accessory-card__name" style={{ color: gradeColor(accessory.grade) }}>
            {accessory.name}
          </span>
          <div className="accessory-card__tags">
            <span className="accessory-card__tag">{accessory.subtype}</span>
            <span className="accessory-card__tag accessory-card__tag--grade" style={{ color: gradeColor(accessory.grade) }}>
              {gradeLabel(accessory.grade)}
            </span>
            <span className="accessory-card__tag">Lv {accessory.levelRequired}</span>
          </div>
        </div>
      </div>

      <div className="accessory-card__body">
        <span className="accessory-card__race">{civilMaskLabel(accessory.civilMask)}</span>

        <ElementBadges
          fire={accessory.fire}
          water={accessory.water}
          soil={accessory.soil}
          wind={accessory.wind}
        />

        {accessory.effects.length > 0 && (
          <div className="accessory-card__effects">
            {accessory.effects.map((eff) => {
              const direction =
                eff.displayType === 'BOOLEAN'
                  ? null
                  : eff.rawValue != null && eff.rawValue < 0
                    ? 'Decrease'
                    : 'Increase'

              return (
                <span key={eff.code} className="accessory-card__effect">
                  {eff.name}{' '}
                  {direction && (
                    <span className={`accessory-card__effect-dir accessory-card__effect-dir--${direction.toLowerCase()}`}>
                      {direction}
                    </span>
                  )}{' '}
                  <strong>{eff.displayType === 'BOOLEAN' ? 'Yes' : eff.displayValue}</strong>
                </span>
              )
            })}
          </div>
        )}
      </div>
    </button>
  )
}
