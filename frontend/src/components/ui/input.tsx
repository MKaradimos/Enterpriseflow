import type { ComponentProps } from 'react'
import { cn } from '@/lib/utils'

const fieldStyles =
  'w-full rounded-md border border-input bg-card px-3 py-1.5 text-sm shadow-xs transition-colors placeholder:text-muted-foreground disabled:cursor-not-allowed disabled:opacity-50 aria-invalid:border-destructive'

function Input({ className, ...props }: ComponentProps<'input'>) {
  return <input data-slot="input" className={cn(fieldStyles, 'h-9', className)} {...props} />
}

function Textarea({ className, ...props }: ComponentProps<'textarea'>) {
  return <textarea data-slot="textarea" className={cn(fieldStyles, 'min-h-20 resize-y', className)} {...props} />
}

/** Native select: accessible and keyboard-friendly by default, styled to match the other inputs. */
function Select({ className, ...props }: ComponentProps<'select'>) {
  return <select data-slot="select" className={cn(fieldStyles, 'h-9 pr-8', className)} {...props} />
}

function Label({ className, ...props }: ComponentProps<'label'>) {
  return <label data-slot="label" className={cn('text-sm font-medium leading-none', className)} {...props} />
}

export { Input, Label, Select, Textarea }
