import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '@/lib/api-client'
import { cleanParams, queryKeys } from '@/lib/query'
import type { Approval, ApprovalScope, ApprovalStatus, ApprovalType, Page } from '@/types/api'

export interface ApprovalFilters {
  scope: ApprovalScope
  status?: ApprovalStatus | ''
  page: number
}

export interface SubmitApprovalInput {
  type: ApprovalType
  projectId?: string
  taskId?: string
  requestedBudget?: number
  reason: string
}

export function useApprovals(filters: ApprovalFilters) {
  return useQuery({
    queryKey: [...queryKeys.approvals, 'list', filters],
    queryFn: async () =>
      (await api.get<Page<Approval>>('/api/v1/approvals', { params: cleanParams({ size: 10, ...filters }) })).data,
    placeholderData: keepPreviousData,
  })
}

/** Decisions change tasks (completion) and projects (budget), so all three are refreshed. */
function useInvalidateApprovals() {
  const queryClient = useQueryClient()
  return () => {
    for (const key of [queryKeys.approvals, queryKeys.tasks, queryKeys.projects, queryKeys.dashboard]) {
      void queryClient.invalidateQueries({ queryKey: key })
    }
  }
}

export function useSubmitApproval() {
  const invalidate = useInvalidateApprovals()
  return useMutation({
    mutationFn: async (input: SubmitApprovalInput) => (await api.post<Approval>('/api/v1/approvals', input)).data,
    onSuccess: invalidate,
  })
}

export function useApprovalDecision() {
  const invalidate = useInvalidateApprovals()
  return useMutation({
    mutationFn: async ({
      id,
      action,
      comment,
    }: {
      id: string
      action: 'approve' | 'reject' | 'cancel'
      comment?: string
    }) =>
      (await api.post<Approval>(`/api/v1/approvals/${id}/${action}`, action === 'cancel' ? undefined : { comment }))
        .data,
    onSuccess: invalidate,
  })
}
