import { useEffect, useMemo, useState } from 'react'
import {
  useAllCatalogAccessories,
  useAllCatalogArmor,
  useAllCatalogSets,
  useCatalogAccessories,
  useCatalogArmor,
} from '@/Domain/Catalog/hooks/useCatalog'
import { civilMaskLabel, CIVIL_MASK_OPTIONS } from '@/Domain/Catalog/utils/civilMaskUtils'
import {
  armorGradeLabel,
  formatEffectDirection,
  gradeColor,
  gradeLabel,
  isTimedEffect,
} from '@/Domain/Catalog/utils/catalogUtils'
import { mergeEffects } from '@/Domain/Catalog/utils/effectUtils'
import {
  buildSetIndex,
  deduplicateComboSets,
  getSetMemberSlots,
  type SetMemberSlot,
} from '@/Domain/Catalog/utils/setIndexUtils'
import type {
  CatalogIconRef,
  GameAccessory,
  GameAccessoryEffect,
  GameArmor,
  ItemSet,
  ItemSetEffect,
} from '@/Domain/types/models'
import { SpriteIcon } from '@/Shared/ui/components/SpriteIcon/SpriteIcon'
import './AccessoryCatalog.styles.scss'

type CatalogMode = 'accessories' | 'armor'
type SubtypeFilter = 'ALL' | 'RING' | 'AMULET'
type SlotFilter = 'ALL' | GameArmor['slot']
type GradeFilter = 'ALL' | string

const ARMOR_SLOTS: GameArmor['slot'][] = ['HELMET', 'UPPER', 'LOWER', 'GAUNTLET', 'SHOES']

const SLOT_LABELS: Record<GameArmor['slot'], string> = {
  HELMET: 'Helmet',
  UPPER: 'Upper',
  LOWER: 'Lower',
  GAUNTLET: 'Gauntlet',
  SHOES: 'Shoes',
}

