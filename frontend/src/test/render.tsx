import { render, type RenderOptions } from '@testing-library/react'
import type { ReactElement } from 'react'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { Providers } from '@/app/providers'
import { routes } from '@/app/routes'
import type { AuthResponse, Page, RoleName, User } from '@/types/api'

/** Renders with the same providers as the real app (React Query, auth, toasts). */
export function renderWithProviders(ui: ReactElement, options?: RenderOptions) {
  return render(ui, { wrapper: Providers, ...options })
}

/** Mounts the real route tree at a given URL, as the browser would. */
export function renderRoute(path: string) {
  const router = createMemoryRouter(routes, { initialEntries: [path] })
  return { router, ...render(<RouterProvider router={router} />, { wrapper: Providers }) }
}

export function makeUser(roles: RoleName[] = ['EMPLOYEE'], overrides: Partial<User> = {}): User {
  return {
    id: 'user-1',
    email: 'eva@enterpriseflow.local',
    fullName: 'Eva Employee',
    jobTitle: null,
    active: true,
    roles,
    createdAt: '2026-01-01T00:00:00Z',
    ...overrides,
  }
}

export function makeSession(user: User): AuthResponse {
  return { accessToken: 'token', tokenType: 'Bearer', expiresAt: '2099-01-01T00:00:00Z', user }
}

export function page<T>(content: T[]): Page<T> {
  return { content, page: 0, size: 20, totalElements: content.length, totalPages: content.length ? 1 : 0 }
}
