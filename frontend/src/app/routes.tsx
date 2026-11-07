// All routes. Everything except /login sits behind RequireAuth.

import type { RouteObject } from 'react-router'
import { ApprovalsPage } from '@/features/approvals/approvals-page'
import { LoginPage } from '@/features/auth/login-page'
import { RequireAuth, RequireRole } from '@/features/auth/route-guards'
import { NotificationsPage } from '@/features/notifications/notifications-page'
import { ProfilePage } from '@/features/profile/profile-page'
import { ProjectDetailPage } from '@/features/projects/project-detail-page'
import { ProjectsPage } from '@/features/projects/projects-page'
import { TaskDetailPage } from '@/features/tasks/task-detail-page'
import { TasksPage } from '@/features/tasks/tasks-page'
import { UsersPage } from '@/features/users/users-page'
import { AppLayout } from './layout/app-layout'
import { NotFoundPage } from './not-found-page'
import { RouteError } from './route-error'

// Kept separate from the browser router so tests can mount the same tree with createMemoryRouter.
export const routes: RouteObject[] = [
  { path: '/login', element: <LoginPage />, errorElement: <RouteError /> },
  {
    element: <RequireAuth />,
    errorElement: <RouteError />,
    children: [
      {
        element: <AppLayout />,
        children: [
          {
            index: true,
            // Loaded on demand: the dashboard pulls in the charting library, which other pages don't need.
            lazy: async () => ({ Component: (await import('@/features/dashboard/dashboard-page')).DashboardPage }),
          },
          { path: 'projects', element: <ProjectsPage /> },
          { path: 'projects/:projectId', element: <ProjectDetailPage /> },
          { path: 'tasks', element: <TasksPage /> },
          { path: 'tasks/:taskId', element: <TaskDetailPage /> },
          { path: 'approvals', element: <ApprovalsPage /> },
          { path: 'notifications', element: <NotificationsPage /> },
          {
            path: 'users',
            element: (
              <RequireRole roles={['ADMIN']}>
                <UsersPage />
              </RequireRole>
            ),
          },
          { path: 'profile', element: <ProfilePage /> },
          { path: '*', element: <NotFoundPage /> },
        ],
      },
    ],
  },
]
