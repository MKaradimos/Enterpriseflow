// Error boundary element used by the router.

import { isRouteErrorResponse, useRouteError } from 'react-router'

/** Catches render errors in any route, so one broken page doesn't blank the whole app. */
export function RouteError() {
  const error = useRouteError()
  const message = isRouteErrorResponse(error) ? `${error.status} ${error.statusText}` : 'Something went wrong.'

  return (
    <div role="alert" className="flex min-h-svh flex-col items-center justify-center gap-2 p-4 text-center">
      <h1 className="text-2xl font-semibold tracking-tight">Unexpected error</h1>
      <p className="text-sm text-muted-foreground">{message}</p>
      <a href="/" className="text-sm font-medium underline underline-offset-4">
        Reload the app
      </a>
    </div>
  )
}
