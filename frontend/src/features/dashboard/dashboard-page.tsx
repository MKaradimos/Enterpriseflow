// Dashboard: KPI cards, task charts, project progress, overdue tasks, approvals waiting for the user and
// recent activity. All data is already limited to what the user may see.

import { AlertTriangle, CheckCircle2, Clock, FolderKanban, ListChecks, type LucideIcon } from 'lucide-react'
import { Link } from 'react-router'
import { EmptyState, ErrorState, PageHeader } from '@/components/page'
import { PriorityBadge, ProgressBar, ProjectStatusBadge } from '@/components/status-badges'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'
import { useAuth } from '@/features/auth/use-auth'
import { formatDate, humanize, relativeTime, taskPriorities, taskStatuses } from '@/lib/format'
import { useDashboard } from './api'
import { CountBarChart } from './count-bar-chart'

/** Renders the dashboard sections from a single API response. */
export function DashboardPage() {
  const { user } = useAuth()
  // One request loads every section, so the numbers on the page are consistent with each other.
  const { data, isPending, isError, error, refetch } = useDashboard()

  return (
    <div className="flex flex-col gap-6">
      <PageHeader
        title={`Welcome back${user ? `, ${user.fullName.split(' ')[0]}` : ''}`}
        description="Overview of the projects you have access to."
      />

      {isError ? (
        <Card>
          <ErrorState error={error} onRetry={() => void refetch()} />
        </Card>
      ) : isPending ? (
        <DashboardSkeleton />
      ) : (
        <>
          <section aria-label="Key metrics" className="grid grid-cols-2 gap-4 lg:grid-cols-3 xl:grid-cols-6">
            <Kpi icon={FolderKanban} label="Total projects" value={data.kpis.totalProjects} />
            <Kpi icon={FolderKanban} label="Active projects" value={data.kpis.activeProjects} />
            <Kpi icon={ListChecks} label="Total tasks" value={data.kpis.totalTasks} />
            <Kpi icon={CheckCircle2} label="Completed tasks" value={data.kpis.completedTasks} />
            <Kpi icon={AlertTriangle} label="Overdue tasks" value={data.kpis.overdueTasks} tone="alert" />
            <Kpi icon={Clock} label="Pending approvals" value={data.kpis.pendingApprovals} />
          </section>

          <div className="grid gap-4 lg:grid-cols-2">
            <Card>
              <CardHeader>
                <CardTitle>Tasks by status</CardTitle>
                <CardDescription>All non-archived tasks</CardDescription>
              </CardHeader>
              <CardContent>
                <CountBarChart
                  title="Tasks by status"
                  data={taskStatuses.map((s) => ({ label: humanize(s), value: data.tasksByStatus[s] ?? 0 }))}
                />
              </CardContent>
            </Card>
            <Card>
              <CardHeader>
                <CardTitle>Open tasks by priority</CardTitle>
                <CardDescription>Tasks not yet done</CardDescription>
              </CardHeader>
              <CardContent>
                <CountBarChart
                  title="Open tasks by priority"
                  data={taskPriorities.map((p) => ({ label: humanize(p), value: data.tasksByPriority[p] ?? 0 }))}
                />
              </CardContent>
            </Card>
          </div>

          <div className="grid gap-4 lg:grid-cols-2">
            <Card>
              <CardHeader>
                <CardTitle>Project progress</CardTitle>
                <CardDescription>Most recently updated projects</CardDescription>
              </CardHeader>
              <CardContent>
                {data.projectProgress.length === 0 ? (
                  <EmptyState title="No projects yet" />
                ) : (
                  <ul className="flex flex-col gap-4">
                    {data.projectProgress.map((p) => (
                      <li key={p.id} className="grid gap-1.5">
                        <div className="flex items-center justify-between gap-2 text-sm">
                          <Link to={`/projects/${p.id}`} className="truncate font-medium hover:underline">
                            {p.name}
                          </Link>
                          <ProjectStatusBadge status={p.status} />
                        </div>
                        <ProgressBar value={p.progressPercent} label={`${p.name} progress`} />
                        <span className="text-xs text-muted-foreground">
                          {p.doneTasks} of {p.totalTasks} tasks done
                        </span>
                      </li>
                    ))}
                  </ul>
                )}
              </CardContent>
            </Card>

            <Card>
              <CardHeader>
                {/* The five most urgent overdue tasks, oldest due date first. */}
                <CardTitle>Overdue tasks</CardTitle>
                <CardDescription>Past their due date and not done</CardDescription>
              </CardHeader>
              <CardContent>
                {data.overdueTasks.length === 0 ? (
                  <EmptyState icon={CheckCircle2} title="Nothing overdue" description="All deadlines are on track." />
                ) : (
                  <ul className="divide-y">
                    {data.overdueTasks.map((t) => (
                      <li key={t.id} className="flex items-center justify-between gap-3 py-2.5 text-sm">
                        <div className="min-w-0">
                          <Link to={`/tasks/${t.id}`} className="block truncate font-medium hover:underline">
                            {t.title}
                          </Link>
                          <span className="text-xs text-muted-foreground">
                            {t.project.name} · due {formatDate(t.dueDate)}
                          </span>
                        </div>
                        <PriorityBadge priority={t.priority} />
                      </li>
                    ))}
                  </ul>
                )}
              </CardContent>
            </Card>
          </div>

          <div className="grid gap-4 lg:grid-cols-2">
            <Card>
              <CardHeader>
                {/* Pending approvals this user can decide; empty for employees. */}
                <CardTitle>Waiting for your review</CardTitle>
                <CardDescription>Pending approvals you can decide</CardDescription>
              </CardHeader>
              <CardContent>
                {data.approvalsToReview.length === 0 ? (
                  <EmptyState title="No pending approvals" />
                ) : (
                  <ul className="divide-y">
                    {data.approvalsToReview.map((a) => (
                      <li key={a.id} className="flex items-center justify-between gap-3 py-2.5 text-sm">
                        <div className="min-w-0">
                          <p className="truncate font-medium">
                            {a.task ? a.task.name : `Budget change · ${a.project.name}`}
                          </p>
                          <span className="text-xs text-muted-foreground">
                            {a.requester.fullName} · {relativeTime(a.createdAt)}
                          </span>
                        </div>
                        <Link to="/approvals" className="shrink-0 text-xs font-medium text-primary hover:underline">
                          Review
                        </Link>
                      </li>
                    ))}
                  </ul>
                )}
              </CardContent>
            </Card>

            <Card>
              <CardHeader>
                <CardTitle>Recent activity</CardTitle>
                <CardDescription>Latest changes across your projects</CardDescription>
              </CardHeader>
              <CardContent>
                {data.recentActivity.length === 0 ? (
                  <EmptyState title="No activity yet" />
                ) : (
                  <ul className="flex flex-col gap-3">
                    {data.recentActivity.map((entry) => (
                      <li key={entry.id} className="flex gap-3 text-sm">
                        <span className="mt-1.5 size-1.5 shrink-0 rounded-full bg-primary" aria-hidden />
                        <div>
                          <span className="font-medium">{entry.actorName ?? 'System'}</span>{' '}
                          <span className="text-muted-foreground">{humanize(entry.action).toLowerCase()}</span>
                          <div className="text-xs text-muted-foreground">{relativeTime(entry.occurredAt)}</div>
                        </div>
                      </li>
                    ))}
                  </ul>
                )}
              </CardContent>
            </Card>
          </div>
        </>
      )}
    </div>
  )
}

