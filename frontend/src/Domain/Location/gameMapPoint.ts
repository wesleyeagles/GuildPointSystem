import { GAME_MAP_GEOMETRY, type GameMapGeometry } from './gameMapGeometry'

/** Limites do mapa no mundo (X e Z), como a janela do mapa do jogo usa. */
export interface GameMapBounds {
  minX: number
  minZ: number
  maxX: number
  maxZ: number
}

export interface GameMapPlacement {
  geometry: GameMapGeometry
  /** Ponto na imagem esticada para width x height (como o jogo desenha). */
  px: number
  py: number
}

/**
 * Onde (x, z) do mundo cai na imagem do mapa {@code code}, pela mesma conta do jogo (0x678DF0 no cliente).
 * live = limites que a DLL enviou (valem mais: seguem um patch); sem eles, os tirados dos arquivos do cliente.
 * null = mapa sem imagem/limites conhecidos ou ponto fora do mapa.
 */
export function gameMapPoint(code: number, x: number, z: number, live?: GameMapBounds | null): GameMapPlacement | null {
  const geometry = GAME_MAP_GEOMETRY[code]
  const box = live ?? geometry?.box
  if (!geometry || !box || box.maxX <= box.minX || box.maxZ <= box.minZ) return null
  const px = ((x - box.minX) * geometry.width) / (box.maxX - box.minX)
  const py = ((box.maxZ - z) * geometry.height) / (box.maxZ - box.minZ)
  if (!(px >= 0 && py >= 0 && px <= geometry.width && py <= geometry.height)) return null
  return { geometry, px, py }
}
