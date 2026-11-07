// React Query configuration shared by the app and the tests.

import { QueryClient } from '@tanstack/react-query'
import { ApiError } from './api-client'

/** New React Query client with the app's retry and caching defaults. */
export function createQueryClient() {
  return new QueryClient({
    defaultOptions: {
      queries: {
        staleTime: 30_000,
        refetchOnWindowFocus: false,
        // Retrying a 4xx can't succeed (forbidden, not found, invalid); only retry network and 5xx errors.
        retry: (failureCount, error) =>
          failureCount < 2 && !(error instanceof ApiError && error.status >= 400 && error.status < 500),
      },
    },
  })
}
