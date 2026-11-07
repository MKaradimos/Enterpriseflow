// Application shell for signed-in users: sidebar navigation, header (breadcrumbs, theme toggle,
// notification bell, user menu) and the outlet where the current page renders.

import { Bell, ChevronRight, LogOut, Menu, Moon, Sun, UserCircle, Workflow, X } from 'lucide-react'
import { useState } from 'react'
import { Link, NavLink, Outlet, useLocation, useNavigate } from 'react-router'
import { Button } from '@/components/ui/button'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import { useAuth } from '@/features/auth/use-auth'
import { useUnreadCount } from '@/features/notifications/api'
import { humanize } from '@/lib/format'
import { cn } from '@/lib/utils'
import { navItems, sectionLabels } from './nav-items'
import { useTheme } from './theme'

/** Layout around every protected page. The sidebar collapses into a drawer on small screens. */
export function AppLayout() {
  const [mobileNavOpen, setMobileNavOpen] = useState(false)

  return (
    <div className="flex min-h-svh">
      <a
        href="#main"
        className="sr-only focus:not-sr-only focus:fixed focus:top-2 focus:left-2 focus:z-50 focus:rounded-md focus:bg-card focus:px-3 focus:py-2"
      >
        Skip to content
      </a>

      {/* Fixed on desktop, slide-in drawer below the lg breakpoint. */}
      <aside
        className={cn(
          'fixed inset-y-0 left-0 z-40 flex w-64 flex-col border-r bg-sidebar transition-transform lg:sticky lg:top-0 lg:h-svh lg:translate-x-0',
          mobileNavOpen ? 'translate-x-0' : '-translate-x-full',
        )}
      >
        <div className="flex h-14 items-center gap-2 border-b px-5 font-semibold">
          <span className="rounded-md bg-primary p-1 text-primary-foreground">
            <Workflow className="size-4" aria-hidden />
          </span>
          EnterpriseFlow
          <button
            type="button"
            className="ml-auto rounded-md p-1 hover:bg-muted lg:hidden"
            onClick={() => setMobileNavOpen(false)}
            aria-label="Close navigation"
          >
            <X className="size-4" />
          </button>
        </div>
        <SidebarNav onNavigate={() => setMobileNavOpen(false)} />
      </aside>

      {mobileNavOpen && (
        <div className="fixed inset-0 z-30 bg-black/40 lg:hidden" onClick={() => setMobileNavOpen(false)} aria-hidden />
      )}

      <div className="flex min-w-0 flex-1 flex-col">
        <header className="sticky top-0 z-20 flex h-14 items-center gap-3 border-b bg-card/95 px-4 backdrop-blur lg:px-6">
          <Button
            variant="ghost"
            size="icon"
            className="lg:hidden"
            onClick={() => setMobileNavOpen(true)}
            aria-label="Open navigation"
            aria-expanded={mobileNavOpen}
          >
            <Menu className="size-5" />
          </Button>
          <Breadcrumbs />
          <div className="ml-auto flex items-center gap-1">
            <ThemeToggle />
            <NotificationBell />
            <UserMenu />
          </div>
        </header>

        <main id="main" className="mx-auto w-full max-w-7xl flex-1 p-4 lg:p-8">
          <Outlet />
        </main>
      </div>
    </div>
  )
}

function SidebarNav({ onNavigate }: { onNavigate: () => void }) {
  const { hasRole } = useAuth()
  // Role-aware navigation: hiding the link is convenience; the server enforces the actual permission.
  const items = navItems.filter((item) => !item.roles || hasRole(...item.roles))

  return (
    <nav aria-label="Main" className="flex flex-col gap-0.5 p-3">
      {items.map(({ label, to, icon: Icon }) => (
        <NavLink
          key={to}
          to={to}
          // `end` only for "/", so /projects/123 still highlights "Projects".
          end={to === '/'}
          onClick={onNavigate}
          className={({ isActive }) =>
            cn(
              'flex items-center gap-3 rounded-md px-3 py-2 text-sm transition-colors',
              isActive
                ? 'bg-accent font-medium text-accent-foreground'
                : 'text-muted-foreground hover:bg-muted hover:text-foreground',
            )
          }
        >
          <Icon className="size-4" aria-hidden />
          {label}
        </NavLink>
      ))}
    </nav>
  )
}

