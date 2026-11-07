// Axios client: adds the access token, refreshes it on 401 and retries once.

import axios, { AxiosError, type InternalAxiosRequestConfig } from 'axios'
import type { AuthResponse } from '@/types/api'

/** RFC 9457 Problem Details, the error format returned by every backend endpoint. */
export interface ProblemDetail {
  type?: string
  title?: string
  status: number
  detail?: string
  instance?: string
  errors?: { field: string; message: string }[]
}

export class ApiError extends Error {
  readonly status: number
  readonly problem: ProblemDetail | undefined

  constructor(status: number, problem?: ProblemDetail, message?: string) {
    super(problem?.detail ?? message ?? `Request failed with status ${status}`)
    this.name = 'ApiError'
    this.status = status
    this.problem = problem
  }
}

const baseURL = import.meta.env.VITE_API_BASE_URL ?? ''

/** Main client for the API. Requests carry the in-memory access token. */
export const api = axios.create({
  baseURL,
  timeout: 15_000,
  headers: { Accept: 'application/json' },
  withCredentials: true,
})

/** Separate instance for auth calls, so they never trigger the refresh-and-retry interceptor below. */
const authClient = axios.create({ baseURL, timeout: 15_000, withCredentials: true })

// ---- Token storage ----------------------------------------------------------------------------------------
// The access token lives only in this module's memory: not in localStorage, where any injected script could read
// it. A page reload loses it, and the session is restored from the HttpOnly refresh cookie instead.

let accessToken: string | null = null
let csrfToken: string | null = null
let refreshInFlight: Promise<AuthResponse> | null = null
let onSessionExpired: (() => void) | null = null

export function setAccessToken(token: string | null) {
  accessToken = token
}

export function setSessionExpiredHandler(handler: (() => void) | null) {
  onSessionExpired = handler
}

async function ensureCsrfToken(): Promise<string> {
  if (!csrfToken) {
    const { data } = await authClient.get<{ token: string }>('/api/v1/auth/csrf')
    csrfToken = data.token
  }
  return csrfToken
}

/**
 * Exchanges the refresh cookie for a new access token. Single-flight: concurrent callers share one request.
 * Otherwise two parallel 401s would both present the same refresh token, and the server would treat the second
 * use of a rotated token as theft and revoke the session.
 */
export function refreshSession(): Promise<AuthResponse> {
  refreshInFlight ??= (async () => {
    try {
      const data = await postWithCsrf<AuthResponse>('/api/v1/auth/refresh')
      accessToken = data.accessToken
      return data
    } catch (error) {
      accessToken = null
      throw toApiError(error)
    } finally {
      refreshInFlight = null
    }
  })()
  return refreshInFlight
}

export async function login(email: string, password: string): Promise<AuthResponse> {
  try {
    const { data } = await authClient.post<AuthResponse>('/api/v1/auth/login', { email, password })
    accessToken = data.accessToken
    return data
  } catch (error) {
    throw toApiError(error)
  }
}

/**
 * POST to a cookie-authenticated endpoint with the CSRF header. If the CSRF cookie was cleared (403), fetch a new
 * token once and retry.
 */
async function postWithCsrf<T>(url: string): Promise<T> {
  try {
    const { data } = await authClient.post<T>(url, null, { headers: { 'X-XSRF-TOKEN': await ensureCsrfToken() } })
    return data
  } catch (error) {
    if (axios.isAxiosError(error) && error.response?.status === 403) {
      csrfToken = null
      const { data } = await authClient.post<T>(url, null, { headers: { 'X-XSRF-TOKEN': await ensureCsrfToken() } })
      return data
    }
    throw error
  }
}

export async function logout(): Promise<void> {
  try {
    await postWithCsrf('/api/v1/auth/logout')
  } finally {
    // Clear local state even if the network call fails; the cookie expires on its own.
    accessToken = null
  }
}

// ---- Interceptors -----------------------------------------------------------------------------------------

api.interceptors.request.use((config) => {
  if (accessToken) {
    config.headers.Authorization = `Bearer ${accessToken}`
  }
  return config
})

type RetriableConfig = InternalAxiosRequestConfig & { _retried?: boolean }

api.interceptors.response.use(
  (response) => response,
  async (error: AxiosError<ProblemDetail>) => {
    const config = error.config as RetriableConfig | undefined

    // Access token expired: refresh once, then replay the original request transparently.
    if (error.response?.status === 401 && config && !config._retried) {
      config._retried = true
      try {
        await refreshSession()
        return api(config)
      } catch {
        onSessionExpired?.()
      }
    }
    return Promise.reject(toApiError(error))
  },
)

function toApiError(error: unknown): ApiError {
  if (error instanceof ApiError) {
    return error
  }
  if (axios.isAxiosError<ProblemDetail>(error)) {
    if (error.response) {
      const isProblem = error.response.headers['content-type']?.toString().includes('application/problem+json')
      return new ApiError(error.response.status, isProblem ? error.response.data : undefined)
    }
    // No response at all: network down, CORS rejection or timeout. Status 0 by convention.
    return new ApiError(0, undefined, error.message)
  }
  return new ApiError(0, undefined, error instanceof Error ? error.message : 'Unknown error')
}

/** User-facing message for any error thrown by the API layer. */
export function errorMessage(error: unknown): string {
  if (error instanceof ApiError) {
    if (error.status === 0) return 'Cannot reach the server. Check your connection and try again.'
    return error.problem?.detail ?? error.message
  }
  return 'Something went wrong.'
}
