# API

Base path `/api/v1`. Interactive documentation: http://localhost:8080/swagger-ui.html (dev profile). Log in with
`POST /api/v1/auth/login`, copy `accessToken`, then use **Authorize**.

## Conventions

| Topic | Convention |
|-------|-----------|
| Auth | `Authorization: Bearer <accessToken>` on every endpoint except health, docs, `auth/csrf`, `auth/login`, `auth/register`, `auth/refresh`, `auth/logout` |
| Pagination | `?page=0&size=20&sort=updatedAt,desc` (max size 100; sort properties are allow-listed per endpoint) |
| Page shape | `{ "content": [...], "page": 0, "size": 20, "totalElements": 42, "totalPages": 3 }` |
| Errors | RFC 9457 Problem Details, `Content-Type: application/problem+json` |
| Dates | `LocalDate` as `2026-11-07`, instants as ISO-8601 UTC `2026-11-07T10:15:30Z` |

### Status codes

| Code | When |
|------|------|
| 200 | Successful read or update |
| 201 | Created (with `Location` header) |
| 204 | Success without body (logout, archive task, remove member, change password) |
| 400 | Validation failure, unknown sort property, business input rule (e.g. assignee not a member) |
| 401 | Missing, invalid, expired or foreign token; wrong credentials; invalid refresh token |
| 403 | Authenticated but not allowed (role or project permission); missing CSRF token on refresh/logout |
| 404 | Not found **or not visible to the caller** (prevents probing other projects' ids) |
| 409 | State conflict: invalid task transition, archived project, duplicate pending approval, already decided, concurrent modification |

### Error examples

```json
{
  "type": "about:blank",
  "title": "Validation error",
  "status": 400,
  "detail": "Request validation failed.",
  "instance": "/api/v1/projects",
  "errors": [{ "field": "name", "message": "must not be blank" }]
}
```

```json
{
  "type": "about:blank",
  "title": "Conflict",
  "status": 409,
  "detail": "A task cannot move from TODO to DONE.",
  "instance": "/api/v1/tasks/8b0c.../status"
}
```

## Endpoints

### Auth — `/api/v1/auth`

| Method | Path | Description |
|--------|------|-------------|
| GET | `/csrf` | CSRF token for `/refresh` and `/logout` (`X-XSRF-TOKEN` header) |
| POST | `/register` | Self-registration as EMPLOYEE (only if enabled) |
| POST | `/login` | Returns access token; sets refresh cookie |
| POST | `/refresh` | Rotates refresh cookie; returns new access token |
| POST | `/logout` | Revokes refresh token family; clears cookie |
| GET | `/me` | Current user |

### Users & profile

| Method | Path | Who |
|--------|------|-----|
| GET | `/users?q=&role=&active=` | ADMIN |
| GET | `/users/lookup?q=` | ADMIN, MANAGER (active users, for pickers) |
| GET | `/users/{id}` | ADMIN |
| POST | `/users` | ADMIN |
| PUT | `/users/{id}/roles` | ADMIN (cannot remove own ADMIN role; last active admin protected) |
| PATCH | `/users/{id}/status` | ADMIN (cannot deactivate self) |
| GET / PATCH | `/profile` | Any user, own profile |
| POST | `/profile/password` | Any user; ends all sessions |

### Projects

| Method | Path | Who |
|--------|------|-----|
| GET | `/projects?q=&status=&includeArchived=` | Members see their projects; ADMIN sees all |
| POST | `/projects` | MANAGER, ADMIN |
| GET | `/projects/{id}` | Members, ADMIN |
| PUT | `/projects/{id}` | Project manager, ADMIN |
| PUT | `/projects/{id}/manager` | ADMIN |
| POST | `/projects/{id}/archive` | Project manager, ADMIN |
| POST | `/projects/{id}/restore` | ADMIN |
| GET | `/projects/{id}/activity` | Members, ADMIN |
| GET / POST | `/projects/{id}/members` | View: members; add: project manager, ADMIN |
| DELETE | `/projects/{id}/members/{userId}` | Project manager, ADMIN |

### Tasks

| Method | Path | Who |
|--------|------|-----|
| GET | `/tasks?projectId=&status=&priority=&assigneeId=&assignedToMe=&dueBefore=&overdue=&q=` | Scoped to visible projects |
| GET / POST | `/projects/{id}/tasks` | View: members; create: project manager, ADMIN |
| GET | `/tasks/{id}` | Members, ADMIN |
| PUT | `/tasks/{id}` | Project manager, ADMIN |
| PATCH | `/tasks/{id}/status` | Assignee, project manager, ADMIN (reopen: manager/ADMIN only) |
| DELETE | `/tasks/{id}` | Project manager, ADMIN (archives) |
| GET | `/tasks/{id}/activity` | Members, ADMIN |

### Approvals

| Method | Path | Who |
|--------|------|-----|
| GET | `/approvals?scope=MINE\|TO_REVIEW\|ALL&status=` | Scoped |
| POST | `/approvals` | Task completion: assignee or manager; budget change: project manager or ADMIN |
| GET | `/approvals/{id}` | Requester, project members, ADMIN |
| POST | `/approvals/{id}/approve` | Completion: project manager or ADMIN; budget: ADMIN. Never the requester |
| POST | `/approvals/{id}/reject` | Same as approve; comment required |
| POST | `/approvals/{id}/cancel` | Requester, while pending |

### Notifications, dashboard, audit

| Method | Path | Who |
|--------|------|-----|
| GET | `/notifications?unreadOnly=&page=&size=` | Own |
| GET | `/notifications/unread-count` | Own |
| POST | `/notifications/{id}/read`, `/notifications/read-all` | Own |
| GET | `/dashboard` | Any user (scoped) |
| GET | `/audit-logs?entityType=&entityId=&action=&actorId=&projectId=` | ADMIN |

## Walkthrough (curl)

```bash
API=http://localhost:8080/api/v1

login() {  # $1 = email; prints the access token, stores the refresh cookie in cookies-<user>.txt
  curl -s -c "cookies-${1%@*}.txt" -H 'Content-Type: application/json' \
    -d "{\"email\":\"$1\",\"password\":\"Demo12345!\"}" "$API/auth/login" | jq -r .accessToken
}

EVA=$(login eva.employee@enterpriseflow.local)
MARIA=$(login maria.manager@enterpriseflow.local)

# 1. Eva's tasks
curl -s -H "Authorization: Bearer $EVA" "$API/tasks?assignedToMe=true" | jq '.content[] | {id, title, status}'

# 2. An employee can't create a project → 403
curl -s -o /dev/null -w '%{http_code}\n' -H "Authorization: Bearer $EVA" \
  -H 'Content-Type: application/json' -d '{"name":"Nope"}' "$API/projects"

# 3. Maria reviews pending approvals and approves one
ID=$(curl -s -H "Authorization: Bearer $MARIA" "$API/approvals?scope=TO_REVIEW" | jq -r '.content[0].id')
curl -s -H "Authorization: Bearer $MARIA" -H 'Content-Type: application/json' \
  -d '{"comment":"Verified on staging"}' "$API/approvals/$ID/approve" | jq '{status, reviewer: .reviewer.fullName}'

# 4. Approving again → 409 (already decided)
curl -s -H "Authorization: Bearer $MARIA" -H 'Content-Type: application/json' -d '{}' \
  "$API/approvals/$ID/approve" | jq '{status, detail}'

# 5. Refresh with CSRF (cookie + header)
CSRF=$(curl -s -b cookies-maria.manager.txt -c cookies-maria.manager.txt "$API/auth/csrf" | jq -r .token)
curl -s -b cookies-maria.manager.txt -c cookies-maria.manager.txt -X POST \
  -H "X-XSRF-TOKEN: $CSRF" "$API/auth/refresh" | jq '{expiresAt, user: .user.email}'
```
