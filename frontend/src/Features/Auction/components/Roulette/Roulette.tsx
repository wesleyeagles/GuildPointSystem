import { useEffect, useMemo, useRef, useState } from 'react'
import type { RouletteProps } from './Roulette.types'
import { MIN_SPIN_MS } from './Roulette.types'
import './Roulette.styles.scss'

function seededRandom(seed: number) {
  let s = seed
  return () => {
    s = (s * 9301 + 49297) % 233280
    return s / 233280
  }
}

export function computeSpinRotation(
  segmentCount: number,
  winnerIndex: number,
  seed: number,
): number {
  const rand = seededRandom(seed)
  const segmentAngle = 360 / segmentCount
  const extraSpins = 5 + Math.floor(rand() * 3)
  const offset = rand() * 0.6 + 0.2
  const targetAngle = winnerIndex * segmentAngle + segmentAngle * offset
  return extraSpins * 360 + (360 - targetAngle)
}

export function Roulette({
  segments,
  winnerIndex,
  seed = Date.now(),
  spinning = true,
  onComplete,
}: RouletteProps) {
  const [rotation, setRotation] = useState(0)
  const completedRef = useRef(false)

  const segmentAngle = useMemo(
    () => (segments.length > 0 ? 360 / segments.length : 0),
    [segments.length],
  )

  useEffect(() => {
    if (!spinning || segments.length === 0 || completedRef.current) return

    const finalRotation = computeSpinRotation(segments.length, winnerIndex, seed ?? Date.now())
    setRotation(finalRotation)

    const timer = setTimeout(() => {
      completedRef.current = true
      onComplete?.(winnerIndex)
    }, MIN_SPIN_MS)

    return () => clearTimeout(timer)
  }, [spinning, segments.length, winnerIndex, seed, onComplete])

  if (segments.length === 0) return null

  return (
    <div className="roulette">
      <div className="roulette__pointer" />
      <div
        className="roulette__wheel"
        style={{
          transform: `rotate(${rotation}deg)`,
          transition: spinning ? `transform ${MIN_SPIN_MS}ms cubic-bezier(0.2, 0.8, 0.3, 1)` : 'none',
        }}
      >
        <svg viewBox="0 0 200 200" className="roulette__svg">
          {segments.map((seg, i) => {
            const startAngle = (i * segmentAngle - 90) * (Math.PI / 180)
            const endAngle = ((i + 1) * segmentAngle - 90) * (Math.PI / 180)
            const x1 = 100 + 90 * Math.cos(startAngle)
            const y1 = 100 + 90 * Math.sin(startAngle)
            const x2 = 100 + 90 * Math.cos(endAngle)
            const y2 = 100 + 90 * Math.sin(endAngle)
            const largeArc = segmentAngle > 180 ? 1 : 0
            const colors = [
              'var(--roulette-c1)',
              'var(--roulette-c2)',
              'var(--roulette-c3)',
              'var(--roulette-c4)',
            ]
            return (
              <g key={seg.id}>
                <path
                  d={`M 100 100 L ${x1} ${y1} A 90 90 0 ${largeArc} 1 ${x2} ${y2} Z`}
                  fill={colors[i % colors.length]}
                />
                <text
                  x={100 + 55 * Math.cos((startAngle + endAngle) / 2)}
                  y={100 + 55 * Math.sin((startAngle + endAngle) / 2)}
                  textAnchor="middle"
                  dominantBaseline="middle"
                  className="roulette__label"
                >
                  {seg.label.slice(0, 8)}
                </text>
              </g>
            )
          })}
        </svg>
      </div>
      {spinning && (
        <p className="roulette__status">Desempate em andamento...</p>
      )}
    </div>
  )
}
