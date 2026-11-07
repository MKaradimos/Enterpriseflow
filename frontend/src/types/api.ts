// TypeScript mirror of the backend DTOs. Keep in sync with the Java records in backend/src/main/java/**/dto.

export type RoleName = 'ADMIN' | 'MANAGER' | 'EMPLOYEE'
export type ProjectStatus = 'PLANNED' | 'ACTIVE' | 'ON_HOLD' | 'COMPLETED' | 'ARCHIVED'
export type TaskStatus = 'TODO' | 'IN_PROGRESS' | 'IN_REVIEW' | 'DONE' | 'BLOCKED'
export type TaskPriority = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL'
export type ApprovalType = 'TASK_COMPLETION' | 'PROJECT_BUDGET_CHANGE'
export type ApprovalStatus = 'PENDING' | 'APPROVED' | 'REJECTED' | 'CANCELLED'
export type ApprovalScope = 'MINE' | 'TO_REVIEW' | 'ALL'

export interface Page<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export interface UserSummary {
  id: string
  fullName: string
  email: string
}

export interface User {
  id: string
  email: string
  fullName: string
  jobTitle: string | null
  active: boolean
  roles: RoleName[]
  createdAt: string
}

// No refresh token here on purpose, it only lives in an HttpOnly cookie.
export interface AuthResponse {
  accessToken: string
  tokenType: 'Bearer'
  expiresAt: string
  user: User
}

export interface Project {
  id: string
  name: string
  description: string | null
  status: ProjectStatus
  manager: UserSummary
  budget: number | null
  startDate: string | null
  dueDate: string | null
  totalTasks: number
  doneTasks: number
  progressPercent: number
  memberCount: number
  // Computed by the server for the current user.
  canManage: boolean
  createdAt: string
  updatedAt: string
}

export interface ProjectMember {
  userId: string
  fullName: string
  email: string
  manager: boolean
  joinedAt: string
}

export interface Ref {
  id: string
  name: string
}

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
  overdue: boolean
  requiresApproval: boolean
  archived: boolean
  completedAt: string | null
  createdAt: string
  updatedAt: string
  // Per-user permissions from the server; the UI just follows them.
  canEdit: boolean
  allowedStatuses: TaskStatus[]
}

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
  // Shown next to requestedBudget so the reviewer can compare.
  currentBudget: number | null
  reviewComment: string | null
  decidedAt: string | null
  createdAt: string
  canDecide: boolean
  canCancel: boolean
}

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

export interface AuditEntry {
  id: string
  occurredAt: string
  actorId: string | null
  actorName: string | null
  action: string
  entityType: 'USER' | 'PROJECT' | 'TASK' | 'APPROVAL'
  entityId: string
  projectId: string | null
  metadata: Record<string, string>
}

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
