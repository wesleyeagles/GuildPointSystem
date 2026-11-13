import { useState } from 'react'
import { useAuthContext } from '@/Features/Auth/contexts/AuthContext'
import { EventCreateModal } from '@/Features/Events/components/EventCreateModal/EventCreateModal'
import { PartyLocationPointsModal } from '@/Features/Party/components/PartyLocationPointsModal'
import { ProximityPointsModal } from '@/Features/Party/components/ProximityPointsModal'
import type { Role } from '@/Domain/types/models'
import './QuickCreateModal.styles.scss'

type QuickTab = 'event' | 'rg' | 'proximity'

const TABS: { id: QuickTab; label: string; minRole: Role }[] = [
  { id: 'event', label: 'Evento', minRole: 'ADMINISTRADOR' },
  { id: 'rg', label: 'Pontos de RG', minRole: 'MODERADOR' },
  { id: 'proximity', label: 'Aproximação', minRole: 'MODERADOR' },
]

/** "+ Criar" do menu: um modal só, com uma aba para cada coisa que a staff lança. */
export function QuickCreateModal({ onClose }: { onClose: () => void }) {
  const { hasRole } = useAuthContext()
  const tabs = TABS.filter((t) => hasRole(t.minRole))
  const [active, setActive] = useState<QuickTab>(tabs[0]?.id ?? 'rg')

  const strip = (
    <div className="quick-create-tabs" role="tablist" aria-label="O que criar">
      {tabs.map((t) => (
        <button
          key={t.id}
          type="button"
          role="tab"
          aria-selected={t.id === active}
          className={`quick-create-tabs__btn${t.id === active ? ' quick-create-tabs__btn--active' : ''}`}
          onClick={() => setActive(t.id)}
        >
          {t.label}
        </button>
      ))}
    </div>
  )

  if (active === 'event') return <EventCreateModal onClose={onClose} tabs={strip} />
  if (active === 'rg') return <PartyLocationPointsModal variant="rg" onClose={onClose} tabs={strip} />
  return <ProximityPointsModal onClose={onClose} tabs={strip} />
}
