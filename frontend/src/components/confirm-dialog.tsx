import { useState, type ReactNode } from 'react'
import { Button } from '@/components/ui/button'
import { Textarea } from '@/components/ui/input'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'

/**
 * Confirmation for consequential actions (archive, approve, reject, deactivate). Optionally collects a comment,
 * which can be made mandatory.
 */
export function ConfirmDialog({
  open,
  onOpenChange,
  title,
  description,
  confirmLabel,
  destructive = false,
  pending = false,
  comment,
  onConfirm,
}: {
  open: boolean
  onOpenChange: (open: boolean) => void
  title: string
  description: ReactNode
  confirmLabel: string
  destructive?: boolean
  pending?: boolean
  comment?: { label: string; required: boolean }
  onConfirm: (comment: string) => void
}) {
  const [text, setText] = useState('')
  const missingComment = comment?.required && text.trim().length === 0

  return (
    <Dialog
      open={open}
      onOpenChange={(next) => {
        if (!next) setText('')
        onOpenChange(next)
      }}
    >
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{title}</DialogTitle>
          <DialogDescription>{description}</DialogDescription>
        </DialogHeader>
        {comment && (
          <div className="grid gap-1.5">
            <label htmlFor="confirm-comment" className="text-sm font-medium">
              {comment.label}
              {comment.required && <span className="text-destructive"> *</span>}
            </label>
            <Textarea id="confirm-comment" value={text} maxLength={1000} onChange={(e) => setText(e.target.value)} />
          </div>
        )}
        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)} disabled={pending}>
            Cancel
          </Button>
          <Button
            variant={destructive ? 'destructive' : 'default'}
            disabled={pending || missingComment}
            onClick={() => onConfirm(text.trim())}
          >
            {pending ? 'Working…' : confirmLabel}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
