// Project team panel: lists members and lets the project manager add or remove them.

import { UserMinus, UserPlus } from 'lucide-react'
import { useState } from 'react'
import { toast } from 'sonner'
import { ConfirmDialog } from '@/components/confirm-dialog'
import { ErrorState, LoadingRows } from '@/components/page'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { useUserLookup } from '@/features/users/api'
import { errorMessage } from '@/lib/api-client'
import { useDebouncedValue } from '@/lib/use-debounced-value'
import type { Project, ProjectMember } from '@/types/api'
import { useAddMember, useProjectMembers, useRemoveMember } from './api'

/** Team list; add / remove controls only when the user can manage the project. */
export function MembersPanel({ project }: { project: Project }) {
  const members = useProjectMembers(project.id)
  const remove = useRemoveMember(project.id)
  const [toRemove, setToRemove] = useState<ProjectMember | null>(null)
  const canEdit = project.canManage && project.status !== 'ARCHIVED'

  if (members.isError) return <ErrorState error={members.error} onRetry={() => void members.refetch()} />
  if (members.isPending) return <LoadingRows rows={3} />

  return (
    <div className="flex flex-col gap-4">
      {canEdit && <AddMember projectId={project.id} existing={members.data.map((m) => m.userId)} />}
      <ul className="divide-y">
        {members.data.map((m) => (
          <li key={m.userId} className="flex items-center justify-between gap-3 py-2.5 text-sm">
            <div className="min-w-0">
              <p className="truncate font-medium">{m.fullName}</p>
              <p className="truncate text-xs text-muted-foreground">{m.email}</p>
            </div>
            <div className="flex items-center gap-2">
              {m.manager && <Badge variant="accent">Manager</Badge>}
              {canEdit && !m.manager && (
                <Button variant="ghost" size="icon" aria-label={`Remove ${m.fullName}`} onClick={() => setToRemove(m)}>
                  <UserMinus />
                </Button>
              )}
            </div>
          </li>
        ))}
      </ul>

      <ConfirmDialog
        open={!!toRemove}
        onOpenChange={(open) => !open && setToRemove(null)}
        title="Remove member"
        description={`${toRemove?.fullName ?? ''} will lose access to this project. Their open tasks become unassigned.`}
        confirmLabel="Remove"
        destructive
        pending={remove.isPending}
        onConfirm={() => {
          if (!toRemove) return
          remove.mutate(toRemove.userId, {
            onSuccess: () => {
              toast.success('Member removed')
              setToRemove(null)
            },
            onError: (error) => toast.error(errorMessage(error)),
          })
        }}
      />
    </div>
  )
}

/** Search box for active users who are not already members. */
function AddMember({ projectId, existing }: { projectId: string; existing: string[] }) {
  const [query, setQuery] = useState('')
  const q = useDebouncedValue(query.trim(), 250)
  const lookup = useUserLookup(q, q.length >= 2)
  const add = useAddMember(projectId)
  const candidates = (lookup.data ?? []).filter((u) => !existing.includes(u.id))

  return (
    <div className="grid gap-2">
      <Input
        aria-label="Find people to add"
        placeholder="Add people by name or email…"
        value={query}
        onChange={(e) => setQuery(e.target.value)}
      />
      {q.length >= 2 && (
        <ul className="rounded-md border" aria-label="Matching users">
          {lookup.isPending ? (
            <li className="px-3 py-2 text-sm text-muted-foreground">Searching…</li>
          ) : candidates.length === 0 ? (
            <li className="px-3 py-2 text-sm text-muted-foreground">No matching users</li>
          ) : (
            candidates.map((u) => (
              <li key={u.id} className="flex items-center justify-between gap-2 px-3 py-2 text-sm">
                <span className="min-w-0 truncate">
                  {u.fullName} <span className="text-muted-foreground">· {u.email}</span>
                </span>
                <Button
                  size="sm"
                  variant="outline"
                  disabled={add.isPending}
                  onClick={() =>
                    add.mutate(u.id, {
                      onSuccess: () => {
                        toast.success(`${u.fullName} added`)
                        setQuery('')
                      },
                      onError: (error) => toast.error(errorMessage(error)),
                    })
                  }
                >
                  <UserPlus /> Add
                </Button>
              </li>
            ))
          )}
        </ul>
      )}
    </div>
  )
}
