/** Removes empty values so they don't end up as "?status=&q=" in the query string. */
export function cleanParams<T extends Record<string, unknown>>(params: T): Partial<T> {
  return Object.fromEntries(
    Object.entries(params).filter(([, value]) => value !== undefined && value !== null && value !== ''),
  ) as Partial<T>
}

/**
 * Hierarchical query keys. Invalidating a prefix (e.g. ['tasks']) refreshes every query below it, which keeps
 * lists, details and the dashboard consistent after a mutation.
 */
export const queryKeys = {
  dashboard: ['dashboard'] as const,
  projects: ['projects'] as const,
  project: (id: string) => ['projects', id] as const,
  projectMembers: (id: string) => ['projects', id, 'members'] as const,
  projectActivity: (id: string) => ['projects', id, 'activity'] as const,
  tasks: ['tasks'] as const,
  task: (id: string) => ['tasks', id] as const,
  taskActivity: (id: string) => ['tasks', id, 'activity'] as const,
  approvals: ['approvals'] as const,
  users: ['users'] as const,
  userLookup: (q: string) => ['users', 'lookup', q] as const,
  notifications: ['notifications'] as const,
  unreadCount: ['notifications', 'unread-count'] as const,
}
