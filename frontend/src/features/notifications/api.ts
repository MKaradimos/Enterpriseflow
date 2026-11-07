// React Query hooks for notifications: list, unread count (polled) and mark as read.

import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '@/lib/api-client'
import { queryKeys } from '@/lib/query'
import type { Notification, Page } from '@/types/api'

/** Paged notifications of the current user. */
export function useNotifications(page: number, unreadOnly: boolean) {
  return useQuery({
    queryKey: [...queryKeys.notifications, 'list', { page, unreadOnly }],
    queryFn: async () =>
      (await api.get<Page<Notification>>('/api/v1/notifications', { params: { page, size: 15, unreadOnly } })).data,
    placeholderData: keepPreviousData,
  })
}

/** Polled for the header badge. 30s is a reasonable trade-off without WebSockets. */
export function useUnreadCount() {
  return useQuery({
    queryKey: queryKeys.unreadCount,
    queryFn: async () => (await api.get<{ count: number }>('/api/v1/notifications/unread-count')).data.count,
    refetchInterval: 30_000,
  })
}

/** Marks one notification, or all of them, as read. */
export function useMarkRead() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: async (id: string | 'all') => {
      await api.post(id === 'all' ? '/api/v1/notifications/read-all' : `/api/v1/notifications/${id}/read`)
    },
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: queryKeys.notifications }),
  })
}
