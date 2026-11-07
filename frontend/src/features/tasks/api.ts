// React Query hooks for tasks: list with filters, details, activity and task mutations.

import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '@/lib/api-client'
import { cleanParams, queryKeys } from '@/lib/query'
import type { AuditEntry, Page, Task, TaskPriority, TaskStatus } from '@/types/api'

export interface TaskFilters {
  projectId?: string
  status?: TaskStatus | ''
  priority?: TaskPriority | ''
  assignedToMe?: boolean
  overdue?: boolean
  q?: string
  sort?: string
  page: number
  size?: number
}

export interface TaskInput {
  title: string
  description?: string | null
  priority: TaskPriority
  assigneeId?: string | null
  dueDate?: string | null
  requiresApproval: boolean
}

/** Paged task list; all filters (including projectId) are sent as query parameters to /tasks. */
export function useTasks(filters: TaskFilters) {
  return useQuery({
    queryKey: [...queryKeys.tasks, 'list', filters],
    queryFn: async () =>
      (
        await api.get<Page<Task>>('/api/v1/tasks', {
          params: cleanParams({
            size: 15,
            ...filters,
            assignedToMe: filters.assignedToMe || undefined,
            overdue: filters.overdue || undefined,
          }),
        })
      ).data,
    placeholderData: keepPreviousData,
  })
}

export function useTask(id: string) {
  return useQuery({
    queryKey: queryKeys.task(id),
    queryFn: async () => (await api.get<Task>(`/api/v1/tasks/${id}`)).data,
  })
}

export function useTaskActivity(id: string) {
  return useQuery({
    queryKey: queryKeys.taskActivity(id),
    queryFn: async () => (await api.get<AuditEntry[]>(`/api/v1/tasks/${id}/activity`)).data,
  })
}

/** A task change affects its project's progress and the dashboard too. */
function useInvalidateTasks() {
  const queryClient = useQueryClient()
  return () => {
    void queryClient.invalidateQueries({ queryKey: queryKeys.tasks })
    void queryClient.invalidateQueries({ queryKey: queryKeys.projects })
    void queryClient.invalidateQueries({ queryKey: queryKeys.dashboard })
  }
}

/** Creates a task in a project. */
export function useCreateTask(projectId: string) {
  const invalidate = useInvalidateTasks()
  return useMutation({
    mutationFn: async (input: TaskInput) => (await api.post<Task>(`/api/v1/projects/${projectId}/tasks`, input)).data,
    onSuccess: invalidate,
  })
}

export function useUpdateTask(id: string) {
  const invalidate = useInvalidateTasks()
  return useMutation({
    mutationFn: async (input: TaskInput) => (await api.put<Task>(`/api/v1/tasks/${id}`, input)).data,
    onSuccess: invalidate,
  })
}

/** Moves a task to another workflow status. */
export function useChangeTaskStatus() {
  const invalidate = useInvalidateTasks()
  return useMutation({
    mutationFn: async ({ id, status }: { id: string; status: TaskStatus }) =>
      (await api.patch<Task>(`/api/v1/tasks/${id}/status`, { status })).data,
    onSuccess: invalidate,
  })
}

/** Archives (soft-deletes) a task. */
export function useArchiveTask() {
  const invalidate = useInvalidateTasks()
  return useMutation({
    mutationFn: async (id: string) => {
      await api.delete(`/api/v1/tasks/${id}`)
    },
    onSuccess: invalidate,
  })
}
