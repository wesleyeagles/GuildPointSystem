import { Fragment, useEffect, useId, useLayoutEffect, useMemo, useRef, useState, type CSSProperties } from 'react'
import { createPortal } from 'react-dom'
import { Panel } from '@/Shared/ui/components/Panel/Panel'
import { Button } from '@/Shared/ui/components/Button/Button'
import { ADRENAS, BUFFS, GENERATORS, KEEN_PCT, type Boost } from './data/boosts'
import { calcDamage, defaultMode, type DamageMode, type Effects, type KbAccessory, type KbData, type KbWeapon } from './DamageCalc.utils'
import { GameIconView, KbIcon } from './KbIcon'
import { RelicsPanel } from './RelicsPanel'
import { DAMAGE_RELICS, relicBonus } from './relics'
import './DamageCalc.styles.scss'

const STORAGE_KEY = 'damageCalc.v1'

const WEAPON_TYPES: Record<string, string> = {
  sword: 'Espada', axe: 'Machado', mace: 'Maça', spear: 'Lança', knife: 'Faca',
  bow: 'Arco', firearm: 'Arma de fogo', throwing: 'Arremesso', launcher: 'Lançador',
  grenade: 'Lança-granadas', staff: 'Cajado', other: 'Outras',
}
const ACC_SLOTS = [
  { key: 'amulet1', label: 'Amuleto 1', list: 'amulets' },
  { key: 'amulet2', label: 'Amuleto 2', list: 'amulets' },
  { key: 'ring1', label: 'Anel 1', list: 'rings' },
  { key: 'ring2', label: 'Anel 2', list: 'rings' },
] as const

interface SavedState {
  weaponType: string
  weapon: string
  mode: DamageMode
  keen: number
  acc: Record<string, string>
  buffs: string[]
  adrena: string
  generator: string
  /** id da relic → upgrade (0 = base). */
  relics: Record<string, number>
  extraAtk: number
}

const INITIAL: SavedState = {
  weaponType: 'sword', weapon: '', mode: 'atk', keen: 0, acc: {}, buffs: [], adrena: '', generator: '', relics: {}, extraAtk: 0,
}

function loadState(): SavedState {
  try {
    const saved = { ...INITIAL, ...JSON.parse(localStorage.getItem(STORAGE_KEY) ?? '{}') } as SavedState
    // buffs/adrenas que saíram da lista (só Cora agora) não podem continuar somando
    return {
      ...saved,
      buffs: saved.buffs.filter(id => BUFFS.some(b => b.id === id)),
      adrena: ADRENAS.some(a => a.id === saved.adrena) ? saved.adrena : '',
      generator: GENERATORS.some(g => g.id === saved.generator) ? saved.generator : '',
      relics: Object.fromEntries(Object.entries(saved.relics ?? {})
        .filter(([id, up]) => DAMAGE_RELICS.some(r => String(r.id) === id) && Number.isInteger(up) && up >= 0 && up <= 3)),
    }
  } catch {
    return INITIAL
  }
}

function useKbase() {
  const [data, setData] = useState<KbData | null>(null)
  useEffect(() => {
    let alive = true
    void import('./data/kbase.json').then(m => { if (alive) setData(m.default as KbData) })
    return () => { alive = false }
  }, [])
  return data
}

const fmt = (n: number) => n.toLocaleString('pt-BR')

function effectsLabel(e?: Effects): string {
  if (!e) return ''
  return [e.a && `Atk ${e.a}%`, e.f && `Força ${e.f}%`, e.c && `Crít ${e.c}`].filter(Boolean).join(' · ')
}

const PICKER_LIMIT = 150

