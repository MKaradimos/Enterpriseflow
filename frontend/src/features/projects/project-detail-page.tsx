// Project page: overview, team, tasks, activity.

import { Archive, ArchiveRestore, Banknote, Pencil, Plus } from 'lucide-react'
import { useState, type ReactNode } from 'react'
import { useParams } from 'react-router'
import { toast } from 'sonner'
import { ActivityList } from '@/components/activity-list'
import { ConfirmDialog } from '@/components/confirm-dialog'
import { EmptyState, ErrorState, LoadingRows, PageHeader } from '@/components/page'
import { ProgressBar, ProjectStatusBadge } from '@/components/status-badges'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { BudgetRequestDialog } from '@/features/approvals/request-dialogs'
import { usePermissions } from '@/features/auth/use-auth'
import { useTasks } from '@/features/tasks/api'
import { TaskFormDialog } from '@/features/tasks/task-form-dialog'
import { TaskTable } from '@/features/tasks/task-table'
import { errorMessage } from '@/lib/api-client'
import { formatCurrency, formatDate } from '@/lib/format'
import { useProject, useProjectAction, useProjectActivity } from './api'
import { MembersPanel } from './members-panel'
import { ProjectFormDialog } from './project-form-dialog'

export function ProjectDetailPage() {
  const { projectId = '' } = useParams()
  const project = useProject(projectId)

  if (project.isError) {
    return (
      <Card>
        <ErrorState error={project.error} onRetry={() => void project.refetch()} />
      </Card>
    )
  }
  if (project.isPending) return <LoadingRows rows={6} />

  return <ProjectDetail key={project.data.id} project={project.data} />
}

function ProjectDetail({ project }: { project: NonNullable<ReturnType<typeof useProject>['data']> }) {
  const { isAdmin } = usePermissions()
  const [editOpen, setEditOpen] = useState(false)
  const [budgetOpen, setBudgetOpen] = useState(false)
  const [taskOpen, setTaskOpen] = useState(false)
  const [archiveOpen, setArchiveOpen] = useState(false)
  const archive = useProjectAction(project.id, 'archive')
  const restore = useProjectAction(project.id, 'restore')
  const tasks = useTasks({ projectId: project.id, page: 0, size: 50, sort: 'createdAt,desc' })
  const activity = useProjectActivity(project.id)

  const archived = project.status === 'ARCHIVED'
  // Archived projects are read-only for everyone.
  const canEdit = project.canManage && !archived

  return (
    <div className="flex flex-col gap-6">
      <PageHeader
        title={project.name}
        description={project.description ?? undefined}
        actions={
          <>
            {canEdit && (
              <>
                <Button variant="outline" onClick={() => setEditOpen(true)}>
                  <Pencil /> Edit
                </Button>
                <Button variant="outline" onClick={() => setBudgetOpen(true)}>
                  <Banknote /> Budget change
                </Button>
                <Button variant="outline" onClick={() => setArchiveOpen(true)}>
                  <Archive /> Archive
                </Button>
              </>
            )}
            {archived && isAdmin && (
              <Button
                variant="outline"
                disabled={restore.isPending}
                onClick={() =>
                  restore.mutate(undefined, {
                    onSuccess: () => toast.success('Project restored'),
                    onError: (e) => toast.error(errorMessage(e)),
                  })
                }
              >
                <ArchiveRestore /> Restore
              </Button>
            )}
          </>
        }
      />

      {archived && (
        <p className="rounded-md border border-warning/40 bg-warning/10 px-4 py-2 text-sm" role="status">
          This project is archived and read-only.
        </p>
      )}

      <div className="grid gap-4 lg:grid-cols-3">
        <Card className="lg:col-span-2">
          <CardHeader>
            <CardTitle>Overview</CardTitle>
          </CardHeader>
          <CardContent>
            <dl className="grid grid-cols-2 gap-x-6 gap-y-4 text-sm sm:grid-cols-3">
              <Detail label="Status">
                <ProjectStatusBadge status={project.status} />
              </Detail>
              <Detail label="Manager">{project.manager.fullName}</Detail>
              <Detail label="Budget">{formatCurrency(project.budget)}</Detail>
              <Detail label="Start">{formatDate(project.startDate)}</Detail>
              <Detail label="Due">{formatDate(project.dueDate)}</Detail>
              <Detail label="Members">{project.memberCount}</Detail>
            </dl>
            <div className="mt-6 grid gap-1.5">
              <span className="text-sm font-medium">Progress</span>
              <ProgressBar value={project.progressPercent} />
              <span className="text-xs text-muted-foreground">
                {project.doneTasks} of {project.totalTasks} tasks done
              </span>
            </div>
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle>Members</CardTitle>
          </CardHeader>
          <CardContent>
            <MembersPanel project={project} />
          </CardContent>
        </Card>
      </div>

      <Card className="gap-0 pb-0">
        <CardHeader className="flex flex-row items-center justify-between pb-4">
          <CardTitle>Tasks</CardTitle>
          {canEdit && (
            <Button size="sm" onClick={() => setTaskOpen(true)}>
              <Plus /> New task
            </Button>
          )}
        </CardHeader>
        {tasks.isError ? (
          <ErrorState error={tasks.error} onRetry={() => void tasks.refetch()} />
        ) : tasks.isPending ? (
          <LoadingRows />
        ) : tasks.data.content.length === 0 ? (
          <EmptyState
            title="No tasks yet"
            description={canEdit ? 'Create the first task for this project.' : undefined}
          />
        ) : (
          <TaskTable tasks={tasks.data.content} showProject={false} />
        )}
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>Activity</CardTitle>
        </CardHeader>
        <CardContent>
          <ActivityList entries={activity.data} isPending={activity.isPending} error={activity.error} />
        </CardContent>
      </Card>

      <ProjectFormDialog open={editOpen} onOpenChange={setEditOpen} project={project} />
      <BudgetRequestDialog project={project} open={budgetOpen} onOpenChange={setBudgetOpen} />
      <TaskFormDialog open={taskOpen} onOpenChange={setTaskOpen} projectId={project.id} />
      <ConfirmDialog
        open={archiveOpen}
        onOpenChange={setArchiveOpen}
        title="Archive project"
        description="The project and its tasks become read-only. Only an administrator can restore it."
        confirmLabel="Archive"
        destructive
        pending={archive.isPending}
        onConfirm={() =>
          archive.mutate(undefined, {
            onSuccess: () => {
              toast.success('Project archived')
              setArchiveOpen(false)
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
