import { useRotation } from '@/Domain/Rotation/hooks/useRotation'
import { useServerNow } from '@/Shared/utils/useServerNow'
import { Panel } from '@/Shared/ui/components/Panel/Panel'
import { rotationTime } from '@/Features/Rotation/Rotation.utils'
import { formatCountdown, upcomingBossSlots } from '@/Features/Dashboard/utils/nextBosses'

const GUILD = 'Blacklist'
/** Brasília → Moscou (servidor), same offset the rotation page uses. */
const SERVER_OFFSET = 6
const LATER_COUNT = 1

function Bosses({ label, bosses }: { label: string; bosses: string[] }) {
  if (bosses.length === 0) return null
  return (
    <div className="next-bosses__area">
      <span className="next-bosses__area-label">{label}</span>
      <span>{bosses.join(' / ')}</span>
    </div>
  )
}

export function NextBossesPanel({ className }: { className?: string }) {
  const { data, isPending } = useRotation()
  const now = new Date(useServerNow())
  const [next, ...later] = data ? upcomingBossSlots(data.slots, GUILD, now) : []

  return (
    <Panel title="Próximos PBs" variant="amber" code={GUILD.toUpperCase()} className={className}>
      {isPending ? (
        <p className="dashboard__empty">Carregando rotação…</p>
      ) : !next ? (
        <p className="dashboard__empty">Nenhum PB da {GUILD} na rotação.</p>
      ) : (
        <div className="next-bosses">
          <div className={`next-bosses__next${next.secondsUntil <= 0 ? ' next-bosses__next--live' : ''}`}>
            <div className="next-bosses__head">
              <strong className="next-bosses__time">{next.time}</strong>
              <span className="next-bosses__server">Server {rotationTime(next.time, SERVER_OFFSET)}</span>
              <span className="next-bosses__countdown">
                {next.secondsUntil <= 0 ? 'Em andamento' : `em ${formatCountdown(next.secondsUntil)}`}
              </span>
            </div>
            <Bosses label="QG" bosses={next.qg} />
            <Bosses label="Outpost" bosses={next.outpost} />
          </div>

          {later.length > 0 && (
            <ul className="next-bosses__later">
              {later.slice(0, LATER_COUNT).map((slot) => (
                <li key={slot.time}>
                  <span className="next-bosses__later-time">{slot.time}</span>
                  <span className="next-bosses__later-bosses">{[...slot.qg, ...slot.outpost].join(' / ')}</span>
                </li>
              ))}
            </ul>
          )}
          <p className="next-bosses__note">Horários de Brasília · troca 5 min após o horário</p>
        </div>
      )}
    </Panel>
  )
}
