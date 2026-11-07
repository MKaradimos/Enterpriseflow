// Login page with client-side validation; after login the user returns to the page they first requested.

import { zodResolver } from '@hookform/resolvers/zod'
import { Loader2, Workflow } from 'lucide-react'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { Navigate, useLocation, useNavigate } from 'react-router'
import { z } from 'zod'
import { FormField } from '@/components/page'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { ApiError, errorMessage } from '@/lib/api-client'
import { useAuth } from './use-auth'

// Client-side checks only for fast feedback; the server validates again.
const loginSchema = z.object({
  email: z.email('Enter a valid email address'),
  password: z.string().min(1, 'Password is required'),
})

type LoginValues = z.infer<typeof loginSchema>

/** Email / password form. Wrong credentials show one generic message, like the server. */
export function LoginPage() {
  const { status, login } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const [formError, setFormError] = useState<string | null>(null)

  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<LoginValues>({ resolver: zodResolver(loginSchema), defaultValues: { email: '', password: '' } })

  // The page the user tried to open before being sent to login (set by RequireAuth).
  const from = (location.state as { from?: string } | null)?.from ?? '/'

  // Already signed in (e.g. opened /login in a new tab): go straight on.
  if (status === 'authenticated') {
    return <Navigate to={from} replace />
  }

  const onSubmit = handleSubmit(async ({ email, password }) => {
    setFormError(null)
    try {
      await login(email, password)
      navigate(from, { replace: true })
    } catch (error) {
      setFormError(
        error instanceof ApiError && error.status === 401 ? 'Invalid email or password.' : errorMessage(error),
      )
    }
  })

  return (
    <main className="flex min-h-svh items-center justify-center bg-muted/40 px-4">
      <div className="flex w-full max-w-sm flex-col gap-6">
        <div className="flex items-center justify-center gap-2 text-lg font-semibold">
          <span className="rounded-md bg-primary p-1.5 text-primary-foreground">
            <Workflow className="size-4" aria-hidden />
          </span>
          EnterpriseFlow
        </div>
        <Card>
          <CardHeader>
            <h1 className="text-xl leading-none font-semibold">Sign in</h1>
            <CardDescription>Use your work account to continue.</CardDescription>
          </CardHeader>
          <CardContent>
            <form onSubmit={onSubmit} noValidate className="grid gap-4">
              {formError && (
                <p role="alert" className="rounded-md bg-destructive/10 px-3 py-2 text-sm text-destructive">
                  {formError}
                </p>
              )}
              <FormField id="email" label="Email" error={errors.email?.message}>
                <Input
                  id="email"
                  type="email"
                  autoComplete="username"
                  aria-invalid={!!errors.email}
                  aria-describedby={errors.email ? 'email-error' : undefined}
                  {...register('email')}
                />
              </FormField>
              <FormField id="password" label="Password" error={errors.password?.message}>
                <Input
                  id="password"
                  type="password"
                  autoComplete="current-password"
                  aria-invalid={!!errors.password}
                  aria-describedby={errors.password ? 'password-error' : undefined}
                  {...register('password')}
                />
              </FormField>
              <Button type="submit" disabled={isSubmitting} className="w-full">
                {isSubmitting && <Loader2 className="animate-spin" />}
                {isSubmitting ? 'Signing in…' : 'Sign in'}
              </Button>
            </form>
          </CardContent>
        </Card>
        <p className="text-center text-xs text-muted-foreground">
          Local demo: <span className="font-mono">admin@enterpriseflow.local</span> — see README for all accounts.
        </p>
      </div>
    </main>
  )
}
