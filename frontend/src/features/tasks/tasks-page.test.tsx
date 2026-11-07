// Tasks page: rendering, overdue marking, filters sent to the API and the error state.

import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import type { AxiosResponse } from 'axios'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import * as client from '@/lib/api-client'
import { makeSession, makeUser, page, renderRoute } from '@/test/render'
import type { Task } from '@/types/api'

vi.mock('@/lib/api-client', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/lib/api-client')>()
  return { ...actual, refreshSession: vi.fn() }
})

const task: Task = {
  id: 'task-1',
  project: { id: 'p-1', name: 'Customer Portal' },
  title: 'Build account dashboard',
  description: null,
  status: 'IN_PROGRESS',
  priority: 'HIGH',
  assignee: { id: 'user-1', fullName: 'Eva Employee', email: 'eva@enterpriseflow.local' },
  reporter: { id: 'm-1', fullName: 'Maria Manager', email: 'maria@enterpriseflow.local' },
  dueDate: '2026-01-02',
  overdue: true,
  requiresApproval: false,
  archived: false,
  completedAt: null,
  createdAt: '2026-01-01T00:00:00Z',
  updatedAt: '2026-01-01T00:00:00Z',
  canEdit: false,
  allowedStatuses: ['TODO', 'IN_REVIEW', 'BLOCKED'],
}

describe('TasksPage', () => {
  let get: ReturnType<typeof vi.spyOn>

  beforeEach(() => {
    vi.mocked(client.refreshSession).mockResolvedValue(makeSession(makeUser()))
    get = vi.spyOn(client.api, 'get').mockImplementation(async (url: string) => {
      if (url.endsWith('/unread-count')) return { data: { count: 0 } } as AxiosResponse
      return { data: page([task]) } as AxiosResponse
    })
  })

  it('lists tasks and marks overdue ones with text, not only color', async () => {
    renderRoute('/tasks')

    const row = (await screen.findByRole('link', { name: 'Build account dashboard' })).closest('tr')!
    expect(within(row).getByText('(overdue)')).toBeInTheDocument()
    expect(within(row).getByText('Customer Portal')).toBeInTheDocument()
  })

  it('sends the selected filters to the API', async () => {
    renderRoute('/tasks')
    await screen.findByRole('link', { name: 'Build account dashboard' })

    await userEvent.selectOptions(screen.getByLabelText('Filter by status'), 'BLOCKED')
    await userEvent.click(screen.getByLabelText('Assigned to me'))

    await waitFor(() =>
      expect(get).toHaveBeenLastCalledWith(
        '/api/v1/tasks',
        expect.objectContaining({ params: expect.objectContaining({ status: 'BLOCKED', assignedToMe: true }) }),
      ),
    )
  })

  it('shows an error state with retry when loading fails', async () => {
    get.mockImplementation(async (url: string) => {
      if (url.endsWith('/unread-count')) return { data: { count: 0 } } as AxiosResponse
      // 4xx: not retried by the query client, so the error state shows immediately.
      throw new client.ApiError(403, { status: 403, detail: 'You do not have permission to perform this action.' })
    })

    renderRoute('/tasks')

    expect(await screen.findByText('Could not load data')).toBeInTheDocument()
    expect(screen.getByText('You do not have permission to perform this action.')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Try again' })).toBeInTheDocument()
  })
})
