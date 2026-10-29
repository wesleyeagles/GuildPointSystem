import { createContext, useContext } from 'react'

export interface DllNotice {
  /** Sincroniza com uma DLL anterior à mais recente. */
  outdated: boolean
  latestVersion: string | null
  /** Reabre o aviso (selo no topo do site). */
  open: () => void
}

export const DllNoticeContext = createContext<DllNotice>({ outdated: false, latestVersion: null, open: () => {} })

/** Estado do aviso de DLL desatualizada, fornecido pelo SyncAccessGate. */
export function useDllNotice() {
  return useContext(DllNoticeContext)
}
