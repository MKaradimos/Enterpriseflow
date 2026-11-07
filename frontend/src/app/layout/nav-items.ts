// Sidebar navigation entries and breadcrumb labels, with optional role restrictions per entry.

import {
  Bell,
  CheckSquare,
  FolderKanban,
  LayoutDashboard,
  ListChecks,
  UserCircle,
  Users,
  type LucideIcon,
} from 'lucide-react'
import type { RoleName } from '@/types/api'

export interface NavItem {
  label: string
  to: string
  icon: LucideIcon
  /** When set, only users with one of these roles see the item. */
  roles?: RoleName[]
}

export const navItems: NavItem[] = [
  { label: 'Dashboard', to: '/', icon: LayoutDashboard },
  { label: 'Projects', to: '/projects', icon: FolderKanban },
  { label: 'Tasks', to: '/tasks', icon: ListChecks },
  { label: 'Approvals', to: '/approvals', icon: CheckSquare },
  { label: 'Notifications', to: '/notifications', icon: Bell },
  { label: 'Users', to: '/users', icon: Users, roles: ['ADMIN'] },
  { label: 'Profile', to: '/profile', icon: UserCircle },
]

/** Breadcrumb labels for the first path segment. */
export const sectionLabels: Record<string, string> = Object.fromEntries(
  navItems.filter((item) => item.to !== '/').map((item) => [item.to.slice(1), item.label]),
)
