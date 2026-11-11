import { BrowserRouter, Navigate, Route, Routes, useLocation } from 'react-router-dom'
import { Layout } from '@/Shared/ui/Layout/Layout'
import { AuthProvider } from '@/Features/Auth/contexts/AuthContext'
import { ProtectedRoute, ModeratorRoute, AdminRoute } from '@/Features/Auth/ProtectedRoute'
import { AuthShell } from '@/Features/Auth/AuthShell'
import { LoginPage } from '@/Features/Auth/LoginPage'
import { RegisterPage } from '@/Features/Auth/RegisterPage'
import { DiscordCallbackPage } from '@/Features/Auth/DiscordCallbackPage'
import { DashboardPage } from '@/Features/Dashboard/Dashboard'
import { PhrasesPage } from '@/Features/Phrases/Phrases'
import { DonationPage } from '@/Features/Donation/Donation'
import { LogsPage } from '@/Features/Logs/Logs'
import { EventsPage } from '@/Features/Events/Events'
import { EquipmentPage } from '@/Features/Equipment/Equipment'
import { QuestsPage } from '@/Features/Quests/Quests'
import { RotationPage } from '@/Features/Rotation/Rotation'
import { DamageCalcPage } from '@/Features/DamageCalc/DamageCalc'
import { AuctionsPage } from '@/Features/Auction/AuctionsPage'
import { AuctionDetailPage } from '@/Features/Auction/AuctionDetail'
import { ApprovalsPage } from '@/Features/Admin/Approvals/Approvals'
import { OnlinePage } from '@/Features/Admin/Online/Online'
import { AdminIndex, AdminLayout } from '@/Features/Admin/AdminLayout'
import { DllAdminPage } from '@/Features/Admin/Dll/DllAdmin'
import { ProfilePage } from '@/Features/Profile/Profile'
import { GuildLeagueRagPage } from '@/Features/GuildLeagueRag/GuildLeagueRagPage'
import { ServerInfoListPage } from '@/Features/ServerInfo/ServerInfoListPage'
import { ServerInfoTopicPage } from '@/Features/ServerInfo/ServerInfoTopicPage'
import { PartyPage } from '@/Features/Party/Party'
import { RaidPage } from '@/Features/Raid/Raid'
import { ResourcesPage } from '@/Features/Resources/Resources'
import { PbHistoryPage } from '@/Features/PbHistory/PbHistory'
import { CwAttendancePage } from '@/Features/CwAttendance/CwAttendance'
import { LocationHistoryPage } from '@/Features/LocationHistory/LocationHistory'
import { PresenceReporter } from '@/Features/Presence/PresenceReporter'
import './App.styles.scss'

function EquipmentRoute() {
  const location = useLocation()
  return <EquipmentPage key={location.key} />
}

function App() {
  return (
    <BrowserRouter>
      <AuthProvider>
        <PresenceReporter />
        <Routes>
          <Route element={<AuthShell />}>
            <Route path="/login" element={<LoginPage />} />
            <Route path="/register" element={<RegisterPage />} />
          </Route>

          <Route path="/auth/callback" element={<DiscordCallbackPage />} />
          <Route path="/guild-league-rag" element={<GuildLeagueRagPage />} />
          <Route path="/server-info" element={<ServerInfoListPage />} />
          <Route path="/server-info/:slug" element={<ServerInfoTopicPage />} />

          <Route element={<ProtectedRoute />}>
            <Route element={<Layout />}>
              {/* Home: votação das frases da guild. */}
              <Route index element={<PhrasesPage />} />
              <Route path="dashboard" element={<DashboardPage />} />
              <Route path="donate" element={<DonationPage />} />
              <Route path="logs" element={<LogsPage />} />
              <Route path="objectives" element={<Navigate to="/events?tab=objectives" replace />} />
              <Route path="events" element={<EventsPage />} />
              <Route path="cw" element={<CwAttendancePage />} />
              <Route path="items" element={<Navigate to="/auctions?tab=items" replace />} />
              <Route path="equipment" element={<EquipmentRoute />} />
              <Route path="quests" element={<QuestsPage />} />
              <Route path="rotation" element={<RotationPage />} />
              <Route path="damage" element={<DamageCalcPage />} />
              <Route path="party" element={<PartyPage />} />
              <Route path="raid" element={<RaidPage />} />
              <Route path="auctions" element={<AuctionsPage />} />
              <Route path="auctions/:id" element={<AuctionDetailPage />} />
              <Route path="profile" element={<ProfilePage />} />
              <Route path="profile/:id" element={<ProfilePage />} />
              {/* Sem link no menu: só pela URL. */}
              <Route element={<AdminRoute />}>
                <Route path="onde-estava" element={<LocationHistoryPage />} />
              </Route>
              {/* Administração: um item no menu, uma aba por página (cada aba mantém o próprio endereço). */}
              <Route element={<ModeratorRoute />}>
                <Route path="admin" element={<AdminLayout />}>
                  <Route index element={<AdminIndex />} />
                  <Route path="approvals" element={<ApprovalsPage />} />
                  <Route element={<AdminRoute />}>
                    <Route path="online" element={<OnlinePage />} />
                    <Route path="dll" element={<DllAdminPage />} />
                    <Route path="resources" element={<ResourcesPage />} />
                    <Route path="pb-history" element={<PbHistoryPage />} />
                    <Route path="cw" element={<Navigate to="/cw" replace />} />
                  </Route>
                </Route>
              </Route>
            </Route>
          </Route>

          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </AuthProvider>
    </BrowserRouter>
  )
}

export default App
