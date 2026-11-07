// User administration (ADMIN): search, create users, change roles and activate / deactivate accounts.

import { zodResolver } from '@hookform/resolvers/zod'
import { Plus, Search, Users } from 'lucide-react'
import { useEffect, useState } from 'react'
import { useForm } from 'react-hook-form'
import { useSearchParams } from 'react-router'
import { toast } from 'sonner'
import { z } from 'zod'
import { ConfirmDialog } from '@/components/confirm-dialog'
import { EmptyState, ErrorState, FormField, LoadingRows, PageHeader, Pagination } from '@/components/page'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Input, Select } from '@/components/ui/input'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { useAuth } from '@/features/auth/use-auth'
import { errorMessage } from '@/lib/api-client'
import { formatDate, humanize } from '@/lib/format'
import { useDebouncedValue } from '@/lib/use-debounced-value'
import { passwordSchema } from '@/lib/validation'
import type { RoleName, User } from '@/types/api'
import { useCreateUser, useUpdateRoles, useUpdateUserStatus, useUsers } from './api'

const allRoles: RoleName[] = ['ADMIN', 'MANAGER', 'EMPLOYEE']

/** User list with filters and per-user actions (roles, activate / deactivate). */
export function UsersPage() {
  const { user: me } = useAuth()
  const [params, setParams] = useSearchParams()
  const [search, setSearch] = useState(params.get('q') ?? '')
  const q = useDebouncedValue(search, 300)
  const role = (params.get('role') ?? '') as RoleName | ''
  const active = (params.get('active') ?? '') as 'true' | 'false' | ''
  const page = Number(params.get('page') ?? 0)
  const { data, isPending, isError, error, refetch, isPlaceholderData } = useUsers({ q, role, active, page })

  const [createOpen, setCreateOpen] = useState(false)
  const [rolesUser, setRolesUser] = useState<User | null>(null)
  const [statusUser, setStatusUser] = useState<User | null>(null)
  const updateStatus = useUpdateUserStatus()

  const setParam = (key: string, value: string) => {
    const next = new URLSearchParams(params)
    if (value) next.set(key, value)
    else next.delete(key)
    if (key !== 'page') next.delete('page')
    setParams(next, { replace: true })
  }

  return (
    <div className="flex flex-col gap-6">
      <PageHeader
        title="Users"
        description="Manage accounts, roles and access."
        actions={
          <Button onClick={() => setCreateOpen(true)}>
            <Plus /> New user
          </Button>
        }
      />

      <Card className="gap-0 py-0">
        <div className="flex flex-col gap-3 border-b p-4 sm:flex-row">
          <div className="relative flex-1">
            <Search className="absolute top-1/2 left-3 size-4 -translate-y-1/2 text-muted-foreground" aria-hidden />
            <Input
              aria-label="Search users"
              placeholder="Search by name or email…"
              className="pl-9"
              value={search}
              onChange={(e) => {
                setSearch(e.target.value)
                setParam('page', '')
              }}
            />
          </div>
          <Select
            aria-label="Filter by role"
            className="sm:w-40"
            value={role}
            onChange={(e) => setParam('role', e.target.value)}
          >
            <option value="">Any role</option>
            {allRoles.map((r) => (
              <option key={r} value={r}>
                {humanize(r)}
              </option>
            ))}
          </Select>
          <Select
            aria-label="Filter by status"
            className="sm:w-40"
            value={active}
            onChange={(e) => setParam('active', e.target.value)}
          >
            <option value="">Any status</option>
            <option value="true">Active</option>
            <option value="false">Deactivated</option>
          </Select>
        </div>

        {isError ? (
          <ErrorState error={error} onRetry={() => void refetch()} />
        ) : isPending ? (
          <LoadingRows />
        ) : data.content.length === 0 ? (
          <EmptyState icon={Users} title="No users found" />
        ) : (
          <>
            <Table className={isPlaceholderData ? 'opacity-60' : undefined}>
              <TableHeader>
                <TableRow>
                  <TableHead>Name</TableHead>
                  <TableHead>Roles</TableHead>
                  <TableHead>Status</TableHead>
                  <TableHead>Joined</TableHead>
                  <TableHead className="text-right">Actions</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {data.content.map((u) => (
                  <TableRow key={u.id}>
                    <TableCell>
                      <div className="font-medium">{u.fullName}</div>
                      <div className="text-xs text-muted-foreground">{u.email}</div>
                    </TableCell>
                    <TableCell>
                      <div className="flex flex-wrap gap-1">
                        {u.roles.map((r) => (
                          <Badge key={r} variant={r === 'ADMIN' ? 'accent' : 'secondary'}>
                            {humanize(r)}
                          </Badge>
                        ))}
                      </div>
                    </TableCell>
                    <TableCell>
                      <Badge variant={u.active ? 'success' : 'secondary'}>{u.active ? 'Active' : 'Deactivated'}</Badge>
                    </TableCell>
                    <TableCell className="text-muted-foreground">{formatDate(u.createdAt)}</TableCell>
                    <TableCell className="text-right">
                      <div className="flex justify-end gap-2">
                        <Button size="sm" variant="outline" onClick={() => setRolesUser(u)}>
                          Roles
                        </Button>
                        {u.id !== me?.id && (
                          <Button size="sm" variant="ghost" onClick={() => setStatusUser(u)}>
                            {u.active ? 'Deactivate' : 'Activate'}
                          </Button>
                        )}
                      </div>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
            <Pagination
              page={data.page}
              totalPages={data.totalPages}
              totalElements={data.totalElements}
              onPageChange={(p) => setParam('page', String(p))}
            />
          </>
        )}
      </Card>

      <CreateUserDialog open={createOpen} onOpenChange={setCreateOpen} />
      <RolesDialog user={rolesUser} onClose={() => setRolesUser(null)} />
      <ConfirmDialog
        open={!!statusUser}
        onOpenChange={(open) => !open && setStatusUser(null)}
        title={statusUser?.active ? 'Deactivate user' : 'Activate user'}
        description={
          statusUser?.active
            ? `${statusUser.fullName} will be signed out everywhere and can no longer log in.`
            : `${statusUser?.fullName ?? ''} will be able to log in again.`
        }
        confirmLabel={statusUser?.active ? 'Deactivate' : 'Activate'}
        destructive={statusUser?.active}
        pending={updateStatus.isPending}
        onConfirm={() => {
          if (!statusUser) return
          updateStatus.mutate(
            { id: statusUser.id, active: !statusUser.active },
            {
              onSuccess: () => {
                toast.success('User updated')
                setStatusUser(null)
              },
              onError: (e) => toast.error(errorMessage(e)),
            },
          )
        }}
      />
    </div>
  )
}

const createSchema = z.object({
  fullName: z.string().trim().min(1, 'Name is required').max(120),
  email: z.email('Enter a valid email address'),
  jobTitle: z.string().max(120).optional(),
  password: passwordSchema,
  role: z.enum(['ADMIN', 'MANAGER', 'EMPLOYEE']),
})

/** Form for creating a user with roles and an initial password. */
function CreateUserDialog({ open, onOpenChange }: { open: boolean; onOpenChange: (open: boolean) => void }) {
  const create = useCreateUser()
  const {
    register,
    handleSubmit,
    reset,
    formState: { errors, isSubmitting },
  } = useForm<z.infer<typeof createSchema>>({ resolver: zodResolver(createSchema) })

  useEffect(() => {
    if (open) reset({ fullName: '', email: '', jobTitle: '', password: '', role: 'EMPLOYEE' })
  }, [open, reset])

  const onSubmit = handleSubmit(async ({ role, ...values }) => {
    try {
      await create.mutateAsync({ ...values, jobTitle: values.jobTitle || undefined, roles: [role] })
      toast.success('User created')
      onOpenChange(false)
    } catch (error) {
      toast.error(errorMessage(error))
    }
  })

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>New user</DialogTitle>
          <DialogDescription>
            Share the initial password securely; the user can change it in their profile.
          </DialogDescription>
        </DialogHeader>
        <form id="user-form" onSubmit={onSubmit} noValidate className="grid gap-4">
          <FormField id="user-name" label="Full name" error={errors.fullName?.message}>
            <Input id="user-name" aria-invalid={!!errors.fullName} {...register('fullName')} />
          </FormField>
          <FormField id="user-email" label="Email" error={errors.email?.message}>
            <Input id="user-email" type="email" aria-invalid={!!errors.email} {...register('email')} />
          </FormField>
          <FormField id="user-title" label="Job title">
            <Input id="user-title" {...register('jobTitle')} />
          </FormField>
          <div className="grid gap-4 sm:grid-cols-2">
            <FormField id="user-password" label="Initial password" error={errors.password?.message}>
              <Input
                id="user-password"
                type="password"
                autoComplete="new-password"
                aria-invalid={!!errors.password}
                {...register('password')}
              />
            </FormField>
            <FormField id="user-role" label="Role">
              <Select id="user-role" {...register('role')}>
                {allRoles.map((r) => (
                  <option key={r} value={r}>
                    {humanize(r)}
                  </option>
                ))}
              </Select>
            </FormField>
          </div>
        </form>
        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)}>
            Cancel
          </Button>
          <Button type="submit" form="user-form" disabled={isSubmitting}>
            Create user
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}

/** Dialog wrapper for editing a user's roles. */
function RolesDialog({ user, onClose }: { user: User | null; onClose: () => void }) {
  return (
    <Dialog open={!!user} onOpenChange={(open) => !open && onClose()}>
      {/* Keyed by user: the form state starts fresh from the selected user's roles, no syncing effect needed. */}
      <DialogContent>{user && <RolesForm key={user.id} user={user} onClose={onClose} />}</DialogContent>
    </Dialog>
  )
}

function RolesForm({ user, onClose }: { user: User; onClose: () => void }) {
  const update = useUpdateRoles()
  const [roles, setRoles] = useState<RoleName[]>(user.roles)

  const toggle = (role: RoleName) =>
    setRoles((current) => (current.includes(role) ? current.filter((r) => r !== role) : [...current, role]))

  return (
    <>
      <DialogHeader>
        <DialogTitle>Roles for {user.fullName}</DialogTitle>
        <DialogDescription>
          Changing roles signs the user out so their next session uses the new roles.
        </DialogDescription>
      </DialogHeader>
      <fieldset className="grid gap-2">
        <legend className="sr-only">Roles</legend>
        {allRoles.map((role) => (
          <label key={role} className="flex items-center gap-2 text-sm">
            <input
              type="checkbox"
              className="size-4 accent-[var(--primary)]"
              checked={roles.includes(role)}
              onChange={() => toggle(role)}
            />
            {humanize(role)}
          </label>
        ))}
      </fieldset>
      <DialogFooter>
        <Button variant="outline" onClick={onClose}>
          Cancel
        </Button>
        <Button
          disabled={roles.length === 0 || update.isPending}
          onClick={() =>
            update.mutate(
              { id: user.id, roles },
              {
                onSuccess: () => {
                  toast.success('Roles updated')
                  onClose()
                },
                onError: (e) => toast.error(errorMessage(e)),
              },
            )
          }
        >
          Save roles
        </Button>
      </DialogFooter>
    </>
  )
}
