// Approvals page: confirming decisions, mandatory rejection comments, and showing only allowed actions.

import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import type { AxiosResponse } from 'axios'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import * as client from '@/lib/api-client'
import { makeSession, makeUser, page, renderRoute } from '@/test/render'
import type { Approval } from '@/types/api'

vi.mock('@/lib/api-client', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/lib/api-client')>()
  return { ...actual, refreshSession: vi.fn() }
})

const approval: Approval = {
  id: 'a-1',
  type: 'TASK_COMPLETION',
  status: 'PENDING',
  project: { id: 'p-1', name: 'Customer Portal' },
  task: { id: 't-1', name: 'Implement SSO login' },
  requester: { id: 'e-1', fullName: 'Eva Employee', email: 'eva@enterpriseflow.local' },
  reviewer: null,
  reason: 'SSO is implemented and tested.',
  requestedBudget: null,
  currentBudget: 120000,
  reviewComment: null,
  decidedAt: null,
  createdAt: '2026-11-01T10:00:00Z',
  canDecide: true,
  canCancel: false,
}

describe('ApprovalsPage', () => {
  let post: ReturnType<typeof vi.spyOn>

  beforeEach(() => {
    vi.mocked(client.refreshSession).mockResolvedValue(makeSession(makeUser(['MANAGER'])))
    vi.spyOn(client.api, 'get').mockImplementation(async (url: string) => {
      if (url.endsWith('/unread-count')) return { data: { count: 0 } } as AxiosResponse
      return { data: page([approval]) } as AxiosResponse
    })
    post = vi
      .spyOn(client.api, 'post')
      .mockResolvedValue({ data: { ...approval, status: 'APPROVED', canDecide: false } } as AxiosResponse)
  })

  it('approves a request after confirmation', async () => {
    renderRoute('/approvals')

    await userEvent.click(await screen.findByRole('button', { name: 'Approve' }))
    const dialog = await screen.findByRole('dialog')
    await userEvent.type(screen.getByLabelText('Comment'), 'Looks good')
    await userEvent.click(within(dialog).getByRole('button', { name: 'Approve' }))

    await waitFor(() => expect(post).toHaveBeenCalledWith('/api/v1/approvals/a-1/approve', { comment: 'Looks good' }))
  })

  it('requires a comment before a rejection can be confirmed', async () => {
    renderRoute('/approvals')

    await userEvent.click(await screen.findByRole('button', { name: 'Reject' }))
    const dialog = await screen.findByRole('dialog')
    const confirm = within(dialog).getByRole('button', { name: 'Reject' })

    expect(confirm).toBeDisabled()
    await userEvent.type(within(dialog).getByLabelText(/comment/i), 'Missing tests')
    expect(confirm).toBeEnabled()

    await userEvent.click(confirm)
    await waitFor(() => expect(post).toHaveBeenCalledWith('/api/v1/approvals/a-1/reject', { comment: 'Missing tests' }))
  })

  it('hides decision buttons the server says the user cannot use', async () => {
    vi.mocked(client.api.get).mockImplementation(async (url: string) => {
      if (url.endsWith('/unread-count')) return { data: { count: 0 } } as AxiosResponse
      return { data: page([{ ...approval, canDecide: false }]) } as AxiosResponse
    })

    renderRoute('/approvals')

    expect(await screen.findByText('SSO is implemented and tested.')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Approve' })).not.toBeInTheDocument()
  })
})
