import { publicAssetUrl } from '@/Shared/utils/publicAssetUrl'
import sheet from './data/kbase-icons.json'
import type { GameIcon } from './data/boosts'

/** Cores de borda por grau, as mesmas da kbase. */
const GRADE_COLORS = ['#ffffff', '#ffffa3', '#b5a3d3', '#eaa037', '#1188e2', '#dfbae6', '#7ab5bb', '#69d474', '#8af28f', '#f1daba', '#d32b11']

/** Ícone da kbase recortado na folha compacta (public/sprites/kbase-icons.webp). */
export function KbIcon({ icon, grade = 0, size = 40 }: { icon?: number; grade?: number; size?: number }) {
  const color = GRADE_COLORS[grade] ?? GRADE_COLORS[0]
  const style = { width: size, height: size, '--kb-grade': color } as React.CSSProperties
  if (icon == null || icon < 0 || icon >= sheet.count) {
    return <span className="kb-icon kb-icon--missing" style={style} aria-hidden="true" />
  }
  const scale = size / sheet.size
  return <span className="kb-icon" aria-hidden="true" style={{
    ...style,
    backgroundImage: `url(${publicAssetUrl(sheet.path)})`,
    backgroundSize: `${sheet.cols * size}px ${Math.ceil(sheet.count / sheet.cols) * sheet.size * scale}px`,
    backgroundPosition: `${-(icon % sheet.cols) * size}px ${-Math.floor(icon / sheet.cols) * size}px`,
  }} />
}

/** Ícone de uma folha do jogo (buffs em /effect-icons, poções em /sprites). */
export function GameIconView({ icon, size = 32 }: { icon: GameIcon; size?: number }) {
  return <span className="kb-icon kb-icon--plain" aria-hidden="true" style={{
    width: size, height: size,
    backgroundImage: `url(${publicAssetUrl(icon.sheet)})`,
    backgroundSize: `${icon.cols * size}px ${icon.rows * size}px`,
    backgroundPosition: `${-(icon.cell % icon.cols) * size}px ${-Math.floor(icon.cell / icon.cols) * size}px`,
  }} />
}
