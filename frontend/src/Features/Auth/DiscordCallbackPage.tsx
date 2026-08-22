import { useEffect } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { useQueryClient } from '@tanstack/react-query'
import { setToken } from '@/Shared/api/client'
import { authKeys } from '@/Domain/Auth/hooks/useAuth'

export function DiscordCallbackPage() {
  const [searchParams] = useSearchParams()
  const navigate = useNavigate()
  const queryClient = useQueryClient()

  useEffect(() => {
    const token = searchParams.get('token')
    const error = searchParams.get('error')

    if (error) {
      const message = searchParams.get('message')
      const params = new URLSearchParams({ error: 'discord' })
      if (message) params.set('message', message)
      navigate(`/login?${params.toString()}`, { replace: true })
      return
    }

    if (!token) {
      navigate('/login?error=discord', { replace: true })
      return
    }

    setToken(token)
    queryClient.removeQueries({ queryKey: authKeys.me })
    window.history.replaceState({}, '', '/auth/callback')
    navigate('/', { replace: true })
  }, [searchParams, navigate, queryClient])

  return (
    <div className="loading-screen">
      <p>Conectando com Discord...</p>
    </div>
  )
}
