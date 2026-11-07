// TypeScript mirror of the backend DTOs. Keep in sync with the Java records in backend/src/main/java/**/dto.

// Enums are sent as strings by the backend; string unions give the same safety with no runtime code.
// Dates are ISO strings: "2026-11-07" for calendar dates, "2026-11-07T10:15:30Z" for timestamps.
export type RoleName = 'ADMIN' | 'MANAGER' | 'EMPLOYEE'
export type ProjectStatus = 'PLANNED' | 'ACTIVE' | 'ON_HOLD' | 'COMPLETED' | 'ARCHIVED'
export type TaskStatus = 'TODO' | 'IN_PROGRESS' | 'IN_REVIEW' | 'DONE' | 'BLOCKED'
export type TaskPriority = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL'
export type ApprovalType = 'TASK_COMPLETION' | 'PROJECT_BUDGET_CHANGE'
export type ApprovalStatus = 'PENDING' | 'APPROVED' | 'REJECTED' | 'CANCELLED'
export type ApprovalScope = 'MINE' | 'TO_REVIEW' | 'ALL'

/** One page of a paged list (matches PageResponse on the backend; `page` starts at 0). */
export interface Page<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

/** Minimal user reference used inside other objects (manager, assignee, requester). */
export interface UserSummary {
  id: string
  fullName: string
  email: string
}

/** Full user account, as returned to admins and to the user themselves. */
export interface User {
  id: string
  email: string
  fullName: string
  jobTitle: string | null
  active: boolean
  roles: RoleName[]
  createdAt: string
}

/** Result of login / refresh. The refresh token is not here: it travels only in an HttpOnly cookie. */
export interface AuthResponse {
  accessToken: string
  tokenType: 'Bearer'
  /** When the access token stops being accepted; the client refreshes on the first 401 after that. */
  expiresAt: string
  user: User
}

/** A project with derived numbers (task progress, member count) computed by the server. */
export interface Project {
  id: string
  name: string
  description: string | null
  status: ProjectStatus
  manager: UserSummary
  /** Set when the project is created; after that it changes only through an approved budget request. */
  budget: number | null
  startDate: string | null
  dueDate: string | null
  totalTasks: number
  doneTasks: number
  progressPercent: number
  memberCount: number
  /** Whether the caller may edit this project; the UI hides management actions when false. */
  canManage: boolean
  createdAt: string
  updatedAt: string
}

/** One member of a project team; `manager` marks the project's manager. */
export interface ProjectMember {
  userId: string
  fullName: string
  email: string
  manager: boolean
  joinedAt: string
}

/** Id + display name of a related item, enough to render a link. */
export interface Ref {
  id: string
  name: string
}

/** A task as shown in lists and on its detail page. */
export interface Task {
  id: string
  project: Ref
  title: string
  description: string | null
  status: TaskStatus
  priority: TaskPriority
  assignee: UserSummary | null
  reporter: UserSummary
  dueDate: string | null
  /** Computed by the server with its own clock, so every user sees the same answer. */
  overdue: boolean
  /** When true, the task reaches DONE only through an approved completion request. */
  requiresApproval: boolean
  archived: boolean
  completedAt: string | null
  createdAt: string
  updatedAt: string
  /** Permissions for the caller: whether they may edit the task, and which statuses they may move it to. */
  canEdit: boolean
  allowedStatuses: TaskStatus[]
}

/** An approval request (task completion or budget change) and its decision, if any. */
export interface Approval {
  id: string
  type: ApprovalType
  status: ApprovalStatus
  project: Ref
  task: Ref | null
  requester: UserSummary
  reviewer: UserSummary | null
  reason: string
  requestedBudget: number | null
  /** The project's budget today, shown next to the requested one so reviewers can compare. */
  currentBudget: number | null
  reviewComment: string | null
  decidedAt: string | null
  createdAt: string
  /** Computed per caller with the server's own rules; the UI only renders the actions that are allowed. */
  canDecide: boolean
  canCancel: boolean
}

/** An in-app notification. `referenceType` + `referenceId` tell the UI which page to open. */
export interface Notification {
  id: string
  type: 'TASK_ASSIGNED' | 'APPROVAL_REQUESTED' | 'APPROVAL_DECIDED' | 'PROJECT_MEMBERSHIP_ADDED'
  title: string
  message: string
  referenceType: 'TASK' | 'PROJECT' | 'APPROVAL' | null
  referenceId: string | null
  read: boolean
  createdAt: string
}

/** One audit-trail entry: who (actor) did what (action) to which item (entity), and when. */
export interface AuditEntry {
  id: string
  occurredAt: string
  actorId: string | null
  /** Null when the entry has no acting user (actorId is optional in the audit table). */
  actorName: string | null
  action: string
  entityType: 'USER' | 'PROJECT' | 'TASK' | 'APPROVAL'
  entityId: string
  projectId: string | null
  /** Safe details of the change (e.g. old and new status). Never contains secrets. */
  metadata: Record<string, string>
}

/** Everything the dashboard renders, already limited to the caller's projects. */
export interface Dashboard {
  kpis: {
    totalProjects: number
    activeProjects: number
    totalTasks: number
    completedTasks: number
    overdueTasks: number
    pendingApprovals: number
  }
  tasksByStatus: Record<TaskStatus, number>
  tasksByPriority: Record<TaskPriority, number>
  projectProgress: {
    id: string
    name: string
    status: ProjectStatus
    totalTasks: number
    doneTasks: number
    progressPercent: number
  }[]
  overdueTasks: Task[]
  approvalsToReview: Approval[]
  recentActivity: AuditEntry[]
}
