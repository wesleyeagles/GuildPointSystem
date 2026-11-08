import { useEffect, useState } from 'react'
import { Link, Outlet, useLocation, useNavigate } from 'react-router-dom'
import { useAuthContext } from '@/Features/Auth/contexts/AuthContext'
import { useCurrentMember } from '@/Domain/Member/hooks/useMembers'
import { usePointsSubscription } from '@/Domain/Auction/hooks/useAuction'
import { useMemberLogNotifications } from '@/Domain/Log/hooks/useLogs'
import { useGuildEmojisSubscription } from '@/Domain/Emoji/hooks/useGuildEmojis'
import { EventToastProvider } from '@/Features/Events/components/EventToast/EventToast'
import { QuickCreateModal } from '@/Features/QuickCreate/QuickCreateModal'
import { AppToastProvider } from '@/Shared/ui/components/AppToast/AppToast'
import { getSocketState, type SocketState } from '@/Shared/websocket/socketClient'
import { serverNow } from '@/Shared/utils/serverClock'
import { EventHeaderTimers } from '@/Shared/ui/Layout/EventHeaderTimers'
import { SCHEDULE_TIME_ZONE, SERVER_TIME_ZONE } from '@/Shared/utils/scheduleTimeZone'
import { ScheduleAlertListener } from '@/Features/Schedule/ScheduleAlertListener'
import { AuctionCreatedListener } from '@/Features/Auction/AuctionCreatedListener'
import { AuctionNavBadge } from '@/Features/Auction/components/AuctionNavBadge/AuctionNavBadge'
import { AccountDeletedListener } from '@/Features/Auth/AccountDeletedListener'
import { PartyGameInviteListener } from '@/Features/Party/PartyGameInviteListener'
import { ApprovalsListener } from '@/Features/Admin/Approvals/ApprovalsListener'
import { ApprovalsNavBadge } from '@/Features/Admin/Approvals/components/ApprovalsNavBadge/ApprovalsNavBadge'
import type { Role } from '@/Domain/types/models'
import { useEquipmentSubscription } from '@/Domain/Equipment/hooks/useEquipment'
import { SyncAccessGate } from '@/Features/SyncAccess/SyncAccessGate'
import { useDllNotice } from '@/Features/SyncAccess/dllNotice'
import { useRaidAccess } from '@/Domain/Raid/hooks/useRaids'
import './Layout.styles.scss'

const NAV_ITEMS = [
  { to: '/', label: 'Frases' },
  { to: '/dashboard', label: 'Dashboard' },
  { to: '/events', label: 'Eventos' },
  { to: '/cw', label: 'CW' },
  { to: '/party', label: 'Party' },
  { to: '/raid', label: 'Raid' },
  { to: '/equipment', label: 'Equipamentos' },
  { to: '/quests', label: 'Quests' },
  { to: '/rotation', label: 'Rotação' },
  { to: '/auctions', label: 'Leilões' },
  { to: '/logs', label: 'Logs' },
]

/** Reached from the member card at the top of the sidebar, not from the nav list. */
const PROFILE_ITEM = { to: '/profile', label: 'Perfil' }

/** Reachable only by URL; listed so the header still shows the page title. */
// Fora do menu, mas com título no cabeçalho se alguém abrir pelo endereço.
const HIDDEN_ITEMS = [
  { to: '/donate', label: 'Doar Disena' },
]

/** Um item só no menu: as páginas de admin são abas dentro de /admin (AdminLayout). */
const ADMIN_ITEM: { to: string; label: string; minRole: Role } = { to: '/admin', label: 'Administração', minRole: 'MODERADOR' }

const SOCKET_LABEL: Record<SocketState, string> = {
  connected: 'Online',
  connecting: 'Conectando',
  disconnected: 'Offline',
}

function isActive(pathname: string, to: string) {
  return pathname === to || (to !== '/' && pathname.startsWith(to))
}

function useClock() {
  // Server time drives every clock: the whole guild sees Brasília time, whatever the PC says.
  const [now, setNow] = useState(() => new Date(serverNow()))
  const [socket, setSocket] = useState<SocketState>(getSocketState)
  useEffect(() => {
    const id = window.setInterval(() => {
      setNow(new Date(serverNow()))
      setSocket(getSocketState())
    }, 1000)
    return () => window.clearInterval(id)
  }, [])
  return { now, socket }
}

export function Layout() {
  return (
    <AppToastProvider>
      <EventToastProvider>
        <SyncAccessGate>
          <LayoutShell />
        </SyncAccessGate>
      </EventToastProvider>
    </AppToastProvider>
  )
}

