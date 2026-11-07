// Timeline of audit entries (who did what and when), used on project and task detail pages.

import { EmptyState, ErrorState, LoadingRows } from '@/components/page'
import { formatDateTime, humanize } from '@/lib/format'
import type { AuditEntry } from '@/types/api'

/** Renders an audit trail. Metadata comes from the server already stripped of anything sensitive. */
export function ActivityList({
  entries,
  isPending,
  error,
}: {
  entries: AuditEntry[] | undefined
  isPending: boolean
  error: unknown
}) {
  if (error) return <ErrorState error={error} />
  if (isPending) return <LoadingRows rows={3} />
  if (!entries?.length) return <EmptyState title="No activity yet" />

  return (
    <ol className="relative flex flex-col gap-4 border-l pl-5">
      {entries.map((entry) => (
        <li key={entry.id} className="relative text-sm">
          <span className="absolute top-1.5 -left-[23px] size-2 rounded-full bg-primary ring-4 ring-card" aria-hidden />
          <p>
            <span className="font-medium">{entry.actorName ?? 'System'}</span>{' '}
            <span className="text-muted-foreground">{humanize(entry.action).toLowerCase()}</span>
          </p>
          {describe(entry) && <p className="text-xs text-muted-foreground">{describe(entry)}</p>}
          <time className="text-xs text-muted-foreground" dateTime={entry.occurredAt}>
            {formatDateTime(entry.occurredAt)}
          </time>
        </li>
      ))}
    </ol>
  )
}

function describe(entry: AuditEntry): string | null {
  const m = entry.metadata
  if (m.from && m.to && entry.action.endsWith('STATUS_CHANGED')) return `${humanize(m.from)} → ${humanize(m.to)}`
  if (m.statusFrom && m.statusTo && m.statusFrom !== m.statusTo) {
    return `Status ${humanize(m.statusFrom)} → ${humanize(m.statusTo)}`
  }
  if (m.comment) return `“${m.comment}”`
  return null
}
