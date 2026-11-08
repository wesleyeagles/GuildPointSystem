import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { apiClient } from '@/Shared/api/client'

/** Um membro numa CW: tempo em Crag Mind Field sem AFK e AFK (segundos), e a % da CW sem AFK. */
export interface CwMember {
  memberId: number
  nickname: string
  character: string | null
  activeSeconds: number
  afkSeconds: number
  percent: number
  /** Primeiro e último envio da DLL em Crag durante a CW. */
  firstSeenAt: string
  lastSeenAt: string
  /** Recebeu o objetivo da CW (CW ou CW Major). */
  awarded: boolean
  /** Recebeu o bônus de 10 min antes. */
  preBonus: boolean
}

export interface Cw {
  date: string
  slot: number
  label: string
  startsAt: string
  windowEndsAt: string
  /** Fim real (DLL viu a mensagem no chat, ou um Admin marcou); null = em andamento ou sem aviso. */
  endedAt: string | null
  endSource: 'GAME' | 'ADMIN' | null
  /** A janela acabou sem aviso de fim: precisa de um Admin. */
  closed: boolean
  awardedAt: string | null
  /** Dia de PB Major: vale "CW Major". */
  major: boolean
  objectiveName: string
  objectivePoints: number | null
  /** O objetivo (CW / CW Major) não existe na aba Objetivos: ninguém recebe. */
  objectiveMissing: boolean
  members: CwMember[]
  /** Quem recebeu o bônus de 10 min antes. */
  preBonus: string[]
}

export interface CwAttendance {
  days: number
  thresholdPercent: number
  preBonusPoints: number
  /** Administrador+: pode marcar o fim de uma CW sem aviso. */
  canManage: boolean
  cws: Cw[]
}

const key = (days: number) => ['cw-attendance', days] as const

export function useCwAttendance(days: number) {
  return useQuery({
    queryKey: key(days),
    queryFn: () => apiClient<CwAttendance>(`/cw-attendance?days=${days}`),
    staleTime: 15_000,
    refetchInterval: 30_000,
  })
}

/** Administrador+: marca o fim de uma CW sem aviso da DLL (antes de pontuar). */
export function useEndCw(days: number) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (vars: { date: string; slot: number; endedAt: string }) =>
      apiClient<CwAttendance>(`/cw-attendance/${vars.date}/${vars.slot}/end?days=${days}`, {
        method: 'PUT',
        body: { endedAt: vars.endedAt },
      }),
    onSuccess: (data) => queryClient.setQueryData(key(days), data),
  })
}
