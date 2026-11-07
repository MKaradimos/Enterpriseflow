import { Badge, type BadgeVariant } from '@/components/ui/badge'
import { humanize } from '@/lib/format'
import type { ApprovalStatus, ProjectStatus, TaskPriority, TaskStatus } from '@/types/api'

const taskStatusVariant: Record<TaskStatus, BadgeVariant> = {
  TODO: 'secondary',
  IN_PROGRESS: 'accent',
  IN_REVIEW: 'warning',
  DONE: 'success',
  BLOCKED: 'destructive',
}

const priorityVariant: Record<TaskPriority, BadgeVariant> = {
  LOW: 'secondary',
  MEDIUM: 'outline',
  HIGH: 'warning',
  CRITICAL: 'destructive',
}

const projectStatusVariant: Record<ProjectStatus, BadgeVariant> = {
  PLANNED: 'secondary',
  ACTIVE: 'accent',
  ON_HOLD: 'warning',
  COMPLETED: 'success',
  ARCHIVED: 'outline',
}

const approvalStatusVariant: Record<ApprovalStatus, BadgeVariant> = {
  PENDING: 'warning',
  APPROVED: 'success',
  REJECTED: 'destructive',
  CANCELLED: 'secondary',
}

export function TaskStatusBadge({ status }: { status: TaskStatus }) {
  return <Badge variant={taskStatusVariant[status]}>{humanize(status)}</Badge>
}

export function PriorityBadge({ priority }: { priority: TaskPriority }) {
  return <Badge variant={priorityVariant[priority]}>{humanize(priority)}</Badge>
}

export function ProjectStatusBadge({ status }: { status: ProjectStatus }) {
  return <Badge variant={projectStatusVariant[status]}>{humanize(status)}</Badge>
}

export function ApprovalStatusBadge({ status }: { status: ApprovalStatus }) {
  return <Badge variant={approvalStatusVariant[status]}>{humanize(status)}</Badge>
}

export function ProgressBar({ value, label }: { value: number; label?: string }) {
  return (
    <div className="flex items-center gap-2">
      <div
        role="progressbar"
        aria-valuenow={value}
        aria-valuemin={0}
        aria-valuemax={100}
        aria-label={label ?? 'Progress'}
        className="h-1.5 w-full min-w-16 overflow-hidden rounded-full bg-muted"
      >
        <div className="h-full rounded-full bg-primary transition-all" style={{ width: `${value}%` }} />
      </div>
      <span className="w-9 text-right text-xs tabular-nums text-muted-foreground">{value}%</span>
    </div>
  )
}
