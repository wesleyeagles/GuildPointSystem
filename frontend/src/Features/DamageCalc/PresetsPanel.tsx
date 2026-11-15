import { useEffect, useRef, useState } from 'react'
import { createPortal } from 'react-dom'
import { Panel } from '@/Shared/ui/components/Panel/Panel'
import { Button } from '@/Shared/ui/components/Button/Button'
import { evaluate, sanitizeState, type ItemIndex, type SavedState } from './damageState'
import { KbIcon } from './KbIcon'
import '@/Features/Auth/Auth.styles.scss'

const PRESETS_KEY = 'damageCalc.presets.v1'
/** Quantos presets cabem lado a lado na comparação. */
export const MAX_COMPARE = 3
const NAME_MAX = 40

export interface Preset { name: string; state: SavedState }

function loadPresets(): Preset[] {
  try {
    const raw = JSON.parse(localStorage.getItem(PRESETS_KEY) ?? '[]')
    if (!Array.isArray(raw)) return []
    return raw
      .filter(p => p && typeof p.name === 'string' && p.name.trim())
      .map(p => ({ name: String(p.name).trim().slice(0, NAME_MAX), state: sanitizeState(p.state) }))
  } catch {
    return []
  }
}

const fmt = (n: number) => n.toLocaleString('pt-BR')
const sameName = (a: string, b: string) => a.trim().toLowerCase() === b.trim().toLowerCase()

/** Salvar a configuração atual com um nome, carregar de volta e comparar até 3 lado a lado. */
export function PresetsPanel({ current, byCode, onLoad }: {
  current: SavedState
  byCode: ItemIndex
  onLoad: (state: SavedState) => void
}) {
  const [presets, setPresets] = useState<Preset[]>(loadPresets)
  const [name, setName] = useState('')
  const [picked, setPicked] = useState<string[]>([])
  const [comparing, setComparing] = useState(false)
  useEffect(() => { localStorage.setItem(PRESETS_KEY, JSON.stringify(presets)) }, [presets])

  const trimmed = name.trim()
  const overwrites = presets.some(p => sameName(p.name, trimmed))
  const save = (e: React.FormEvent) => {
    e.preventDefault()
    if (!trimmed) return
    const preset = { name: trimmed.slice(0, NAME_MAX), state: structuredClone(current) }
    // Mesmo nome substitui no mesmo lugar da lista.
    setPresets(list => overwrites ? list.map(p => sameName(p.name, trimmed) ? preset : p) : [...list, preset])
    setName('')
  }
  const remove = (n: string) => {
    setPresets(list => list.filter(p => p.name !== n))
    setPicked(list => list.filter(x => x !== n))
  }
  const togglePick = (n: string) =>
    setPicked(list => list.includes(n) ? list.filter(x => x !== n) : list.length < MAX_COMPARE ? [...list, n] : list)

  const chosen = picked.map(n => presets.find(p => p.name === n)).filter((p): p is Preset => !!p)

  return (
    <Panel title="Presets" actions={
      <Button size="sm" disabled={chosen.length < 2} onClick={() => setComparing(true)}>
        Comparar{chosen.length ? ` (${chosen.length})` : ''}
      </Button>
    }>
      <form className="damage-calc__preset-save" onSubmit={save}>
        <input aria-label="Nome do preset" placeholder="Nome do preset" maxLength={NAME_MAX} value={name} onChange={e => setName(e.target.value)} />
        <Button type="submit" size="sm" disabled={!trimmed}>{overwrites ? 'Substituir' : 'Salvar'}</Button>
      </form>
      {!presets.length ? (
        <p className="damage-calc__hint">Nenhum preset salvo. Monte a arma, os buffs e as relics, dê um nome e salve.</p>
      ) : (
        <>
          <p className="damage-calc__hint">Marque de 2 a {MAX_COMPARE} para comparar.</p>
          <ul className="damage-calc__presets">
            {presets.map(p => {
              const { weapon, result } = evaluate(p.state, byCode)
              const on = picked.includes(p.name)
              return (
                <li key={p.name} className={on ? 'is-picked' : undefined}>
                  <input type="checkbox" aria-label={`Comparar ${p.name}`} checked={on}
                    disabled={!on && picked.length >= MAX_COMPARE} onChange={() => togglePick(p.name)} />
                  <KbIcon icon={weapon?.i} grade={weapon?.g} size={32} />
                  <span className="damage-calc__preset-name">
                    {p.name}
                    <small>{weapon ? `${fmt(result.final[0])} – ${fmt(result.final[1])}` : 'Sem arma'}</small>
                  </span>
                  <Button size="sm" variant="secondary" onClick={() => onLoad(structuredClone(p.state))}>Carregar</Button>
                  <button type="button" className="damage-calc__preset-remove" aria-label={`Excluir ${p.name}`} title="Excluir"
                    onClick={() => remove(p.name)}>✕</button>
                </li>
              )
            })}
          </ul>
        </>
      )}
      {comparing && <CompareModal presets={chosen} byCode={byCode} onClose={() => setComparing(false)} />}
    </Panel>
  )
}

/** Só o dano final (mínimo e máximo) de cada preset, lado a lado com o ícone da arma. */
function CompareModal({ presets, byCode, onClose }: { presets: Preset[]; byCode: ItemIndex; onClose: () => void }) {
  const card = useRef<HTMLDivElement>(null)
  useEffect(() => {
    card.current?.querySelector<HTMLButtonElement>('[data-autofocus]')?.focus()
    const onKey = (e: KeyboardEvent) => { if (e.key === 'Escape') onClose() }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [onClose])

  const rows = presets.map(p => ({ preset: p, ...evaluate(p.state, byCode) }))
  const best = (pick: (r: (typeof rows)[number]) => number) => Math.max(...rows.filter(r => r.weapon).map(pick))
  const bestMin = best(r => r.result.final[0])
  const bestMax = best(r => r.result.final[1])

  // Portal: o Panel tem clip-path e cortaria o overlay.
  return createPortal(
    <div className="modal-overlay" role="presentation" onClick={onClose}>
      <div ref={card} className="modal-card damage-compare" role="dialog" aria-modal="true" aria-labelledby="damage-compare-title"
        onClick={e => e.stopPropagation()}>
        <div className="auth-window-bar"><span id="damage-compare-title">Comparar presets</span></div>
        <div className="auth-card-content">
          <div className="damage-compare__grid" style={{ gridTemplateColumns: `repeat(${rows.length}, minmax(0, 1fr))` }}>
            {rows.map(({ preset, weapon, result }) => (
              <section key={preset.name} className="damage-compare__col" aria-label={preset.name}>
                <h3>{preset.name}</h3>
                <KbIcon icon={weapon?.i} grade={weapon?.g} size={56} />
                <p className="damage-compare__weapon">{weapon?.n ?? 'Sem arma'}</p>
                {weapon && (
                  <dl>
                    <dt>Mínimo</dt>
                    <dd className={result.final[0] === bestMin ? 'is-best' : undefined}>{fmt(result.final[0])}</dd>
                    <dt>Máximo</dt>
                    <dd className={result.final[1] === bestMax ? 'is-best' : undefined}>{fmt(result.final[1])}</dd>
                  </dl>
                )}
              </section>
            ))}
          </div>
          <div className="damage-compare__actions">
            <Button data-autofocus type="button" variant="secondary" onClick={onClose}>Fechar</Button>
          </div>
        </div>
      </div>
    </div>,
    document.body,
  )
}
