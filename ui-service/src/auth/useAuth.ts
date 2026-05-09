import { useContext } from 'react'
import { AuthContext, type AuthState } from './authContext'

export function useAuth(): AuthState {
  const c = useContext(AuthContext)
  if (!c) throw new Error('useAuth must be used under AuthProvider')
  return c
}
