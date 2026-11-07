// React Query hooks for user administration and for the user's own profile and password.

import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '@/lib/api-client'
import { cleanParams, queryKeys } from '@/lib/query'
import type { Page, RoleName, User, UserSummary } from '@/types/api'

export interface UserFilters {
  q?: string
  role?: RoleName | ''
  active?: 'true' | 'false' | ''
  page: number
}

/** Paged user list for administrators. */
export function useUsers(filters: UserFilters) {
  return useQuery({
    queryKey: [...queryKeys.users, 'list', filters],
    queryFn: async () =>
      (await api.get<Page<User>>('/api/v1/users', { params: cleanParams({ size: 15, ...filters }) })).data,
    placeholderData: keepPreviousData,
  })
}

/** Active-user search for member pickers. Only enabled for managers and admins (the endpoint requires it). */
export function useUserLookup(q: string, enabled: boolean) {
  return useQuery({
    queryKey: queryKeys.userLookup(q),
    queryFn: async () => (await api.get<UserSummary[]>('/api/v1/users/lookup', { params: { q, limit: 8 } })).data,
    enabled,
    staleTime: 60_000,
  })
}

function useInvalidateUsers() {
  const queryClient = useQueryClient()
  return () => void queryClient.invalidateQueries({ queryKey: queryKeys.users })
}

/** Creates a user (admin). */
export function useCreateUser() {
  const invalidate = useInvalidateUsers()
  return useMutation({
    mutationFn: async (input: {
      email: string
      fullName: string
      jobTitle?: string
      password: string
      roles: RoleName[]
    }) => (await api.post<User>('/api/v1/users', input)).data,
    onSuccess: invalidate,
  })
}

/** Replaces a user's roles (admin). */
export function useUpdateRoles() {
  const invalidate = useInvalidateUsers()
  return useMutation({
    mutationFn: async ({ id, roles }: { id: string; roles: RoleName[] }) =>
      (await api.put<User>(`/api/v1/users/${id}/roles`, { roles })).data,
    onSuccess: invalidate,
  })
}

/** Activates or deactivates a user (admin). */
export function useUpdateUserStatus() {
  const invalidate = useInvalidateUsers()
  return useMutation({
    mutationFn: async ({ id, active }: { id: string; active: boolean }) =>
      (await api.patch<User>(`/api/v1/users/${id}/status`, { active })).data,
    onSuccess: invalidate,
  })
}

/** Updates the current user's own profile. */
export function useUpdateProfile() {
  return useMutation({
    mutationFn: async (input: { fullName: string; jobTitle?: string | null }) =>
      (await api.patch<User>('/api/v1/profile', input)).data,
  })
}

/** Changes the current user's password. */
export function useChangePassword() {
  return useMutation({
    mutationFn: async (input: { currentPassword: string; newPassword: string }) => {
      await api.post('/api/v1/profile/password', input)
    },
  })
}
