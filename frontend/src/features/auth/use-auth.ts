import { createContext, useContext } from 'react'
import type { RoleName, User } from '@/types/api'

export type AuthStatus = 'loading' | 'authenticated' | 'unauthenticated'

export interface AuthContextValue {
  status: AuthStatus
  user: User | null
  login: (email: string, password: string) => Promise<void>
  logout: () => Promise<void>
  /** Updates the cached user after profile edits. */
  setUser: (user: User) => void
  hasRole: (...roles: RoleName[]) => boolean
}

export const AuthContext = createContext<AuthContextValue | null>(null)

export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext)
  if (!context) throw new Error('useAuth must be used inside <AuthProvider>')
  return context
}

/** Display rules mirror the backend: managers can create projects, admins manage users. */
export function usePermissions() {
  const { hasRole } = useAuth()
  return {
    isAdmin: hasRole('ADMIN'),
    canCreateProjects: hasRole('ADMIN', 'MANAGER'),
  }
}