function LayoutShell() {
  const { logout, hasRole, user } = useAuthContext()
  const { data: member } = useCurrentMember()
  const { data: raidAccess } = useRaidAccess()
  const location = useLocation()
  const navigate = useNavigate()
  useEffect(() => {
    const openEquipment = (event: KeyboardEvent) => {
      if (event.key !== 'F8' || event.ctrlKey || event.altKey || event.metaKey || event.shiftKey) return
      event.preventDefault()
      navigate('/equipment')
    }
    window.addEventListener('keydown', openEquipment)
    return () => window.removeEventListener('keydown', openEquipment)
  }, [navigate])
  const { now, socket } = useClock()
  const dllNotice = useDllNotice()
  // Drawer closes itself on navigation: it is only open for the path it was opened on.
  const [drawerPath, setDrawerPath] = useState<string | null>(null)
  const drawerOpen = drawerPath === location.pathname
  const setDrawerOpen = (open: boolean) => setDrawerPath(open ? location.pathname : null)
  const [quickCreateOpen, setQuickCreateOpen] = useState(false)

  usePointsSubscription(user?.memberId)
  useMemberLogNotifications(user?.memberId)
  useGuildEmojisSubscription()
  useEquipmentSubscription()

  const adminItems = hasRole(ADMIN_ITEM.minRole) ? [ADMIN_ITEM] : []
  const currentLabel =
    [...NAV_ITEMS, PROFILE_ITEM, ...HIDDEN_ITEMS, ADMIN_ITEM].find((item) => isActive(location.pathname, item.to))?.label ??
    'Terminal'
  const availableRatio =
    member && member.points > 0
      ? Math.max(0, Math.min(1, member.availablePoints / member.points))
      : 0

  return (
    <div className={`layout${drawerOpen ? ' layout--drawer-open' : ''}`}>
      <ScheduleAlertListener />
      <AuctionCreatedListener />
      <AccountDeletedListener />
      <PartyGameInviteListener />
      {hasRole('MODERADOR') && <ApprovalsListener />}
      {quickCreateOpen && <QuickCreateModal onClose={() => setQuickCreateOpen(false)} />}
      <aside className="layout__sidebar">
        <div className="layout__brand">
          <img src="/Logo-Blacklist.png" alt="Guild System" className="layout__logo" />
        </div>

        <div className="layout__member">
          <Link
            to={PROFILE_ITEM.to}
            className={`layout__member-head${location.pathname === PROFILE_ITEM.to ? ' layout__member-head--active' : ''}`}
            title="Abrir meu perfil"
          >
            <span className="layout__member-name">{user?.nickname ?? 'Membro'}</span>
          </Link>

          {member && (
            <div className="layout__gauge">
              <div className="layout__gauge-row">
                <span className="layout__gauge-label">Total</span>
                <span className="layout__gauge-value">{member.points}</span>
              </div>
              <div className="layout__gauge-row">
                <span className="layout__gauge-label">Disponível</span>
                <span className="layout__gauge-value layout__gauge-value--cyan">
                  {member.availablePoints}
                </span>
              </div>
              <div className="layout__gauge-bar" aria-hidden="true">
                <span style={{ width: `${availableRatio * 100}%` }} />
              </div>
            </div>
          )}
        </div>

        <nav className="layout__nav">
          {/* Evento (admin), Pontos de RG e Aproximação (moderador+): abas de um modal só. */}
          {hasRole('MODERADOR') && (
            <button
              type="button"
              className="layout__nav-link layout__quick-event"
              onClick={() => {
                setDrawerOpen(false)
                setQuickCreateOpen(true)
              }}
            >
              <span className="layout__nav-label">+ Criar</span>
            </button>
          )}

          {NAV_ITEMS.filter((item) => item.to !== '/raid' || raidAccess?.canView).map((item) => (
            <Link
              key={item.to}
              to={item.to}
              className={`layout__nav-link${isActive(location.pathname, item.to) ? ' layout__nav-link--active' : ''}`}
            >
              <span className="layout__nav-label">{item.label}</span>
              {item.to === '/auctions' && <AuctionNavBadge />}
            </Link>
          ))}

          {adminItems.length > 0 && (
            <div className="layout__admin">
              <span className="layout__admin-strip" aria-hidden="true" />
              {adminItems.map((item) => (
                <Link
                  key={item.to}
                  to={item.to}
                  className={`layout__nav-link layout__nav-link--admin${isActive(location.pathname, item.to) ? ' layout__nav-link--active' : ''}`}
                >
                  <span className="layout__nav-label">{item.label}</span>
                  {item.to === ADMIN_ITEM.to && <ApprovalsNavBadge />}
                </Link>
              ))}
            </div>
          )}
        </nav>

        <button type="button" className="layout__logout" onClick={logout}>
          Sair
        </button>
      </aside>

      <button
        type="button"
        className="layout__scrim"
        aria-label="Fechar menu"
        onClick={() => setDrawerOpen(false)}
      />

      <div className="layout__content">
        <div className="layout__status">
          <button
            type="button"
            className="layout__menu-toggle"
            aria-label="Abrir menu"
            onClick={() => setDrawerOpen(!drawerOpen)}
          >
            <span />
            <span />
            <span />
          </button>
          <h1 className="layout__page-title">{currentLabel}</h1>
          <EventHeaderTimers now={now} />
          <div className="layout__status-right">
            {/* Só quando é preciso trocar a DLL à mão: a que se atualiza sozinha não ganha aviso. */}
            {dllNotice.outdated && !dllNotice.autoUpdating && (
              <button
                type="button"
                className="layout__dll-outdated"
                title={`Atualize para a v${dllNotice.latestVersion}`}
                onClick={dllNotice.open}
              >
                DLL desatualizada
              </button>
            )}
            <span className={`layout__socket layout__socket--${socket}`} title={SOCKET_LABEL[socket]}>
              <span className="layout__socket-text">{SOCKET_LABEL[socket]}</span>
            </span>
            <span className="layout__clock layout__clock--server" title="Horário do servidor Cerberus (Moscou, UTC+3)">
              <span className="layout__clock-label">Server</span>
              {now.toLocaleTimeString('pt-BR', { timeZone: SERVER_TIME_ZONE, hour12: false })}
            </span>
            <span className="layout__clock layout__clock--local" title="Horário de Brasília (UTC-3), usado em todo o site">
              <span className="layout__clock-label">Brasília</span>
              {now.toLocaleTimeString('pt-BR', { timeZone: SCHEDULE_TIME_ZONE, hour12: false })}
            </span>
          </div>
        </div>
        <main className="layout__main">
          <Outlet />
        </main>
      </div>
    </div>
  )
}
