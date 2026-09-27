export function formatLastLogin(lastLoginAt: string | null | undefined): string {
  if (!lastLoginAt) return 'Nunca'
  return new Date(lastLoginAt).toLocaleString('pt-BR', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  })
}