export function ItemCatalog() {
  const [mode, setMode] = useState<CatalogMode>('accessories')
  const [subtype, setSubtype] = useState<SubtypeFilter>('ALL')
  const [slot, setSlot] = useState<SlotFilter>('ALL')
  const [grade, setGrade] = useState<GradeFilter>('ALL')
  const [civilMask, setCivilMask] = useState('')
  const [search, setSearch] = useState('')
  const [debouncedSearch, setDebouncedSearch] = useState('')
  const [selectedCode, setSelectedCode] = useState<string | null>(null)

  useEffect(() => {
    const timer = window.setTimeout(() => setDebouncedSearch(search.trim()), 300)
    return () => window.clearTimeout(timer)
  }, [search])

  useEffect(() => {
    setSelectedCode(null)
  }, [mode, subtype, slot, grade, civilMask])

  const accessoryFilters = {
    subtype: subtype === 'ALL' ? undefined : subtype,
    grade: grade === 'ALL' ? undefined : Number(grade),
    civilMask: civilMask || undefined,
    search: debouncedSearch || undefined,
  }

  const armorFilters = {
    slot: slot === 'ALL' ? undefined : slot,
    grade: grade === 'ALL' ? undefined : Number(grade),
    civilMask: civilMask || undefined,
    minLevel: 35,
    search: debouncedSearch || undefined,
  }

  const { data: accessories = [], isLoading: loadingAccessories } = useCatalogAccessories(accessoryFilters)
  const { data: armors = [], isLoading: loadingArmor } = useCatalogArmor(armorFilters)
  const { data: allSets = [] } = useAllCatalogSets()
  const { data: allAccessories = [] } = useAllCatalogAccessories()
  const { data: allArmor = [] } = useAllCatalogArmor()

  const setIndex = useMemo(() => buildSetIndex(allSets), [allSets])
  const catalogByCode = useMemo(() => {
    const map = new Map<string, CatalogIconRef>()
    for (const item of allAccessories) {
      map.set(item.gameCode, {
        gameCode: item.gameCode,
        name: item.name,
        iconId: item.iconId,
        spriteSheet: item.spriteSheet,
        spriteCols: 32,
      })
    }
    for (const item of allArmor) {
      map.set(item.gameCode, {
        gameCode: item.gameCode,
        name: item.name,
        iconId: item.iconId,
        spriteSheet: item.spriteSheet,
        spriteCols: item.spriteCols,
      })
    }
    return map
  }, [allAccessories, allArmor])

  const isLoading = mode === 'accessories' ? loadingAccessories : loadingArmor

  return (
    <div className="accessory-catalog">
      <div className="accessory-catalog__mode-tabs">
        <button
          type="button"
          className={`accessory-catalog__mode-btn${mode === 'accessories' ? ' accessory-catalog__mode-btn--active' : ''}`}
          onClick={() => setMode('accessories')}
        >
          Acessórios
        </button>
        <button
          type="button"
          className={`accessory-catalog__mode-btn${mode === 'armor' ? ' accessory-catalog__mode-btn--active' : ''}`}
          onClick={() => setMode('armor')}
        >
          Armaduras
        </button>
      </div>

      <div className="accessory-catalog__filters">
        <input
          type="search"
          className="accessory-catalog__search"
          placeholder="Buscar por nome ou código..."
          value={search}
          onChange={(e) => setSearch(e.target.value)}
        />

        {mode === 'accessories' ? (
          <select
            className="accessory-catalog__select"
            value={subtype}
            onChange={(e) => setSubtype(e.target.value as SubtypeFilter)}
          >
            <option value="ALL">Todos os tipos</option>
            <option value="RING">Ring</option>
            <option value="AMULET">Amulet</option>
          </select>
        ) : (
          <select
            className="accessory-catalog__select"
            value={slot}
            onChange={(e) => setSlot(e.target.value as SlotFilter)}
          >
            <option value="ALL">Todos os slots</option>
            {ARMOR_SLOTS.map((s) => (
              <option key={s} value={s}>{SLOT_LABELS[s]}</option>
            ))}
          </select>
        )}

        <select
          className="accessory-catalog__select"
          value={grade}
          onChange={(e) => setGrade(e.target.value as GradeFilter)}
        >
          <option value="ALL">Todas as qualidades</option>
          <option value="0">Normal</option>
          <option value="4">Relic</option>
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
      ) : mode === 'accessories' ? (
        accessories.length === 0 ? (
          <p className="accessory-catalog__status">Nenhum acessório encontrado.</p>
        ) : (
          <div className="accessory-catalog__grid">
            {accessories.map((accessory) => (
              <AccessoryCard
                key={accessory.gameCode}
                accessory={accessory}
                sets={setIndex.get(accessory.gameCode) ?? []}
                catalogByCode={catalogByCode}
                selected={selectedCode === accessory.gameCode}
                onSelect={() =>
                  setSelectedCode(
                    selectedCode === accessory.gameCode ? null : accessory.gameCode
                  )
                }
              />
            ))}
          </div>
        )
      ) : armors.length === 0 ? (
        <p className="accessory-catalog__status">Nenhuma armadura encontrada (Lv 35+).</p>
      ) : (
        <div className="accessory-catalog__grid">
          {armors.map((armor) => (
            <ArmorCard
              key={armor.gameCode}
              armor={armor}
              sets={setIndex.get(armor.gameCode) ?? []}
              catalogByCode={catalogByCode}
              selected={selectedCode === armor.gameCode}
              onSelect={() =>
                setSelectedCode(
                  selectedCode === armor.gameCode ? null : armor.gameCode
                )
              }
            />
          ))}
        </div>
      )}
    </div>
  )
}

/** @deprecated Use ItemCatalog */
export const AccessoryCatalog = ItemCatalog

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

function EffectLine({ eff }: { eff: GameAccessoryEffect | ItemSetEffect }) {
  const direction = formatEffectDirection(eff.displayType, eff.rawValue)

  if (isTimedEffect(eff.displayType) && direction) {
    const dirClass = `accessory-card__effect-dir accessory-card__effect-dir--${direction.toLowerCase()}`

    if (eff.code === 34) {
      return (
        <span className="accessory-card__effect">
          {eff.name} is{' '}
          <strong>{eff.displayValue}</strong>{' '}
          <span className={dirClass}>{direction}</span>(Sec)
        </span>
      )
    }

    return (
      <span className="accessory-card__effect">
        {eff.name}{' '}
        <strong>{eff.displayValue}</strong>{' '}
        <span className={dirClass}>{direction}</span>(Sec)
      </span>
    )
  }

  return (
    <span className="accessory-card__effect">
      {eff.name}{' '}
      {direction && (
        <span className={`accessory-card__effect-dir accessory-card__effect-dir--${direction.toLowerCase()}`}>
          {direction}
        </span>
      )}{' '}
      <strong>{eff.displayType === 'BOOLEAN' ? 'Yes' : eff.displayValue}</strong>
    </span>
  )
}