/** Breadcrumbs derived from the current URL. */
function Breadcrumbs() {
  const { pathname } = useLocation()
  const [section, detail] = pathname.split('/').filter(Boolean)

  const crumbs = [{ label: 'Dashboard', to: '/' }]
  if (section) crumbs.push({ label: sectionLabels[section] ?? humanize(section), to: `/${section}` })
  if (detail) crumbs.push({ label: 'Details', to: pathname })

  return (
    <nav aria-label="Breadcrumb" className="hidden min-w-0 sm:block">
      <ol className="flex items-center gap-1.5 text-sm text-muted-foreground">
        {crumbs.map((crumb, index) => {
          const last = index === crumbs.length - 1
          return (
            <li key={crumb.to} className="flex items-center gap-1.5">
              {index > 0 && <ChevronRight className="size-3.5" aria-hidden />}
              {last ? (
                <span aria-current="page" className="truncate font-medium text-foreground">
                  {crumb.label}
                </span>
              ) : (
                <Link to={crumb.to} className="hover:text-foreground">
                  {crumb.label}
                </Link>
              )}
            </li>
          )
        })}
      </ol>
    </nav>
  )
}

/** Switches between light and dark theme. */
function ThemeToggle() {
  const { theme, toggle } = useTheme()
  return (
    <Button
      variant="ghost"
      size="icon"
      onClick={toggle}
      aria-label={`Switch to ${theme === 'dark' ? 'light' : 'dark'} theme`}
    >
      {theme === 'dark' ? <Sun /> : <Moon />}
    </Button>
  )
}

/** Bell icon with the unread notifications count. */
function NotificationBell() {
  const { data: count = 0 } = useUnreadCount()
  return (
    <Button variant="ghost" size="icon" asChild>
      <Link
        to="/notifications"
        aria-label={count > 0 ? `Notifications, ${count} unread` : 'Notifications'}
        className="relative"
      >
        <Bell />
        {count > 0 && (
          <span className="absolute top-1 right-1 flex min-w-4 items-center justify-center rounded-full bg-destructive px-1 text-[10px] leading-4 font-semibold text-white">
            {count > 99 ? '99+' : count}
          </span>
        )}
      </Link>
    </Button>
  )
}

/** Current user's name and roles, with links to the profile and logout. */
function UserMenu() {
  const { user, logout } = useAuth()
  const navigate = useNavigate()
  if (!user) return null

  const initials = user.fullName
    .split(' ')
    .map((part) => part[0])
    .join('')
    .slice(0, 2)
    .toUpperCase()

  return (
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        <button
          type="button"
          className="ml-1 flex size-8 items-center justify-center rounded-full bg-primary text-xs font-semibold text-primary-foreground"
          aria-label="Account menu"
        >
          {initials}
        </button>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="end">
        <DropdownMenuLabel>
          <div className="font-medium text-foreground">{user.fullName}</div>
          <div className="truncate">{user.email}</div>
          <div className="mt-1">{user.roles.map(humanize).join(', ')}</div>
        </DropdownMenuLabel>
        <DropdownMenuSeparator />
        <DropdownMenuItem onSelect={() => navigate('/profile')}>
          <UserCircle /> Profile
        </DropdownMenuItem>
        <DropdownMenuItem
          onSelect={() => {
            void logout().then(() => navigate('/login', { replace: true }))
          }}
        >
          <LogOut /> Sign out
        </DropdownMenuItem>
      </DropdownMenuContent>
    </DropdownMenu>
  )
}
