// Task page: details, workflow buttons, activity.

import { Archive, Pencil, ShieldCheck } from 'lucide-react'
import { useState, type ReactNode } from 'react'
import { Link, useNavigate, useParams } from 'react-router'
import { toast } from 'sonner'
import { ActivityList } from '@/components/activity-list'
import { ConfirmDialog } from '@/components/confirm-dialog'
import { ErrorState, LoadingRows, PageHeader } from '@/components/page'
import { PriorityBadge, TaskStatusBadge } from '@/components/status-badges'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { CompletionRequestDialog } from '@/features/approvals/request-dialogs'
import { useAuth } from '@/features/auth/use-auth'
import { errorMessage } from '@/lib/api-client'
import { formatDate, formatDateTime, humanize } from '@/lib/format'
import type { Task } from '@/types/api'
import { useArchiveTask, useChangeTaskStatus, useTask, useTaskActivity } from './api'
import { TaskFormDialog } from './task-form-dialog'

export function TaskDetailPage() {
  const { taskId = '' } = useParams()
  const task = useTask(taskId)

  if (task.isError) {
    return (
      <Card>
        <ErrorState error={task.error} onRetry={() => void task.refetch()} />
      </Card>
    )
  }
  if (task.isPending) return <LoadingRows rows={6} />
  return <TaskDetail task={task.data} />
}

function TaskDetail({ task }: { task: Task }) {
  const { user } = useAuth()
  const navigate = useNavigate()
  const activity = useTaskActivity(task.id)
  const changeStatus = useChangeTaskStatus()
  const archive = useArchiveTask()
  const [editOpen, setEditOpen] = useState(false)
  const [archiveOpen, setArchiveOpen] = useState(false)
  const [completionOpen, setCompletionOpen] = useState(false)

  // The server tells us which transitions this user may perform; the UI only offers those.
  // Approval tasks can't be set to DONE directly; from IN_REVIEW you ask for approval instead.
  const canRequestCompletion =
    task.requiresApproval &&
    task.status === 'IN_REVIEW' &&
    !task.archived &&
    (task.assignee?.id === user?.id || task.canEdit)

  return (
    <div className="flex flex-col gap-6">
      <PageHeader
        title={task.title}
        description={
          <>
            In{' '}
            <Link to={`/projects/${task.project.id}`} className="font-medium text-foreground hover:underline">
              {task.project.name}
            </Link>
          </>
        }
        actions={
          task.canEdit && (
            <>
              <Button variant="outline" onClick={() => setEditOpen(true)}>
                <Pencil /> Edit
              </Button>
              <Button variant="outline" onClick={() => setArchiveOpen(true)}>
                <Archive /> Archive
              </Button>
            </>
          )
        }
      />

      <div className="grid gap-4 lg:grid-cols-3">
        <Card className="lg:col-span-2">
          <CardHeader>
            <CardTitle>Details</CardTitle>
          </CardHeader>
          <CardContent className="grid gap-6">
            <p className="text-sm whitespace-pre-wrap text-muted-foreground">{task.description || 'No description.'}</p>
            <dl className="grid grid-cols-2 gap-x-6 gap-y-4 text-sm sm:grid-cols-3">
              <Detail label="Status">
                <TaskStatusBadge status={task.status} />
              </Detail>
              <Detail label="Priority">
                <PriorityBadge priority={task.priority} />
              </Detail>
              <Detail label="Due">
                <span className={task.overdue ? 'text-destructive' : undefined}>
                  {formatDate(task.dueDate)}
                  {task.overdue && ' (overdue)'}
                </span>
              </Detail>
              <Detail label="Assignee">{task.assignee?.fullName ?? 'Unassigned'}</Detail>
              <Detail label="Reporter">{task.reporter.fullName}</Detail>
              <Detail label="Completed">{formatDateTime(task.completedAt)}</Detail>
            </dl>
            {task.requiresApproval && (
              <p className="flex items-center gap-2 text-sm text-muted-foreground">
                <ShieldCheck className="size-4" aria-hidden />
                Completion requires approval by the project manager.
              </p>
            )}
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle>Workflow</CardTitle>
          </CardHeader>
          <CardContent className="flex flex-col gap-2">
            {task.allowedStatuses.length === 0 && !canRequestCompletion ? (
              <p className="text-sm text-muted-foreground">
                {task.archived ? 'This task is archived.' : 'You cannot change the status of this task.'}
              </p>
            ) : (
              <>
                <p className="text-sm text-muted-foreground">Move this task to:</p>
                {task.allowedStatuses.map((status) => (
                  <Button
                    key={status}
                    variant="outline"
                    className="justify-start"
                    disabled={changeStatus.isPending}
                    onClick={() =>
                      changeStatus.mutate(
                        { id: task.id, status },
                        {
                          onSuccess: () => toast.success(`Moved to ${humanize(status)}`),
                          onError: (e) => toast.error(errorMessage(e)),
                        },
                      )
                    }
                  >
                    {humanize(status)}
                  </Button>
                ))}
                {canRequestCompletion && (
                  <Button onClick={() => setCompletionOpen(true)}>
                    <ShieldCheck /> Request completion
                  </Button>
                )}
              </>
            )}
          </CardContent>
        </Card>
      </div>

      <Card>
        <CardHeader>
          <CardTitle>Activity</CardTitle>
        </CardHeader>
        <CardContent>
          <ActivityList entries={activity.data} isPending={activity.isPending} error={activity.error} />
        </CardContent>
      </Card>

      <TaskFormDialog open={editOpen} onOpenChange={setEditOpen} projectId={task.project.id} task={task} />
      <CompletionRequestDialog task={task} open={completionOpen} onOpenChange={setCompletionOpen} />
      <ConfirmDialog
        open={archiveOpen}
        onOpenChange={setArchiveOpen}
        title="Archive task"
        description="The task is removed from lists but its history is kept."
        confirmLabel="Archive"
        destructive
        pending={archive.isPending}
        onConfirm={() =>
          archive.mutate(task.id, {
            onSuccess: () => {
              toast.success('Task archived')
              navigate(`/projects/${task.project.id}`)
            },
            onError: (e) => toast.error(errorMessage(e)),
          })
        }
      />
    </div>
  )
}

function Detail({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className="grid gap-1">
      <dt className="text-xs text-muted-foreground">{label}</dt>
      <dd className="font-medium">{children}</dd>
    </div>
  )
}
