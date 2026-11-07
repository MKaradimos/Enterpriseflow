import { Loader2 } from 'lucide-react'
import type { ReactNode } from 'react'
import { Navigate, Outlet, useLocation } from 'react-router'
import { EmptyState } from '@/components/page'
import type { RoleName } from '@/types/api'
import { useAuth } from './use-auth'

/** Waits for session restoration, then either renders the protected routes or redirects to /login. */
export function RequireAuth() {
  const { status } = useAuth()
  const location = useLocation()

  if (status === 'loading') {
    return (
      <div className="flex min-h-svh items-center justify-center" role="status" aria-label="Loading session">
        <Loader2 className="size-6 animate-spin text-muted-foreground" />
      </div>
    )
  }
  if (status === 'unauthenticated') {
    // Remember where the user was going, so login can send them back there.
    return <Navigate to="/login" replace state={{ from: location.pathname + location.search }} />
  }
  return <Outlet />
}

/**
 * Hides a page from users without the role. This is a UX convenience only: the backend enforces the same rule
 * and returns 403 regardless of what the UI shows.
 */
export function RequireRole({ roles, children }: { roles: RoleName[]; children: ReactNode }) {
  const { hasRole } = useAuth()
  if (!hasRole(...roles)) {
    return <EmptyState title="Access restricted" description="You don't have permission to view this page." />
  }
  return <>{children}</>
}
