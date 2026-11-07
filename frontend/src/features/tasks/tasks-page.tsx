// Tasks page: all tasks the user can see, with filters kept in the URL, sorting and pagination.

import { ListChecks, Search } from 'lucide-react'
import { useState } from 'react'
import { useSearchParams } from 'react-router'
import { EmptyState, ErrorState, LoadingRows, PageHeader, Pagination } from '@/components/page'
import { Card } from '@/components/ui/card'
import { Input, Select } from '@/components/ui/input'
import { humanize, taskPriorities, taskStatuses } from '@/lib/format'
import { useDebouncedValue } from '@/lib/use-debounced-value'
import type { TaskPriority, TaskStatus } from '@/types/api'
import { useTasks } from './api'
import { TaskTable } from './task-table'

const sortOptions = [
  { value: 'updatedAt,desc', label: 'Recently updated' },
  { value: 'dueDate,asc', label: 'Due date' },
  { value: 'createdAt,desc', label: 'Newest' },
  { value: 'title,asc', label: 'Title' },
]

/** Task list page; filter values are read from and written to the URL query string. */
export function TasksPage() {
  const [params, setParams] = useSearchParams()
  const [search, setSearch] = useState(params.get('q') ?? '')
  const q = useDebouncedValue(search, 300)

  const filters = {
    q,
    status: (params.get('status') ?? '') as TaskStatus | '',
    priority: (params.get('priority') ?? '') as TaskPriority | '',
    assignedToMe: params.get('mine') === 'true',
    overdue: params.get('overdue') === 'true',
    sort: params.get('sort') ?? 'updatedAt,desc',
    page: Number(params.get('page') ?? 0),
  }
  const { data, isPending, isError, error, refetch, isPlaceholderData } = useTasks(filters)

  const setParam = (key: string, value: string) => {
    const next = new URLSearchParams(params)
    if (value) next.set(key, value)
    else next.delete(key)
    if (key !== 'page') next.delete('page')
    setParams(next, { replace: true })
  }

  const hasFilters = !!(q || filters.status || filters.priority || filters.assignedToMe || filters.overdue)

  return (
    <div className="flex flex-col gap-6">
      <PageHeader title="Tasks" description="Tasks across all projects you can access." />

      <Card className="gap-0 py-0">
        <div className="grid gap-3 border-b p-4 md:grid-cols-[1fr_auto_auto_auto]">
          <div className="relative">
            <Search className="absolute top-1/2 left-3 size-4 -translate-y-1/2 text-muted-foreground" aria-hidden />
            <Input
              aria-label="Search tasks"
              placeholder="Search by title…"
              className="pl-9"
              value={search}
              onChange={(e) => {
                setSearch(e.target.value)
                setParam('page', '')
              }}
            />
          </div>
          <Select
            aria-label="Filter by status"
            value={filters.status}
            onChange={(e) => setParam('status', e.target.value)}
          >
            <option value="">Any status</option>
            {taskStatuses.map((s) => (
              <option key={s} value={s}>
                {humanize(s)}
              </option>
            ))}
          </Select>
          <Select
            aria-label="Filter by priority"
            value={filters.priority}
            onChange={(e) => setParam('priority', e.target.value)}
          >
            <option value="">Any priority</option>
            {taskPriorities.map((p) => (
              <option key={p} value={p}>
                {humanize(p)}
              </option>
            ))}
          </Select>
          <Select aria-label="Sort tasks" value={filters.sort} onChange={(e) => setParam('sort', e.target.value)}>
            {sortOptions.map((o) => (
              <option key={o.value} value={o.value}>
                {o.label}
              </option>
            ))}
          </Select>
          <div className="flex flex-wrap gap-4 text-sm md:col-span-4">
            <label className="flex items-center gap-2">
              <input
                type="checkbox"
                className="size-4 accent-[var(--primary)]"
                checked={filters.assignedToMe}
                onChange={(e) => setParam('mine', e.target.checked ? 'true' : '')}
              />
              Assigned to me
            </label>
            <label className="flex items-center gap-2">
              <input
                type="checkbox"
                className="size-4 accent-[var(--primary)]"
                checked={filters.overdue}
                onChange={(e) => setParam('overdue', e.target.checked ? 'true' : '')}
              />
              Overdue only
            </label>
          </div>
        </div>

        {isError ? (
          <ErrorState error={error} onRetry={() => void refetch()} />
        ) : isPending ? (
          <LoadingRows />
        ) : data.content.length === 0 ? (
          <EmptyState
            icon={ListChecks}
            title="No tasks found"
            description={hasFilters ? 'Try removing some filters.' : 'Tasks assigned in your projects appear here.'}
          />
        ) : (
          <>
            <TaskTable tasks={data.content} dimmed={isPlaceholderData} />
            <Pagination
              page={data.page}
              totalPages={data.totalPages}
              totalElements={data.totalElements}
              onPageChange={(p) => setParam('page', String(p))}
            />
          </>
        )}
      </Card>
    </div>
  )
}
