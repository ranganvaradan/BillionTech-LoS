import { useCallback, useMemo, useState, type ReactNode } from 'react'
import { AuthContext } from './authContext'
import { clearDraft } from '@/lib/borrowerWizardDraft'
import { clearSessionUser, loadSessionUser, type SessionUser, saveSessionUser } from './types'

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<SessionUser | null>(() => loadSessionUser())

  const login = useCallback((u: SessionUser) => {
    saveSessionUser(u)
    setUser(u)
  }, [])

  const logout = useCallback(() => {
    clearSessionUser()
    clearDraft()
    setUser(null)
  }, [])

  const value = useMemo(() => ({ user, login, logout }), [user, login, logout])
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}
