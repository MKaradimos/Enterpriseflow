// Notifications page: list with unread filter; opening a notification marks it read and links to its item.

import { Bell, CheckCheck } from 'lucide-react'
import { useState } from 'react'
import { useNavigate } from 'react-router'
import { toast } from 'sonner'
import { EmptyState, ErrorState, LoadingRows, PageHeader, Pagination } from '@/components/page'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { errorMessage } from '@/lib/api-client'
import { relativeTime } from '@/lib/format'
import { cn } from '@/lib/utils'
import type { Notification } from '@/types/api'
import { useMarkRead, useNotifications } from './api'

// Where a notification links to, depending on the type of item it refers to.
const referenceRoutes: Record<NonNullable<Notification['referenceType']>, (id: string) => string> = {
  TASK: (id) => `/tasks/${id}`,
  PROJECT: (id) => `/projects/${id}`,
  APPROVAL: () => '/approvals?scope=ALL',
}

/** Notification list with "unread only" filter and "mark all as read". */
export function NotificationsPage() {
  const [page, setPage] = useState(0)
  const [unreadOnly, setUnreadOnly] = useState(false)
  const { data, isPending, isError, error, refetch } = useNotifications(page, unreadOnly)
  const markRead = useMarkRead()
  const navigate = useNavigate()

  const open = (n: Notification) => {
    if (!n.read) markRead.mutate(n.id)
    if (n.referenceType && n.referenceId) navigate(referenceRoutes[n.referenceType](n.referenceId))
  }

  return (
    <div className="flex flex-col gap-6">
      <PageHeader
        title="Notifications"
        actions={
          <>
            <Button
              variant="outline"
              aria-pressed={unreadOnly}
              onClick={() => {
                setUnreadOnly((v) => !v)
                setPage(0)
              }}
            >
              {unreadOnly ? 'Show all' : 'Unread only'}
            </Button>
            <Button
              variant="outline"
              disabled={markRead.isPending}
              onClick={() =>
                markRead.mutate('all', {
                  onSuccess: () => toast.success('All notifications marked as read'),
                  onError: (e) => toast.error(errorMessage(e)),
                })
              }
            >
              <CheckCheck /> Mark all read
            </Button>
          </>
        }
      />
      <Card className="gap-0 py-0">
        {isError ? (
          <ErrorState error={error} onRetry={() => void refetch()} />
        ) : isPending ? (
          <LoadingRows />
        ) : data.content.length === 0 ? (
          <EmptyState icon={Bell} title={unreadOnly ? 'No unread notifications' : 'No notifications yet'} />
        ) : (
          <>
            <ul className="divide-y">
              {data.content.map((n) => (
                <li key={n.id}>
                  <button
                    type="button"
                    onClick={() => open(n)}
                    className={cn('flex w-full gap-3 p-4 text-left hover:bg-muted/50', !n.read && 'bg-accent/40')}
                  >
                    <span
                      className={cn('mt-1.5 size-2 shrink-0 rounded-full', n.read ? 'bg-transparent' : 'bg-primary')}
                      aria-hidden
                    />
                    <span className="min-w-0 flex-1">
                      <span className="block text-sm font-medium">
                        {n.title}
                        {!n.read && <span className="sr-only"> (unread)</span>}
                      </span>
                      <span className="block text-sm text-muted-foreground">{n.message}</span>
                      <span className="block text-xs text-muted-foreground">{relativeTime(n.createdAt)}</span>
                    </span>
                  </button>
                </li>
              ))}
            </ul>
            <Pagination
              page={data.page}
              totalPages={data.totalPages}
              totalElements={data.totalElements}
              onPageChange={setPage}
            />
          </>
        )}
      </Card>
    </div>
  )
}
