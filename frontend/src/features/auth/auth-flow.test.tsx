import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import type { AxiosResponse } from 'axios'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import * as client from '@/lib/api-client'
import { makeSession, makeUser, page, renderRoute } from '@/test/render'

vi.mock('@/lib/api-client', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/lib/api-client')>()
  return { ...actual, refreshSession: vi.fn(), login: vi.fn(), logout: vi.fn() }
})

const refreshSession = vi.mocked(client.refreshSession)
const login = vi.mocked(client.login)

/** Answers every GET with an empty page so authenticated pages render without a backend. */
function stubApi() {
  vi.spyOn(client.api, 'get').mockImplementation(async (url: string) => {
    if (url.endsWith('/unread-count')) return { data: { count: 0 } } as AxiosResponse
    return { data: page([]) } as AxiosResponse
  })
}

describe('authentication flow', () => {
  beforeEach(() => {
    stubApi()
  })

  it('redirects unauthenticated visitors to the login page', async () => {
    refreshSession.mockRejectedValue(new client.ApiError(401))

    const { router } = renderRoute('/projects')

    expect(await screen.findByRole('heading', { name: 'Sign in' })).toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/login')
  })

  it('validates the form before calling the API', async () => {
    refreshSession.mockRejectedValue(new client.ApiError(401))
    renderRoute('/login')

    await userEvent.click(await screen.findByRole('button', { name: 'Sign in' }))

    expect(await screen.findByText('Enter a valid email address')).toBeInTheDocument()
    expect(screen.getByText('Password is required')).toBeInTheDocument()
    expect(login).not.toHaveBeenCalled()
  })

  it('shows a generic message for wrong credentials', async () => {
    refreshSession.mockRejectedValue(new client.ApiError(401))
    login.mockRejectedValue(new client.ApiError(401))
    renderRoute('/login')

    await userEvent.type(await screen.findByLabelText('Email'), 'eva@enterpriseflow.local')
    await userEvent.type(screen.getByLabelText('Password'), 'wrong-password')
    await userEvent.click(screen.getByRole('button', { name: 'Sign in' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Invalid email or password.')
  })

  it('returns to the originally requested page after login', async () => {
    refreshSession.mockRejectedValue(new client.ApiError(401))
    login.mockResolvedValue(makeSession(makeUser()))
    const { router } = renderRoute('/tasks')

    await userEvent.type(await screen.findByLabelText('Email'), 'eva@enterpriseflow.local')
    await userEvent.type(screen.getByLabelText('Password'), 'Password123')
    await userEvent.click(screen.getByRole('button', { name: 'Sign in' }))

    await waitFor(() => expect(router.state.location.pathname).toBe('/tasks'))
    expect(await screen.findByRole('heading', { name: 'Tasks' })).toBeInTheDocument()
  })

  it('restores an existing session from the refresh cookie on load', async () => {
    refreshSession.mockResolvedValue(makeSession(makeUser()))

    renderRoute('/projects')

    expect(await screen.findByRole('heading', { name: 'Projects' })).toBeInTheDocument()
  })
})

describe('role-aware navigation', () => {
  beforeEach(() => {
    stubApi()
  })

  it('hides the Users link and blocks the page for non-admins', async () => {
    refreshSession.mockResolvedValue(makeSession(makeUser(['MANAGER'])))

    renderRoute('/users')

    expect(await screen.findByText('Access restricted')).toBeInTheDocument()
    expect(screen.queryByRole('link', { name: 'Users' })).not.toBeInTheDocument()
  })

  it('shows user management to admins', async () => {
    refreshSession.mockResolvedValue(makeSession(makeUser(['ADMIN'])))

    renderRoute('/users')

    expect(await screen.findByRole('heading', { name: 'Users' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Users' })).toBeInTheDocument()
  })

  it('offers project creation only to managers and admins', async () => {
    refreshSession.mockResolvedValue(makeSession(makeUser(['EMPLOYEE'])))

    renderRoute('/projects')

    expect(await screen.findByRole('heading', { name: 'Projects' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /new project/i })).not.toBeInTheDocument()
  })
})
