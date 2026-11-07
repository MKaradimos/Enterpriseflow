// Projects list with search, status filter, sorting and pagination; managers and admins can create projects.

import { FolderKanban, Plus, Search } from 'lucide-react'
import { useState } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router'
import { EmptyState, ErrorState, LoadingRows, PageHeader, Pagination } from '@/components/page'
import { ProgressBar, ProjectStatusBadge } from '@/components/status-badges'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { Input, Select } from '@/components/ui/input'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { usePermissions } from '@/features/auth/use-auth'
import { formatDate, humanize, projectStatuses } from '@/lib/format'
import { useDebouncedValue } from '@/lib/use-debounced-value'
import type { ProjectStatus } from '@/types/api'
import { useProjects } from './api'
import { ProjectFormDialog } from './project-form-dialog'

/** Project list page. */
export function ProjectsPage() {
  const { canCreateProjects } = usePermissions()
  const navigate = useNavigate()
  // Filters live in the URL, so a filtered view can be bookmarked, shared and survives a reload.
  const [params, setParams] = useSearchParams()
  const [search, setSearch] = useState(params.get('q') ?? '')
  const [createOpen, setCreateOpen] = useState(false)

  const q = useDebouncedValue(search, 300)
  const status = (params.get('status') ?? '') as ProjectStatus | ''
  const page = Number(params.get('page') ?? 0)

  const { data, isPending, isError, error, refetch, isPlaceholderData } = useProjects({
    q,
    status,
    includeArchived: status === 'ARCHIVED',
    page,
  })

  const updateParam = (key: string, value: string) => {
    const next = new URLSearchParams(params)
    if (value) next.set(key, value)
    else next.delete(key)
    if (key !== 'page') next.delete('page')
    setParams(next, { replace: true })
  }

  return (
    <div className="flex flex-col gap-6">
      <PageHeader
        title="Projects"
        description="Projects you manage or are a member of."
        actions={
          canCreateProjects && (
            <Button onClick={() => setCreateOpen(true)}>
              <Plus /> New project
            </Button>
          )
        }
      />

      <Card className="gap-0 py-0">
        <div className="flex flex-col gap-3 border-b p-4 sm:flex-row">
          <div className="relative flex-1">
            <Search className="absolute top-1/2 left-3 size-4 -translate-y-1/2 text-muted-foreground" aria-hidden />
            <Input
              aria-label="Search projects"
              placeholder="Search by name…"
              className="pl-9"
              value={search}
              onChange={(e) => {
                setSearch(e.target.value)
                updateParam('page', '')
              }}
            />
          </div>
          <Select
            aria-label="Filter by status"
            className="sm:w-48"
            value={status}
            onChange={(e) => updateParam('status', e.target.value)}
          >
            <option value="">All active statuses</option>
            {projectStatuses.map((s) => (
              <option key={s} value={s}>
                {humanize(s)}
              </option>
            ))}
          </Select>
        </div>

        {isError ? (
          <ErrorState error={error} onRetry={() => void refetch()} />
        ) : isPending ? (
          <LoadingRows />
        ) : data.content.length === 0 ? (
          <EmptyState
            icon={FolderKanban}
            title="No projects found"
            description={q || status ? 'Try different filters.' : 'Projects you join will appear here.'}
          />
        ) : (
          <>
            <Table className={isPlaceholderData ? 'opacity-60' : undefined}>
              <TableHeader>
                <TableRow>
                  <TableHead>Name</TableHead>
                  <TableHead>Status</TableHead>
                  <TableHead>Manager</TableHead>
                  <TableHead className="w-48">Progress</TableHead>
                  <TableHead>Members</TableHead>
                  <TableHead>Due</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {data.content.map((p) => (
                  <TableRow key={p.id} className="cursor-pointer" onClick={() => navigate(`/projects/${p.id}`)}>
                    <TableCell className="font-medium">
                      {/* Real link for keyboard and screen-reader users; the row click is a mouse shortcut. */}
                      <Link to={`/projects/${p.id}`} className="hover:underline" onClick={(e) => e.stopPropagation()}>
                        {p.name}
                      </Link>
                    </TableCell>
                    <TableCell>
                      <ProjectStatusBadge status={p.status} />
                    </TableCell>
                    <TableCell className="text-muted-foreground">{p.manager.fullName}</TableCell>
                    <TableCell>
                      <ProgressBar value={p.progressPercent} label={`${p.name} progress`} />
                    </TableCell>
                    <TableCell className="tabular-nums">{p.memberCount}</TableCell>
                    <TableCell className="text-muted-foreground">{formatDate(p.dueDate)}</TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
            <Pagination
              page={data.page}
              totalPages={data.totalPages}
              totalElements={data.totalElements}
              onPageChange={(p) => updateParam('page', String(p))}
            />
          </>
        )}
      </Card>

      <ProjectFormDialog
        open={createOpen}
        onOpenChange={setCreateOpen}
        onSaved={(p) => navigate(`/projects/${p.id}`)}
      />
    </div>
  )
}
