// API client error handling: Problem Details parsing and friendly messages for network failures.

import { AxiosError, AxiosHeaders, type AxiosAdapter } from 'axios'
import { describe, expect, it } from 'vitest'
import { api, ApiError, errorMessage } from './api-client'

/** Replaces the HTTP transport so the real interceptors run without a network. */
function respondWith(status: number, data: unknown, contentType: string): AxiosAdapter {
  return async (config) => {
    const response = {
      status,
      statusText: '',
      data,
      config,
      headers: new AxiosHeaders({ 'content-type': contentType }),
    }
    if (status >= 400) {
      throw new AxiosError('Request failed', 'ERR_BAD_RESPONSE', config, undefined, response)
    }
    return response
  }
}

describe('api client', () => {
  it('turns a problem+json response into ApiError with the problem details', async () => {
    const problem = { status: 403, title: 'Forbidden', detail: 'You do not have permission to perform this action.' }

    const request = api.get('/api/v1/users', { adapter: respondWith(403, problem, 'application/problem+json') })

    await expect(request).rejects.toBeInstanceOf(ApiError)
    await expect(request).rejects.toMatchObject({ status: 403, problem, message: problem.detail })
  })

  it('ignores non-problem error bodies', async () => {
    const request = api.get('/broken', { adapter: respondWith(502, '<html>Bad Gateway</html>', 'text/html') })

    await expect(request).rejects.toMatchObject({ status: 502, problem: undefined })
  })

  it('gives a friendly message when the server is unreachable', () => {
    expect(errorMessage(new ApiError(0, undefined, 'Network Error'))).toMatch(/cannot reach the server/i)
  })
})