/** Select com busca: fechado mostra o item escolhido; ao clicar abre a lista e o campo vira a busca. */
function ItemPicker<T extends { c: string; n: string; l: number; i?: number; g?: number }>({ label, items, value, onChange, describe }: {
  label: string
  items: T[]
  value: string
  onChange: (code: string) => void
  describe: (item: T) => string
}) {
  const [query, setQuery] = useState('')
  const [open, setOpen] = useState(false)
  const root = useRef<HTMLDivElement>(null)
  const list = useRef<HTMLUListElement>(null)
  const [place, setPlace] = useState<CSSProperties>({})
  const listId = useId()
  const current = items.find(i => i.c === value)
  const hits = useMemo(() => {
    const q = query.trim().toLowerCase()
    return q ? items.filter(i => i.n.toLowerCase().includes(q)) : items
  }, [items, query])
  const close = () => { setOpen(false); setQuery('') }
  const pick = (code: string) => { onChange(code); close() }

  useEffect(() => {
    if (!open) return
    const onDown = (e: MouseEvent) => {
      const t = e.target as Node
      if (!root.current?.contains(t) && !list.current?.contains(t)) { setOpen(false); setQuery('') }
    }
    document.addEventListener('mousedown', onDown)
    return () => document.removeEventListener('mousedown', onDown)
  }, [open])

  // A lista vai para o body (o Panel tem clip-path e cortaria); fica colada no campo, e sobe se faltar espaço embaixo.
  useLayoutEffect(() => {
    if (!open) return
    const update = () => {
      const r = root.current?.querySelector('.damage-calc__combo')?.getBoundingClientRect()
      if (!r) return
      const below = window.innerHeight - r.bottom - 8
      const up = below < 240 && r.top > below
      const maxHeight = Math.min(320, (up ? r.top : below) - 8)
      setPlace({ left: r.left, width: r.width, maxHeight, ...(up ? { bottom: window.innerHeight - r.top + 2 } : { top: r.bottom + 2 }) })
    }
    update()
    window.addEventListener('resize', update)
    window.addEventListener('scroll', update, true)
    return () => { window.removeEventListener('resize', update); window.removeEventListener('scroll', update, true) }
  }, [open])

  const focusOption = (from: EventTarget, step: 1 | -1) => {
    const opts = [...(list.current?.querySelectorAll<HTMLLIElement>('[role="option"]') ?? [])]
    const at = opts.indexOf(from as HTMLLIElement)
    if (at + step < 0) root.current?.querySelector('input')?.focus()
    else opts[Math.min(opts.length - 1, at + step)]?.focus()
  }

  return (
    <div className="damage-calc__picker" ref={root}>
      <span className="damage-calc__picker-label">{label}</span>
      <div className={`damage-calc__combo${open ? ' damage-calc__combo--open' : ''}`}>
        {current && !open && <KbIcon icon={current.i} grade={current.g} size={28} />}
        <input
          role="combobox"
          aria-label={label}
          aria-expanded={open}
          aria-controls={listId}
          autoComplete="off"
          placeholder={open ? 'Buscar pelo nome…' : 'Escolher…'}
          value={open ? query : current?.n ?? ''}
          onFocus={() => setOpen(true)}
          onClick={() => setOpen(true)}
          onChange={e => { setQuery(e.target.value); setOpen(true) }}
          onKeyDown={e => {
            if (e.key === 'Escape') { close(); e.currentTarget.blur() }
            else if (e.key === 'ArrowDown') { e.preventDefault(); focusOption(e.currentTarget, 1) }
            else if (e.key === 'Enter' && open && hits[0]) { e.preventDefault(); pick(hits[0].c) }
          }}
        />
        {current && <button type="button" className="damage-calc__combo-clear" aria-label={`Remover ${label}`} onClick={() => pick('')}>✕</button>}
        <span className="damage-calc__combo-arrow" aria-hidden="true">▾</span>
      </div>
      {current && !open && <small className="damage-calc__combo-detail">{describe(current)}</small>}
      {open && createPortal(
        <ul id={listId} ref={list} className="damage-calc__options" style={place} role="listbox" aria-label={label}>
          {hits.slice(0, PICKER_LIMIT).map(i => (
            <li key={i.c} role="option" aria-selected={i.c === value} tabIndex={-1}
              onClick={() => pick(i.c)}
              onKeyDown={e => {
                if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); pick(i.c) }
                else if (e.key === 'ArrowDown' || e.key === 'ArrowUp') { e.preventDefault(); focusOption(e.currentTarget, e.key === 'ArrowDown' ? 1 : -1) }
                else if (e.key === 'Escape') close()
              }}>
              <KbIcon icon={i.i} grade={i.g} size={32} />
              <span>{i.n}</span>
              <small>{describe(i)}</small>
            </li>
          ))}
          {!hits.length && <li className="damage-calc__options-note">Nada encontrado.</li>}
          {hits.length > PICKER_LIMIT && <li className="damage-calc__options-note">Mostrando {PICKER_LIMIT} de {hits.length}. Digite para filtrar.</li>}
        </ul>,
        document.body,
      )}
    </div>
  )
}

const boostValue = (b: Boost) => [b.atk && `+${b.atk}%`, b.crit && `crít ${b.crit}`].filter(Boolean).join(' · ')

/** Lista de buffs (checkbox) ou de adrenas (radio, uma por vez). */
function BoostList({ items, selected, toggle, name, single = false, mode }: {
  items: Boost[]
  selected: string[]
  toggle: (id: string) => void
  name: string
  single?: boolean
  /** Apaga os buffs que não valem para esse tipo de dano. */
  mode?: DamageMode
}) {
  return (
    <div className="damage-calc__boosts" role={single ? 'radiogroup' : 'group'} aria-label={name}>
      {items.map(b => (
        <label key={b.id} className={`damage-calc__check${mode && b.only && b.only !== mode ? ' damage-calc__check--off' : ''}`}
          title={mode && b.only && b.only !== mode ? `Não soma no dano de ${mode === 'force' ? 'força' : 'ataque'}` : undefined}>
          <input type={single ? 'radio' : 'checkbox'} name={name} checked={selected.includes(b.id)} onChange={() => toggle(b.id)}
            onClick={() => { if (single && selected.includes(b.id)) toggle('') }} />
          <GameIconView icon={b.icon} size={32} />
          <span className="damage-calc__check-name">{b.name}{b.source && <em>{b.source}</em>}</span>
          <small>{boostValue(b)}</small>
        </label>
      ))}
    </div>
  )
}

