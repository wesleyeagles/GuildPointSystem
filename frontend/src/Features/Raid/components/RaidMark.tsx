import { useEffect, useRef, useState } from 'react'

/** Marcas de raid como no WoW; o número é o que o servidor guarda (1..8). */
const RAID_MARKS = [
  { mark: 1, label: 'Estrela', color: '#ffd600' },
  { mark: 2, label: 'Círculo', color: '#ff8c00' },
  { mark: 3, label: 'Losango', color: '#be50ff' },
  { mark: 4, label: 'Triângulo', color: '#3cc850' },
  { mark: 5, label: 'Lua', color: '#c8dcff' },
  { mark: 6, label: 'Quadrado', color: '#288cff' },
  { mark: 7, label: 'X', color: '#eb2d2d' },
  { mark: 8, label: 'Caveira', color: '#f0f0f0' },
] as const

/** Desenho da marca (o mesmo do painel do jogo), em 16 px por padrão. */
export function RaidMarkIcon({ mark, size = 16 }: { mark: number; size?: number }) {
  const info = RAID_MARKS.find((m) => m.mark === mark)
  if (!info) return null
  const c = info.color
  return (
    <svg className="raid-mark" width={size} height={size} viewBox="0 0 16 16" role="img" aria-label={info.label}>
      <title>{info.label}</title>
      {mark === 1 && <polygon points="8,1 10,6 15,6 11,9.5 12.5,15 8,11.5 3.5,15 5,9.5 1,6 6,6" fill={c} />}
      {mark === 2 && <circle cx="8" cy="8" r="6.5" fill={c} />}
      {mark === 3 && <polygon points="8,1 15,8 8,15 1,8" fill={c} />}
      {mark === 4 && <polygon points="8,1.5 15,14.5 1,14.5" fill={c} />}
      {mark === 5 && (
        <>
          <circle cx="8" cy="8" r="6.5" fill={c} />
          <circle cx="11.5" cy="5.5" r="5.5" fill="var(--raid-mark-bg, #000)" />
        </>
      )}
      {mark === 6 && <rect x="2" y="2" width="12" height="12" fill={c} />}
      {mark === 7 && <path d="M3 3 L13 13 M13 3 L3 13" stroke={c} strokeWidth="3" strokeLinecap="round" />}
      {mark === 8 && (
        <>
          <circle cx="8" cy="7" r="6" fill={c} />
          <rect x="4.5" y="10" width="7" height="5" rx="1" fill={c} />
          <circle cx="5.7" cy="7" r="1.6" fill="#141414" />
          <circle cx="10.3" cy="7" r="1.6" fill="#141414" />
        </>
      )}
    </svg>
  )
}

/** Botão do líder: abre as 8 marcas e "sem marca" e chama onPick com a escolhida (null tira). */
export function RaidMarkPicker({ current, onPick }: { current: number | null; onPick: (mark: number | null) => void }) {
  const [open, setOpen] = useState(false)
  const ref = useRef<HTMLSpanElement>(null)
  useEffect(() => {
    if (!open) return
    const close = (e: MouseEvent) => { if (!ref.current?.contains(e.target as Node)) setOpen(false) }
    window.addEventListener('mousedown', close)
    return () => window.removeEventListener('mousedown', close)
  }, [open])
  const pick = (mark: number | null) => { setOpen(false); onPick(mark) }
  return (
    <span className="raid-mark-picker" ref={ref}>
      <button type="button" title="Marca de raid" onClick={() => setOpen((v) => !v)}>
        {current ? <RaidMarkIcon mark={current} size={12} /> : '◎'}
      </button>
      {open && (
        <span className="raid-mark-picker__menu" role="menu">
          {RAID_MARKS.map((m) => (
            <button key={m.mark} type="button" role="menuitem" title={m.label}
              className={current === m.mark ? 'is-current' : undefined} onClick={() => pick(m.mark)}>
              <RaidMarkIcon mark={m.mark} />
            </button>
          ))}
          <button type="button" role="menuitem" title="Sem marca" onClick={() => pick(null)}>✕</button>
        </span>
      )}
    </span>
  )
}
