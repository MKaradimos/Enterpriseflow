// Holds the signed-in user and restores the session from the refresh cookie on load.

import { useQueryClient } from '@tanstack/react-query'
import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react'
import * as client from '@/lib/api-client'
import type { User } from '@/types/api'
import { AuthContext, type AuthContextValue, type AuthStatus } from './use-auth'

export function AuthProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient()
  const [status, setStatus] = useState<AuthStatus>('loading')
  const [user, setUser] = useState<User | null>(null)

  const clearSession = useCallback(() => {
    client.setAccessToken(null)
    setUser(null)
    setStatus('unauthenticated')
    // Drop cached data so the next user never sees the previous user's projects.
    queryClient.clear()
  }, [queryClient])

  // On load, try to restore the session from the HttpOnly refresh cookie.
  useEffect(() => {
    let cancelled = false
    client
      .refreshSession()
      .then((session) => {
        if (cancelled) return
        setUser(session.user)
        setStatus('authenticated')
      })
      .catch(() => {
        if (!cancelled) setStatus('unauthenticated')
      })
    return () => {
      cancelled = true
    }
  }, [])

  useEffect(() => {
    client.setSessionExpiredHandler(clearSession)
    return () => client.setSessionExpiredHandler(null)
  }, [clearSession])

  const login = useCallback(async (email: string, password: string) => {
    const session = await client.login(email, password)
    setUser(session.user)
    setStatus('authenticated')
  }, [])

  const logout = useCallback(async () => {
    try {
      await client.logout()
    } finally {
      clearSession()
    }
  }, [clearSession])

  const value = useMemo<AuthContextValue>(
    () => ({
      status,
      user,
      login,
      logout,
      setUser,
      hasRole: (...roles) => !!user && roles.some((role) => user.roles.includes(role)),
    }),
    [status, user, login, logout],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}
