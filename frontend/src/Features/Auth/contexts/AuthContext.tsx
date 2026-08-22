import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from 'react'
import { useNavigate } from 'react-router-dom'
import { useQueryClient } from '@tanstack/react-query'
import { getToken, setToken } from '@/Shared/api/client'
import { authKeys, useAuthMe } from '@/Domain/Auth/hooks/useAuth'
import type { AuthResponse, Role } from '@/Domain/types/models'
import { connectSocket, disconnectSocket } from '@/Shared/websocket/socketClient'

interface AuthContextValue {
  user: AuthResponse | undefined
  isLoading: boolean
  isAuthenticated: boolean
  logout: () => void
  hasRole: (minRole: Role) => boolean
}

const ROLE_ORDER: Role[] = ['MEMBRO', 'MODERADOR', 'ADMINISTRADOR', 'LIDER']

const AuthContext = createContext<AuthContextValue | null>(null)

export function AuthProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient()
  const navigate = useNavigate()
  const [sessionVersion, setSessionVersion] = useState(0)
  const hasToken = sessionVersion >= 0 && !!getToken()
  const { data: user, isLoading } = useAuthMe(hasToken)

  const logout = useCallback(() => {
    setToken(null)
    disconnectSocket()
    queryClient.removeQueries({ queryKey: authKeys.me })
    queryClient.clear()
    setSessionVersion((v) => v + 1)
    navigate('/login', { replace: true })
  }, [queryClient, navigate])

  const hasRole = useCallback(
    (minRole: Role) => {
      if (!user) return false
      return ROLE_ORDER.indexOf(user.role) >= ROLE_ORDER.indexOf(minRole)
    },
    [user],
  )

  const canUseRealtime =
    hasToken && !!user && user.status === 'APROVADO' && user.profileComplete

  useEffect(() => {
    if (!canUseRealtime) return

    connectSocket().catch((err) => {
      console.error('[WS] Failed to connect after auth:', err)
    })
  }, [canUseRealtime])

  const value = useMemo(
    () => ({
      user,
      isLoading: hasToken && isLoading,
      isAuthenticated: hasToken && !!user,
      logout,
      hasRole,
    }),
    [user, isLoading, hasToken, logout, hasRole],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuthContext() {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuthContext must be used within AuthProvider')
  return ctx
}

export { authKeys }