export function DamageCalcPage() {
  const kb = useKbase()
  const [s, setS] = useState<SavedState>(loadState)
  const set = (patch: Partial<SavedState>) => setS(prev => ({ ...prev, ...patch }))
  useEffect(() => { localStorage.setItem(STORAGE_KEY, JSON.stringify(s)) }, [s])

  const weapons = useMemo(() => (kb?.weapons ?? [])
    .filter(w => w.t === s.weaponType)
    .sort((a, b) => b.l - a.l || a.n.localeCompare(b.n)), [kb, s.weaponType])
  const byCode = useMemo(() => {
    const m = new Map<string, KbWeapon | KbAccessory>()
    kb?.weapons.forEach(w => m.set(w.c, w))
    kb?.amulets.forEach(a => m.set(a.c, a))
    kb?.rings.forEach(r => m.set(r.c, r))
    return m
  }, [kb])
  const accLists = useMemo(() => ({
    amulets: [...(kb?.amulets ?? [])].sort((a, b) => (b.e.a ?? 0) - (a.e.a ?? 0) || a.n.localeCompare(b.n)),
    rings: [...(kb?.rings ?? [])].sort((a, b) => (b.e.a ?? 0) - (a.e.a ?? 0) || a.n.localeCompare(b.n)),
  }), [kb])

  const weapon = (byCode.get(s.weapon) as KbWeapon | undefined) ?? null
  const accessories = ACC_SLOTS.map(slot => byCode.get(s.acc[slot.key] ?? '') as KbAccessory | undefined)
  // Rage Range só vale no ataque físico; Adept só na força
  const buffs = BUFFS.filter(b => s.buffs.includes(b.id) && (!b.only || b.only === s.mode))
  const adrena = ADRENAS.find(a => a.id === s.adrena)
  const adrenaPct = adrena?.atk ?? 0
  const generator = GENERATORS.find(g => g.id === s.generator)
  const hasBoth = !!(weapon?.atk && weapon?.fo)
  const relics = Object.entries(s.relics).map(([id, up]) => relicBonus(Number(id), up, weapon?.k, s.mode))
  const relicAtk = Math.round(relics.reduce((t, r) => t + r.atk, 0) * 100) / 100
  const relicCrit = relics.reduce((t, r) => t + r.crit, 0)

  const result = calcDamage({
    weapon,
    mode: s.mode,
    keen: s.keen,
    effects: [weapon?.e ?? {}, ...accessories.map(a => a?.e ?? {})],
    bonusPct: [...buffs.map(b => b.atk), adrenaPct, generator?.atk ?? 0, relicAtk, s.extraAtk],
    bonusCrit: buffs.reduce((t, b) => t + (b.crit ?? 0), 0) + relicCrit,
  })

  const toggleBuff = (id: string) =>
    set({ buffs: s.buffs.includes(id) ? s.buffs.filter(x => x !== id) : [...s.buffs, id] })
  const num = (v: string) => Number(v.replace(',', '.')) || 0

  const atkLines = [
    weapon?.e?.a || (s.mode === 'force' && weapon?.e?.f) ? { label: 'Efeito da arma', pct: (weapon?.e?.a ?? 0) + (s.mode === 'force' ? weapon?.e?.f ?? 0 : 0) } : null,
    ...accessories.map((a, i) => a ? { label: `${ACC_SLOTS[i].label}: ${a.n}`, pct: (a.e.a ?? 0) + (s.mode === 'force' ? a.e.f ?? 0 : 0) } : null),
    ...buffs.map(b => ({ label: b.name, pct: b.atk })),
    adrena ? { label: `Adrena: ${adrena.name}`, pct: adrenaPct } : null,
    generator ? { label: generator.name, pct: generator.atk } : null,
    relicAtk ? { label: `Relics (${relics.filter(r => r.atk).length})`, pct: relicAtk } : null,
    s.extraAtk ? { label: 'Outros', pct: s.extraAtk } : null,
  ].filter((l): l is { label: string; pct: number } => !!l && l.pct !== 0)

  return (
    <div className="damage-calc">
      <div className="damage-calc__layout">
        <div className="damage-calc__inputs">
          <Panel title="Arma">
            <div className="damage-calc__row">
              <label>
                Tipo
                <select value={s.weaponType} onChange={e => set({ weaponType: e.target.value, weapon: '' })}>
                  {Object.entries(WEAPON_TYPES).map(([k, v]) => <option key={k} value={k}>{v}</option>)}
                </select>
              </label>
              <label>
                Talics Keen
                <select value={s.keen} onChange={e => set({ keen: Number(e.target.value) })}>
                  {KEEN_PCT.map((pct, i) => <option key={i} value={i}>{i === 0 ? 'Nenhuma' : `+${i} (${pct}%)`}</option>)}
                </select>
              </label>
              {hasBoth && (
                <label>
                  Dano
                  <select value={s.mode} onChange={e => set({ mode: e.target.value as DamageMode })}>
                    <option value="atk">Ataque</option>
                    <option value="force">Força</option>
                  </select>
                </label>
              )}
            </div>
            <ItemPicker
              label="Arma"
              items={weapons}
              value={s.weapon}
              onChange={code => {
                const w = (byCode.get(code) as KbWeapon | undefined) ?? null
                set({ weapon: code, mode: defaultMode(w) })
              }}
              describe={w => {
                const r = w.fo && (!w.atk || w.fo[1] > w.atk[1]) ? w.fo : w.atk
                const fx = effectsLabel(w.e)
                return `Lv ${w.l} · ${w.fo === r ? 'Força' : 'Atk'} ${r ? `${fmt(r[0])}–${fmt(r[1])}` : ''}${fx ? ` · ${fx}` : ''}`
              }}
            />
          </Panel>

          <Panel title="Acessórios">
            <p className="damage-calc__hint">Só aparecem os que dão ataque, força ou crítico. Ordenados pelo maior % de ataque.</p>
            <div className="damage-calc__acc">
              {ACC_SLOTS.map(slot => (
                <ItemPicker
                  key={slot.key}
                  label={slot.label}
                  items={accLists[slot.list]}
                  value={s.acc[slot.key] ?? ''}
                  onChange={code => set({ acc: { ...s.acc, [slot.key]: code } })}
                  describe={a => `Lv ${a.l} · ${effectsLabel(a.e)}`}
                />
              ))}
            </div>
          </Panel>

          <Panel title="Buffs" actions={s.buffs.length ? <Button size="sm" variant="ghost" onClick={() => set({ buffs: [] })}>Limpar</Button> : undefined}>
            <BoostList name="Buffs" items={BUFFS} selected={s.buffs} toggle={toggleBuff} mode={weapon ? s.mode : undefined} />
          </Panel>

          <RelicsPanel value={s.relics} onChange={relics => set({ relics })} weaponClass={weapon?.k} mode={s.mode} />

          <Panel title="Adrena, gerador e outros">
            <BoostList name="Adrena" single items={ADRENAS} selected={s.adrena ? [s.adrena] : []} toggle={id => set({ adrena: id })} />
            <BoostList name="Gerador" single items={GENERATORS} selected={s.generator ? [s.generator] : []} toggle={id => set({ generator: id })} />
            <div className="damage-calc__row damage-calc__row--extra">
              <label>
                Outros % de ataque
                <input inputMode="decimal" value={s.extraAtk || ''} placeholder="0" onChange={e => set({ extraAtk: num(e.target.value) })} />
              </label>
            </div>
          </Panel>
        </div>

        <Panel title="Dano final" variant="amber" className="damage-calc__result" aria-live="polite">
          {!weapon ? <p className="damage-calc__hint">Escolha uma arma.</p> : (
            <>
              <div className="damage-calc__big">
                <span>{fmt(result.final[0])}</span>
                <small>a</small>
                <span>{fmt(result.final[1])}</span>
              </div>
              <p className="damage-calc__avg">Média <strong>{fmt(result.average)}</strong> · crítico {fmt(result.crit)}</p>
              <dl className="damage-calc__breakdown">
                <dt>{weapon.n} ({s.mode === 'force' ? 'força' : 'ataque'})</dt>
                <dd>{fmt(result.base[0])} – {fmt(result.base[1])}</dd>
                {result.keenPct > 0 && <><dt className="damage-calc__sub">Keen +{s.keen}</dt><dd className="damage-calc__sub">+{result.keenPct}%</dd></>}
                {atkLines.map((l, i) => <Fragment key={i}><dt className="damage-calc__sub">{l.label}</dt><dd className="damage-calc__sub">+{l.pct}%</dd></Fragment>)}
                <dt>Bônus de ataque</dt>
                <dd>+{result.totalPct}%</dd>
                <dt>Multiplicador</dt>
                <dd>×{result.multiplier}</dd>
              </dl>
            </>
          )}
        </Panel>
      </div>
    </div>
  )
}
