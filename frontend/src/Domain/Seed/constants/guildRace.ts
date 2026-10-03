export const GUILD_RACE_NAME = 'Cora'

export function findCoraRaceId(races: { id: number; name: string }[]): number {
  return races.find((r) => r.name === GUILD_RACE_NAME)?.id ?? 0
}