/** One KPI card; `alert` highlights numbers that need attention (e.g. overdue tasks). */
function Kpi({ icon: Icon, label, value, tone }: { icon: LucideIcon; label: string; value: number; tone?: 'alert' }) {
  return (
    <Card className="gap-2 py-4">
      <CardContent className="flex flex-col gap-2 px-4">
        <div className="flex items-center justify-between text-xs text-muted-foreground">
          {label}
          <Icon className={tone === 'alert' && value > 0 ? 'size-4 text-destructive' : 'size-4'} aria-hidden />
        </div>
        <span className="text-2xl font-semibold tabular-nums">{value}</span>
      </CardContent>
    </Card>
  )
}

/** Placeholder with the same layout as the dashboard, so the page does not jump when data arrives. */
function DashboardSkeleton() {
  return (
    <div className="flex flex-col gap-4" role="status" aria-label="Loading dashboard">
      <div className="grid grid-cols-2 gap-4 lg:grid-cols-3 xl:grid-cols-6">
        {Array.from({ length: 6 }, (_, i) => (
          <Skeleton key={i} className="h-24" />
        ))}
      </div>
      <div className="grid gap-4 lg:grid-cols-2">
        <Skeleton className="h-72" />
        <Skeleton className="h-72" />
      </div>
    </div>
  )
}
