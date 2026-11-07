// React Query hook for the dashboard data (one request returns everything the page shows).

import { useQuery } from '@tanstack/react-query'
import { api } from '@/lib/api-client'
import { queryKeys } from '@/lib/query'
import type { Dashboard } from '@/types/api'

/** Loads the dashboard for the current user. */
export function useDashboard() {
  return useQuery({
    queryKey: queryKeys.dashboard,
    queryFn: async () => (await api.get<Dashboard>('/api/v1/dashboard')).data,
  })
}
