import { zodResolver } from '@hookform/resolvers/zod'
import { useEffect } from 'react'
import { useForm } from 'react-hook-form'
import { toast } from 'sonner'
import { z } from 'zod'
import { FormField } from '@/components/page'
import { Button } from '@/components/ui/button'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Input, Select, Textarea } from '@/components/ui/input'
import { useProjectMembers } from '@/features/projects/api'
import { errorMessage } from '@/lib/api-client'
import { humanize, taskPriorities } from '@/lib/format'
import type { Task } from '@/types/api'
import { useCreateTask, useUpdateTask } from './api'

// Same limits as the backend, which checks again anyway.
const taskSchema = z.object({
  title: z.string().trim().min(1, 'Title is required').max(200),
  description: z.string().max(10000).optional(),
  priority: z.enum(['LOW', 'MEDIUM', 'HIGH', 'CRITICAL']),
  assigneeId: z.string().optional(),
  dueDate: z.string().optional(),
  requiresApproval: z.boolean(),
})

type Values = z.infer<typeof taskSchema>

export function TaskFormDialog({
  open,
  onOpenChange,
  projectId,
  task,
}: {
  open: boolean
  onOpenChange: (open: boolean) => void
  projectId: string
  task?: Task
}) {
  const editing = !!task
  // Assignees come from the project's members, matching the server rule that assignees must be members.
  const members = useProjectMembers(projectId)
  const create = useCreateTask(projectId)
  const update = useUpdateTask(task?.id ?? '')

  const {
    register,
    handleSubmit,
    reset,
    formState: { errors, isSubmitting },
  } = useForm<Values>({ resolver: zodResolver(taskSchema) })

  useEffect(() => {
    if (open) {
      reset({
        title: task?.title ?? '',
        description: task?.description ?? '',
        priority: task?.priority ?? 'MEDIUM',
        assigneeId: task?.assignee?.id ?? '',
        dueDate: task?.dueDate ?? '',
        requiresApproval: task?.requiresApproval ?? false,
      })
    }
  }, [open, task, reset])

  const onSubmit = handleSubmit(async (values) => {
    const input = {
      title: values.title,
      description: values.description || null,
      priority: values.priority,
      assigneeId: values.assigneeId || null,
      dueDate: values.dueDate || null,
      requiresApproval: values.requiresApproval,
    }
    try {
      await (editing ? update.mutateAsync(input) : create.mutateAsync(input))
      toast.success(editing ? 'Task updated' : 'Task created')
      onOpenChange(false)
    } catch (error) {
      toast.error(errorMessage(error))
    }
  })

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{editing ? 'Edit task' : 'New task'}</DialogTitle>
          <DialogDescription>Assignees must be members of the project.</DialogDescription>
        </DialogHeader>
        <form id="task-form" onSubmit={onSubmit} noValidate className="grid gap-4">
          <FormField id="task-title" label="Title" error={errors.title?.message}>
            <Input id="task-title" aria-invalid={!!errors.title} {...register('title')} />
          </FormField>
          <FormField id="task-description" label="Description">
            <Textarea id="task-description" {...register('description')} />
          </FormField>
          <div className="grid gap-4 sm:grid-cols-2">
            <FormField id="task-priority" label="Priority">
              <Select id="task-priority" {...register('priority')}>
                {taskPriorities.map((p) => (
                  <option key={p} value={p}>
                    {humanize(p)}
                  </option>
                ))}
              </Select>
            </FormField>
            <FormField id="task-due" label="Due date">
              <Input id="task-due" type="date" {...register('dueDate')} />
            </FormField>
          </div>
          <FormField id="task-assignee" label="Assignee">
            <Select id="task-assignee" disabled={members.isPending} {...register('assigneeId')}>
              <option value="">Unassigned</option>
              {members.data?.map((m) => (
                <option key={m.userId} value={m.userId}>
                  {m.fullName}
                </option>
              ))}
            </Select>
          </FormField>
          <label className="flex items-start gap-2 text-sm">
            <input
              type="checkbox"
              className="mt-0.5 size-4 accent-[var(--primary)]"
              {...register('requiresApproval')}
            />
            <span>
              <span className="font-medium">Requires approval to complete</span>
              <span className="block text-xs text-muted-foreground">
                The task can only move to Done through an approved completion request.
              </span>
            </span>
          </label>
        </form>
        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)}>
            Cancel
          </Button>
          <Button type="submit" form="task-form" disabled={isSubmitting}>
            {isSubmitting ? 'Saving…' : editing ? 'Save changes' : 'Create task'}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
