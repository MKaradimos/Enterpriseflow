import { zodResolver } from '@hookform/resolvers/zod'
import { LogOut } from 'lucide-react'
import { useForm } from 'react-hook-form'
import { useNavigate } from 'react-router'
import { toast } from 'sonner'
import { z } from 'zod'
import { FormField, PageHeader } from '@/components/page'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { useAuth } from '@/features/auth/use-auth'
import { useChangePassword, useUpdateProfile } from '@/features/users/api'
import { errorMessage } from '@/lib/api-client'
import { humanize } from '@/lib/format'
import { passwordSchema } from '@/lib/validation'

const profileSchema = z.object({
  fullName: z.string().trim().min(1, 'Name is required').max(120),
  jobTitle: z.string().max(120).optional(),
})

const passwordFormSchema = z
  .object({
    currentPassword: z.string().min(1, 'Enter your current password'),
    newPassword: passwordSchema,
    confirm: z.string(),
  })
  .refine((v) => v.newPassword === v.confirm, { path: ['confirm'], message: 'Passwords do not match' })

export function ProfilePage() {
  const { user, setUser, logout } = useAuth()
  const navigate = useNavigate()
  const updateProfile = useUpdateProfile()
  const changePassword = useChangePassword()

  const profileForm = useForm<z.infer<typeof profileSchema>>({
    resolver: zodResolver(profileSchema),
    values: { fullName: user?.fullName ?? '', jobTitle: user?.jobTitle ?? '' },
  })
  const passwordForm = useForm<z.infer<typeof passwordFormSchema>>({
    resolver: zodResolver(passwordFormSchema),
    defaultValues: { currentPassword: '', newPassword: '', confirm: '' },
  })

  if (!user) return null

  const saveProfile = profileForm.handleSubmit(async (values) => {
    try {
      setUser(await updateProfile.mutateAsync({ fullName: values.fullName, jobTitle: values.jobTitle || null }))
      toast.success('Profile updated')
    } catch (error) {
      toast.error(errorMessage(error))
    }
  })

  const savePassword = passwordForm.handleSubmit(async ({ currentPassword, newPassword }) => {
    try {
      await changePassword.mutateAsync({ currentPassword, newPassword })
      // The server ended every session, including this one.
      toast.success('Password changed. Please sign in again.')
      await logout()
      navigate('/login', { replace: true })
    } catch (error) {
      toast.error(errorMessage(error))
    }
  })

  return (
    <div className="flex max-w-3xl flex-col gap-6">
      <PageHeader
        title="Profile"
        actions={
          <Button variant="outline" onClick={() => void logout().then(() => navigate('/login', { replace: true }))}>
            <LogOut /> Sign out
          </Button>
        }
      />

      <Card>
        <CardHeader>
          <CardTitle>Account</CardTitle>
          <CardDescription>Email and roles are managed by administrators.</CardDescription>
        </CardHeader>
        <CardContent>
          <dl className="mb-6 grid gap-4 text-sm sm:grid-cols-2">
            <div>
              <dt className="text-xs text-muted-foreground">Email</dt>
              <dd className="font-medium">{user.email}</dd>
            </div>
            <div>
              <dt className="text-xs text-muted-foreground">Roles</dt>
              <dd className="mt-1 flex gap-1">
                {user.roles.map((r) => (
                  <Badge key={r} variant="accent">
                    {humanize(r)}
                  </Badge>
                ))}
              </dd>
            </div>
          </dl>
          <form onSubmit={saveProfile} noValidate className="grid gap-4 sm:grid-cols-2">
            <FormField id="profile-name" label="Full name" error={profileForm.formState.errors.fullName?.message}>
              <Input id="profile-name" {...profileForm.register('fullName')} />
            </FormField>
            <FormField id="profile-title" label="Job title">
              <Input id="profile-title" {...profileForm.register('jobTitle')} />
            </FormField>
            <div className="sm:col-span-2">
              <Button type="submit" disabled={profileForm.formState.isSubmitting}>
                Save profile
              </Button>
            </div>
          </form>
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>Change password</CardTitle>
          <CardDescription>Changing your password signs you out on all devices.</CardDescription>
        </CardHeader>
        <CardContent>
          <form onSubmit={savePassword} noValidate className="grid gap-4 sm:max-w-sm">
            <FormField
              id="current-password"
              label="Current password"
              error={passwordForm.formState.errors.currentPassword?.message}
            >
              <Input
                id="current-password"
                type="password"
                autoComplete="current-password"
                {...passwordForm.register('currentPassword')}
              />
            </FormField>
            <FormField
              id="new-password"
              label="New password"
              error={passwordForm.formState.errors.newPassword?.message}
              hint="At least 10 characters, with a letter and a digit."
            >
              <Input
                id="new-password"
                type="password"
                autoComplete="new-password"
                {...passwordForm.register('newPassword')}
              />
            </FormField>
            <FormField
              id="confirm-password"
              label="Confirm new password"
              error={passwordForm.formState.errors.confirm?.message}
            >
              <Input
                id="confirm-password"
                type="password"
                autoComplete="new-password"
                {...passwordForm.register('confirm')}
              />
            </FormField>
            <div>
              <Button type="submit" disabled={passwordForm.formState.isSubmitting}>
                Change password
              </Button>
            </div>
          </form>
        </CardContent>
      </Card>
    </div>
  )
}
