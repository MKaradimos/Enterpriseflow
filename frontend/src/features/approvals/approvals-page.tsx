// Approvals page: tabs for "to review", "my requests" and "all", a status filter, and the
// approve / reject / cancel actions with confirmation.

import { Check, CheckSquare, X } from 'lucide-react'
import { useState } from 'react'
import { Link, useSearchParams } from 'react-router'
import { toast } from 'sonner'
import { ConfirmDialog } from '@/components/confirm-dialog'
import { EmptyState, ErrorState, LoadingRows, PageHeader, Pagination } from '@/components/page'
import { ApprovalStatusBadge } from '@/components/status-badges'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { Select } from '@/components/ui/input'
import { errorMessage } from '@/lib/api-client'
import { approvalStatuses, formatCurrency, formatDateTime, humanize } from '@/lib/format'
import { cn } from '@/lib/utils'
import type { Approval, ApprovalScope, ApprovalStatus } from '@/types/api'
import { useApprovalDecision, useApprovals } from './api'

const tabs: { scope: ApprovalScope; label: string }[] = [
  { scope: 'TO_REVIEW', label: 'To review' },
  { scope: 'MINE', label: 'My requests' },
  { scope: 'ALL', label: 'History' },
]

type Pending = { approval: Approval; action: 'approve' | 'reject' | 'cancel' }

/** Holds the selected tab, filter and the action waiting for confirmation. */
export function ApprovalsPage() {
  const [params, setParams] = useSearchParams()
  const scope = (params.get('scope') ?? 'TO_REVIEW') as ApprovalScope
  const status = (params.get('status') ?? '') as ApprovalStatus | ''
  const page = Number(params.get('page') ?? 0)
  const { data, isPending, isError, error, refetch } = useApprovals({ scope, status, page })
  const decide = useApprovalDecision()
  const [pending, setPending] = useState<Pending | null>(null)

  const setParam = (key: string, value: string) => {
    const next = new URLSearchParams(params)
    if (value) next.set(key, value)
    else next.delete(key)
    if (key !== 'page') next.delete('page')
    setParams(next, { replace: true })
  }

  return (
    <div className="flex flex-col gap-6">
      <PageHeader title="Approvals" description="Task completion and budget change requests." />

      <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
        <div role="tablist" aria-label="Approval views" className="inline-flex rounded-lg border bg-card p-1">
          {tabs.map((tab) => (
            <button
              key={tab.scope}
              type="button"
              role="tab"
              aria-selected={scope === tab.scope}
              onClick={() => setParam('scope', tab.scope)}
              className={cn(
                'rounded-md px-3 py-1.5 text-sm transition-colors',
                scope === tab.scope
                  ? 'bg-accent font-medium text-accent-foreground'
                  : 'text-muted-foreground hover:text-foreground',
              )}
            >
              {tab.label}
            </button>
          ))}
        </div>
        {scope !== 'TO_REVIEW' && (
          <Select
            aria-label="Filter by status"
            className="sm:w-44"
            value={status}
            onChange={(e) => setParam('status', e.target.value)}
          >
            <option value="">Any status</option>
            {approvalStatuses.map((s) => (
              <option key={s} value={s}>
                {humanize(s)}
              </option>
            ))}
          </Select>
        )}
      </div>

      <Card className="gap-0 py-0">
        {isError ? (
          <ErrorState error={error} onRetry={() => void refetch()} />
        ) : isPending ? (
          <LoadingRows />
        ) : data.content.length === 0 ? (
          <EmptyState
            icon={CheckSquare}
            title={scope === 'TO_REVIEW' ? 'Nothing waiting for you' : 'No requests found'}
          />
        ) : (
          <>
            <ul className="divide-y">
              {data.content.map((a) => (
                <ApprovalRow key={a.id} approval={a} onAction={(action) => setPending({ approval: a, action })} />
              ))}
            </ul>
            <Pagination
              page={data.page}
              totalPages={data.totalPages}
              totalElements={data.totalElements}
              onPageChange={(p) => setParam('page', String(p))}
            />
          </>
        )}
      </Card>

      <ConfirmDialog
        open={!!pending}
        onOpenChange={(open) => !open && setPending(null)}
        title={pending ? `${humanize(pending.action)} request` : ''}
        description={
          pending?.action === 'approve'
            ? 'The change is applied immediately and the requester is notified.'
            : pending?.action === 'reject'
              ? 'The requester is notified with your comment.'
              : 'The request is withdrawn and cannot be decided anymore.'
        }
        confirmLabel={pending ? humanize(pending.action) : ''}
        destructive={pending?.action !== 'approve'}
        pending={decide.isPending}
        comment={
          pending?.action === 'cancel' ? undefined : { label: 'Comment', required: pending?.action === 'reject' }
        }
        onConfirm={(comment) => {
          if (!pending) return
          decide.mutate(
            { id: pending.approval.id, action: pending.action, comment: comment || undefined },
            {
              onSuccess: () => {
                toast.success(`Request ${pending.action === 'cancel' ? 'cancelled' : `${pending.action}d`}`)
                setPending(null)
              },
              onError: (e) => toast.error(errorMessage(e)),
            },
          )
        }}
      />
    </div>
  )
}

/** One request; buttons are shown only when the server says the user may use them. */
function ApprovalRow({ approval: a, onAction }: { approval: Approval; onAction: (action: Pending['action']) => void }) {
  return (
    <li className="flex flex-col gap-3 p-4 sm:flex-row sm:items-start sm:justify-between">
      <div className="min-w-0 space-y-1">
        <div className="flex flex-wrap items-center gap-2">
          <span className="font-medium">{a.type === 'TASK_COMPLETION' ? 'Task completion' : 'Budget change'}</span>
          <ApprovalStatusBadge status={a.status} />
        </div>
        <p className="text-sm text-muted-foreground">
          <Link to={`/projects/${a.project.id}`} className="hover:underline">
            {a.project.name}
          </Link>
          {a.task && (
            <>
              {' · '}
              <Link to={`/tasks/${a.task.id}`} className="hover:underline">
                {a.task.name}
              </Link>
            </>
          )}
          {a.requestedBudget != null && (
            <>
              {' · '}
              {formatCurrency(a.currentBudget)} →{' '}
              <span className="font-medium text-foreground">{formatCurrency(a.requestedBudget)}</span>
            </>
          )}
        </p>
        <p className="text-sm">{a.reason}</p>
        <p className="text-xs text-muted-foreground">
          Requested by {a.requester.fullName} · {formatDateTime(a.createdAt)}
          {a.reviewer && ` · ${humanize(a.status)} by ${a.reviewer.fullName} ${formatDateTime(a.decidedAt)}`}
        </p>
        {a.reviewComment && <p className="text-sm text-muted-foreground italic">“{a.reviewComment}”</p>}
      </div>
      <div className="flex shrink-0 gap-2">
        {a.canDecide && (
          <>
            <Button size="sm" variant="outline" onClick={() => onAction('reject')}>
              <X /> Reject
            </Button>
            <Button size="sm" onClick={() => onAction('approve')}>
              <Check /> Approve
            </Button>
          </>
        )}
        {a.canCancel && (
          <Button size="sm" variant="ghost" onClick={() => onAction('cancel')}>
            Cancel request
          </Button>
        )}
      </div>
    </li>
  )
}
