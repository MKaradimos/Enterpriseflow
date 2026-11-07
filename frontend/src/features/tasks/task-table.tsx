import { AlertTriangle, ShieldCheck } from 'lucide-react'
import { Link, useNavigate } from 'react-router'
import { PriorityBadge, TaskStatusBadge } from '@/components/status-badges'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { formatDate } from '@/lib/format'
import type { Task } from '@/types/api'

export function TaskTable({
  tasks,
  showProject = true,
  dimmed,
}: {
  tasks: Task[]
  showProject?: boolean
  dimmed?: boolean
}) {
  const navigate = useNavigate()
  return (
    <Table className={dimmed ? 'opacity-60' : undefined}>
      <TableHeader>
        <TableRow>
          <TableHead>Task</TableHead>
          {showProject && <TableHead>Project</TableHead>}
          <TableHead>Status</TableHead>
          <TableHead>Priority</TableHead>
          <TableHead>Assignee</TableHead>
          <TableHead>Due</TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        {tasks.map((t) => (
          <TableRow key={t.id} className="cursor-pointer" onClick={() => navigate(`/tasks/${t.id}`)}>
            <TableCell className="max-w-xs font-medium">
              <div className="flex items-center gap-1.5">
                <Link to={`/tasks/${t.id}`} className="truncate hover:underline" onClick={(e) => e.stopPropagation()}>
                  {t.title}
                </Link>
                {t.requiresApproval && (
                  <ShieldCheck className="size-3.5 shrink-0 text-muted-foreground" aria-label="Requires approval" />
                )}
              </div>
            </TableCell>
            {showProject && <TableCell className="text-muted-foreground">{t.project.name}</TableCell>}
            <TableCell>
              <TaskStatusBadge status={t.status} />
            </TableCell>
            <TableCell>
              <PriorityBadge priority={t.priority} />
            </TableCell>
            <TableCell className="text-muted-foreground">{t.assignee?.fullName ?? 'Unassigned'}</TableCell>
            <TableCell className={t.overdue ? 'font-medium text-destructive' : 'text-muted-foreground'}>
              <span className="flex items-center gap-1">
                {/* Icon + text, not color alone, so "overdue" is perceivable without color vision. */}
                {t.overdue && <AlertTriangle className="size-3.5" aria-hidden />}
                {formatDate(t.dueDate)}
                {t.overdue && <span className="sr-only">(overdue)</span>}
              </span>
            </TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  )
}
