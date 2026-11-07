// React Query hooks for projects: list, details, members, activity and all project mutations.

import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '@/lib/api-client'
import { cleanParams, queryKeys } from '@/lib/query'
import type { AuditEntry, Page, Project, ProjectMember, ProjectStatus } from '@/types/api'

export interface ProjectFilters {
  q?: string
  status?: ProjectStatus | ''
  includeArchived?: boolean
  page: number
  size?: number
}

export interface ProjectInput {
  name: string
  description?: string | null
  status?: ProjectStatus
  managerId?: string | null
  budget?: number | null
  startDate?: string | null
  dueDate?: string | null
}

/** Paged project list for the given filters. */
export function useProjects(filters: ProjectFilters) {
  return useQuery({
    queryKey: [...queryKeys.projects, 'list', filters],
    queryFn: async () =>
      (await api.get<Page<Project>>('/api/v1/projects', { params: cleanParams({ size: 10, ...filters }) })).data,
    // Keep the previous page on screen while the next one loads, instead of flashing a skeleton.
    placeholderData: keepPreviousData,
  })
}

export function useProject(id: string) {
  return useQuery({
    queryKey: queryKeys.project(id),
    queryFn: async () => (await api.get<Project>(`/api/v1/projects/${id}`)).data,
  })
}

export function useProjectMembers(id: string) {
  return useQuery({
    queryKey: queryKeys.projectMembers(id),
    queryFn: async () => (await api.get<ProjectMember[]>(`/api/v1/projects/${id}/members`)).data,
  })
}

export function useProjectActivity(id: string) {
  return useQuery({
    queryKey: queryKeys.projectActivity(id),
    queryFn: async () => (await api.get<AuditEntry[]>(`/api/v1/projects/${id}/activity`)).data,
  })
}

/** Invalidates projects and the dashboard, which shows project counts and progress. */
function useInvalidateProjects() {
  const queryClient = useQueryClient()
  return () => {
    void queryClient.invalidateQueries({ queryKey: queryKeys.projects })
    void queryClient.invalidateQueries({ queryKey: queryKeys.dashboard })
  }
}

/** Creates a project. */
export function useCreateProject() {
  const invalidate = useInvalidateProjects()
  return useMutation({
    mutationFn: async (input: ProjectInput) => (await api.post<Project>('/api/v1/projects', input)).data,
    onSuccess: invalidate,
  })
}

export function useUpdateProject(id: string) {
  const invalidate = useInvalidateProjects()
  return useMutation({
    mutationFn: async (input: ProjectInput) => (await api.put<Project>(`/api/v1/projects/${id}`, input)).data,
    onSuccess: invalidate,
  })
}

/** Archives or restores a project. */
export function useProjectAction(id: string, action: 'archive' | 'restore') {
  const invalidate = useInvalidateProjects()
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: async () => (await api.post<Project>(`/api/v1/projects/${id}/${action}`)).data,
    onSuccess: () => {
      invalidate()
      void queryClient.invalidateQueries({ queryKey: queryKeys.tasks })
    },
  })
}

/** Adds a user to the project team. */
export function useAddMember(projectId: string) {
  const invalidate = useInvalidateProjects()
  return useMutation({
    mutationFn: async (userId: string) =>
      (await api.post<ProjectMember>(`/api/v1/projects/${projectId}/members`, { userId })).data,
    onSuccess: invalidate,
  })
}

export function useRemoveMember(projectId: string) {
  const invalidate = useInvalidateProjects()
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: async (userId: string) => {
      await api.delete(`/api/v1/projects/${projectId}/members/${userId}`)
    },
    onSuccess: () => {
      invalidate()
      // Removing a member unassigns their open tasks on the server.
      void queryClient.invalidateQueries({ queryKey: queryKeys.tasks })
    },
  })
}
