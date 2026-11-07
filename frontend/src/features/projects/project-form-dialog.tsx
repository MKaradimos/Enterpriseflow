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
import { errorMessage } from '@/lib/api-client'
import { humanize, projectStatuses } from '@/lib/format'
import type { Project, ProjectStatus } from '@/types/api'
import { useCreateProject, useUpdateProject } from './api'

const schema = z
  .object({
    name: z.string().trim().min(1, 'Name is required').max(150),
    description: z.string().max(5000).optional(),
    status: z.enum(['PLANNED', 'ACTIVE', 'ON_HOLD', 'COMPLETED']),
    budget: z
      .string()
      .regex(/^\d{0,12}(\.\d{1,2})?$/, 'Enter an amount like 15000 or 15000.50')
      .optional(),
    startDate: z.string().optional(),
    dueDate: z.string().optional(),
  })
  // Same rule as the backend (and the database CHECK), checked early for a better experience.
  .refine((v) => !v.startDate || !v.dueDate || v.dueDate >= v.startDate, {
    path: ['dueDate'],
    message: 'Due date cannot be before the start date',
  })

type Values = z.infer<typeof schema>

const editableStatuses = projectStatuses.filter((s) => s !== 'ARCHIVED') as Exclude<ProjectStatus, 'ARCHIVED'>[]

export function ProjectFormDialog({
  open,
  onOpenChange,
  project,
  onSaved,
}: {
  open: boolean
  onOpenChange: (open: boolean) => void
  /** Present when editing; absent when creating. */
  project?: Project
  onSaved?: (project: Project) => void
}) {
  const create = useCreateProject()
  const update = useUpdateProject(project?.id ?? '')
  const editing = !!project

  const {
    register,
    handleSubmit,
    reset,
    formState: { errors, isSubmitting },
  } = useForm<Values>({ resolver: zodResolver(schema) })

  useEffect(() => {
    if (open) {
      reset({
        name: project?.name ?? '',
        description: project?.description ?? '',
        status: project && project.status !== 'ARCHIVED' ? project.status : 'PLANNED',
        budget: project?.budget?.toString() ?? '',
        startDate: project?.startDate ?? '',
        dueDate: project?.dueDate ?? '',
      })
    }
  }, [open, project, reset])

  const onSubmit = handleSubmit(async (values) => {
    const common = {
      name: values.name,
      description: values.description || null,
      startDate: values.startDate || null,
      dueDate: values.dueDate || null,
    }
    try {
      const saved = editing
        ? await update.mutateAsync({ ...common, status: values.status })
        : await create.mutateAsync({ ...common, budget: values.budget ? Number(values.budget) : null })
      toast.success(editing ? 'Project updated' : 'Project created')
      onOpenChange(false)
      onSaved?.(saved)
    } catch (error) {
      toast.error(errorMessage(error))
    }
  })

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{editing ? 'Edit project' : 'New project'}</DialogTitle>
          <DialogDescription>
            {editing
              ? 'Budget changes go through an approval request.'
              : 'You will be the project manager. You can add members afterwards.'}
          </DialogDescription>
        </DialogHeader>
        <form id="project-form" onSubmit={onSubmit} noValidate className="grid gap-4">
          <FormField id="project-name" label="Name" error={errors.name?.message}>
            <Input id="project-name" aria-invalid={!!errors.name} {...register('name')} />
          </FormField>
          <FormField id="project-description" label="Description" error={errors.description?.message}>
            <Textarea id="project-description" {...register('description')} />
          </FormField>
          {editing ? (
            <FormField id="project-status" label="Status">
              <Select id="project-status" {...register('status')}>
                {editableStatuses.map((s) => (
                  <option key={s} value={s}>
                    {humanize(s)}
                  </option>
                ))}
              </Select>
            </FormField>
          ) : (
            <FormField id="project-budget" label="Budget (EUR)" error={errors.budget?.message}>
              <Input id="project-budget" inputMode="decimal" aria-invalid={!!errors.budget} {...register('budget')} />
            </FormField>
          )}
          <div className="grid gap-4 sm:grid-cols-2">
            <FormField id="project-start" label="Start date">
              <Input id="project-start" type="date" {...register('startDate')} />
            </FormField>
            <FormField id="project-due" label="Due date" error={errors.dueDate?.message}>
              <Input id="project-due" type="date" aria-invalid={!!errors.dueDate} {...register('dueDate')} />
            </FormField>
          </div>
        </form>
        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)}>
            Cancel
          </Button>
          <Button type="submit" form="project-form" disabled={isSubmitting}>
            {isSubmitting ? 'Saving…' : editing ? 'Save changes' : 'Create project'}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
