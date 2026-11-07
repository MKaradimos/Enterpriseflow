// Dialogs for submitting approval requests: a project budget change and a task completion.

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
import { Input, Textarea } from '@/components/ui/input'
import { errorMessage } from '@/lib/api-client'
import { formatCurrency } from '@/lib/format'
import type { Project, Task } from '@/types/api'
import { useSubmitApproval } from './api'

// Every request needs a reason, so the reviewer knows why it was asked.
const reasonField = z.string().trim().min(1, 'Explain the reason for the request').max(1000)

const budgetSchema = z.object({
  budget: z.string().regex(/^\d{1,12}(\.\d{1,2})?$/, 'Enter an amount like 15000 or 15000.50'),
  reason: reasonField,
})

/** Requests a new budget for a project; an admin must approve it. */
export function BudgetRequestDialog({
  project,
  open,
  onOpenChange,
}: {
  project: Project
  open: boolean
  onOpenChange: (open: boolean) => void
}) {
  const submit = useSubmitApproval()
  const {
    register,
    handleSubmit,
    reset,
    formState: { errors, isSubmitting },
  } = useForm<z.infer<typeof budgetSchema>>({ resolver: zodResolver(budgetSchema) })

  useEffect(() => {
    // Start from the current budget each time the dialog opens.
    if (open) reset({ budget: project.budget?.toString() ?? '', reason: '' })
  }, [open, project.budget, reset])

  const onSubmit = handleSubmit(async ({ budget, reason }) => {
    try {
      await submit.mutateAsync({
        type: 'PROJECT_BUDGET_CHANGE',
        projectId: project.id,
        requestedBudget: Number(budget),
        reason,
      })
      toast.success('Budget change submitted for approval')
      onOpenChange(false)
    } catch (error) {
      toast.error(errorMessage(error))
    }
  })

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Request budget change</DialogTitle>
          <DialogDescription>
            Current budget: {formatCurrency(project.budget)}. An administrator must approve the change.
          </DialogDescription>
        </DialogHeader>
        <form id="budget-form" onSubmit={onSubmit} noValidate className="grid gap-4">
          <FormField id="budget-amount" label="New budget (EUR)" error={errors.budget?.message}>
            <Input id="budget-amount" inputMode="decimal" aria-invalid={!!errors.budget} {...register('budget')} />
          </FormField>
          <FormField id="budget-reason" label="Reason" error={errors.reason?.message}>
            <Textarea id="budget-reason" aria-invalid={!!errors.reason} {...register('reason')} />
          </FormField>
        </form>
        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)}>
            Cancel
          </Button>
          <Button type="submit" form="budget-form" disabled={isSubmitting}>
            Submit request
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}

const completionSchema = z.object({ reason: reasonField })

/** Asks the project manager to approve completing a task that requires approval. */
export function CompletionRequestDialog({
  task,
  open,
  onOpenChange,
}: {
  task: Task
  open: boolean
  onOpenChange: (open: boolean) => void
}) {
  const submit = useSubmitApproval()
  const {
    register,
    handleSubmit,
    reset,
    formState: { errors, isSubmitting },
  } = useForm<z.infer<typeof completionSchema>>({ resolver: zodResolver(completionSchema) })

  useEffect(() => {
    if (open) reset({ reason: '' })
  }, [open, reset])

  const onSubmit = handleSubmit(async ({ reason }) => {
    try {
      await submit.mutateAsync({ type: 'TASK_COMPLETION', taskId: task.id, reason })
      toast.success('Completion request submitted')
      onOpenChange(false)
    } catch (error) {
      toast.error(errorMessage(error))
    }
  })

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Request completion approval</DialogTitle>
          <DialogDescription>
            The project manager reviews the request and completes the task on approval.
          </DialogDescription>
        </DialogHeader>
        <form id="completion-form" onSubmit={onSubmit} noValidate className="grid gap-4">
          <FormField id="completion-reason" label="What was done?" error={errors.reason?.message}>
            <Textarea id="completion-reason" aria-invalid={!!errors.reason} {...register('reason')} />
          </FormField>
        </form>
        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)}>
            Cancel
          </Button>
          <Button type="submit" form="completion-form" disabled={isSubmitting}>
            Submit request
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
