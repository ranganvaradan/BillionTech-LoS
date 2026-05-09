import { createContext } from 'react'
import type { SessionUser } from './types'

export type AuthState = {
  user: SessionUser | null
  login: (u: SessionUser) => void
  logout: () => void
}

export const AuthContext = createContext<AuthState | null>(null)