function ComboMemberIcon({
  member,
  catalogByCode,
}: {
  member: SetMemberSlot
  catalogByCode: Map<string, CatalogIconRef>
}) {
  const catalogItem = catalogByCode.get(member.gameCode)

  if (catalogItem) {
    return (
      <span className="accessory-card__combo-icon" title={catalogItem.name}>
        <SpriteIcon
          iconId={catalogItem.iconId}
          spriteSheet={catalogItem.spriteSheet}
          spriteCols={catalogItem.spriteCols}
          size={24}
        />
      </span>
    )
  }

  return (
    <span
      className="accessory-card__combo-icon accessory-card__combo-icon--placeholder"
      title={member.gameCode}
    >
      <span className="accessory-card__combo-icon-label">?</span>
    </span>
  )
}

function ComboSetBlock({
  set,
  catalogByCode,
}: {
  set: ItemSet
  catalogByCode: Map<string, CatalogIconRef>
}) {
  const members = getSetMemberSlots(set)
  const mergedEffects = mergeEffects(set.effects)

  if (members.length === 0 && mergedEffects.length === 0) return null

  return (
    <div className="accessory-card__combo-set">
      {members.length > 0 && (
        <div className="accessory-card__combo-items">
          {members.map((member, index) => (
            <ComboMemberIcon
              key={`${set.id}-${member.slot}-${index}`}
              member={member}
              catalogByCode={catalogByCode}
            />
          ))}
        </div>
      )}
      {mergedEffects.map((eff) => (
        <EffectLine key={`${set.id}-${eff.code}-${eff.displayType}`} eff={eff} />
      ))}
    </div>
  )
}

function AccessoryCard({
  accessory,
  sets,
  catalogByCode,
  selected,
  onSelect,
}: {
  accessory: GameAccessory
  sets: ItemSet[]
  catalogByCode: Map<string, CatalogIconRef>
  selected: boolean
  onSelect: () => void
}) {
  const comboSets = deduplicateComboSets(sets.filter((set) => set.effects.length > 0))

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
            {mergeEffects(accessory.effects).map((eff) => (
              <EffectLine key={`${eff.code}-${eff.displayType}`} eff={eff} />
            ))}
          </div>
        )}

        {comboSets.length > 0 && (
          <div className="accessory-card__combo">
            <span className="accessory-card__combo-label">Combo</span>
            {comboSets.map((set) => (
              <ComboSetBlock key={set.id} set={set} catalogByCode={catalogByCode} />
            ))}
          </div>
        )}
      </div>
    </button>
  )
}

function ArmorCard({
  armor,
  sets,
  catalogByCode,
  selected,
  onSelect,
}: {
  armor: GameArmor
  sets: ItemSet[]
  catalogByCode: Map<string, CatalogIconRef>
  selected: boolean
  onSelect: () => void
}) {
  const comboSets = deduplicateComboSets(sets.filter((set) => set.effects.length > 0))
  const gradeName = armorGradeLabel(armor.grade)

  return (
    <button
      type="button"
      className={`accessory-card${selected ? ' accessory-card--selected' : ''}`}
      onClick={onSelect}
    >
      <div className="accessory-card__header">
        <div className="accessory-card__icon">
          <SpriteIcon
            iconId={armor.iconId}
            spriteSheet={armor.spriteSheet}
            spriteCols={armor.spriteCols}
            size={36}
          />
        </div>
        <div className="accessory-card__title-group">
          <span className="accessory-card__name" style={{ color: gradeColor(armor.grade) }}>
            {armor.name}
          </span>
          <div className="accessory-card__tags">
            <span className="accessory-card__tag">{SLOT_LABELS[armor.slot]}</span>
            <span className="accessory-card__tag accessory-card__tag--grade" style={{ color: gradeColor(armor.grade) }}>
              {gradeName}
            </span>
            <span className="accessory-card__tag">Lv {armor.levelRequired}</span>
          </div>
        </div>
      </div>

      <div className="accessory-card__body">
        <span className="accessory-card__race">{civilMaskLabel(armor.civilMask)}</span>

        <div className="accessory-card__def-stats">
          <span className="accessory-card__def-stat">
            DefFc <strong>{armor.defFc}</strong>
          </span>
          {armor.defFacingDisplay != null && (
            <span className="accessory-card__def-stat">
              DSR <strong>{armor.defFacingDisplay}</strong>
            </span>
          )}
        </div>

        {armor.effects.length > 0 && (
          <div className="accessory-card__effects">
            {mergeEffects(armor.effects).map((eff) => (
              <EffectLine key={`${eff.code}-${eff.displayType}`} eff={eff} />
            ))}
          </div>
        )}

        {comboSets.length > 0 && (
          <div className="accessory-card__combo">
            <span className="accessory-card__combo-label">Combo</span>
            {comboSets.map((set) => (
              <ComboSetBlock key={set.id} set={set} catalogByCode={catalogByCode} />
            ))}
          </div>
        )}
      </div>
    </button>
  )
}
