/**
 * Mapas que aparecem no "Pontos por local" / "Pontos de RG".
 * {@code value} é o nome que a DLL envia (título do mapa grande, tabela do cliente 4.1.5);
 * {@code label} é como a guild chama o mapa, quando o jogo escreve diferente.
 */
export const GAME_MAPS: { value: string; label: string }[] = [
  { value: 'Cora HQ', label: 'Cora HQ' },
  { value: 'Crag Mind Field', label: 'Crag Mine Field' },
  { value: 'Haram', label: 'Haram' },
  { value: 'Numerus', label: 'Numerus' },
  { value: 'Sette Desert', label: 'Sette Desert' },
  { value: 'Elan Plateau', label: 'Elan Plateau' },
  { value: 'Ether of Heaven', label: 'Ether' },
  { value: "Beast's Mountain", label: "Beast's Mountain" },
  { value: "Outcast's Land", label: "Outcast's Land" },
]

/** Nome para exibir: o da lista acima, ou o próprio nome do jogo. */
export function mapLabel(value: string | null) {
  return GAME_MAPS.find((m) => m.value === value)?.label ?? value ?? ''
}

/** Local some depois disso sem envio novo (a DLL reenvia a cada 60 s com o jogo aberto). */
export const LOCATION_FRESH_MS = 3 * 60_000

/** "Mapa · Região" do último envio, ou null se não há local ou ele é antigo. */
export function currentLocation(
  status: { mapName: string | null; regionName: string | null; locationAt: string | null } | undefined,
  now: number,
) {
  if (!status?.mapName || !status.locationAt) return null
  if (now - Date.parse(status.locationAt) > LOCATION_FRESH_MS) return null
  const map = mapLabel(status.mapName)
  return status.regionName && status.regionName !== status.mapName ? `${map} · ${status.regionName}` : map
}
