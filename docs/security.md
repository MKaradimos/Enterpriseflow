# Security

## Authentication flow

```mermaid
sequenceDiagram
  autonumber
  participant B as Browser (SPA)
  participant A as API
  participant D as PostgreSQL
  B->>A: POST /auth/login {email, password}
  A->>D: load user by email
  A->>A: BCrypt verify, check active
  A->>D: store SHA-256(refresh token), new family
  A-->>B: 200 {accessToken (15 min)} + Set-Cookie ef_refresh (HttpOnly, SameSite=Strict, Path=/api/v1/auth)
  B->>A: GET /api/v1/projects  Authorization: Bearer …
  A->>A: verify signature, exp, iss → roles
  A-->>B: 200
  Note over B,A: …15 minutes later
  B->>A: GET /api/v1/projects (expired token)
  A-->>B: 401
  B->>A: POST /auth/refresh  cookie + X-XSRF-TOKEN
  A->>D: find token by hash; revoke it; issue next in same family
  A-->>B: 200 {new accessToken} + new cookie
  B->>A: retry original request
```

### Tokens

| | Access token | Refresh token |
|---|---|---|
| Format | JWT, HS256, claims `sub`, `email`, `roles`, `iss`, `iat`, `exp` | 256-bit random, Base64URL, opaque |
| Lifetime | 15 minutes (`JWT_ACCESS_TOKEN_TTL`) | 7 days (`REFRESH_TOKEN_TTL`) |
| Client storage | JavaScript memory only | `HttpOnly` cookie, unreadable by scripts |
| Server storage | none (stateless) | SHA-256 hash only |
| Revocable | no — expires quickly | yes — per token, per family, per user |

**Why not localStorage?** Any script running on the page (an XSS bug, a compromised dependency) can read
localStorage and exfiltrate a long-lived credential. In memory, a token lives only as long as the page; the
long-lived credential is in a cookie scripts cannot read.

**Rotation and reuse detection.** Every refresh revokes the presented token and issues a new one in the same
family. If a revoked token is presented again, someone copied it: the server revokes the entire family, so both
the thief and the user must log in again. The revocation is committed even though the request fails
(`@Transactional(noRollbackFor = InvalidRefreshTokenException.class)`).

**Session invalidation.** Deactivating a user, changing their roles or changing a password publishes
`UserCredentialsInvalidatedEvent`; all refresh tokens of that user are revoked in the same transaction.

**Signing key.** `JWT_SECRET` is a Base64 key of at least 256 bits, supplied only via the environment. The
application refuses to start without it or with a shorter key. `scripts/setup-env.sh` generates one.

### Passwords

BCrypt with cost 12. Policy: 10–72 characters with a letter and a digit (72 is BCrypt's input limit). Login
errors are identical for unknown email, wrong password and deactivated account, so accounts cannot be enumerated.

## CSRF

Only `/auth/refresh` and `/auth/logout` authenticate with a cookie, so only they need CSRF protection:

1. `SameSite=Strict` — browsers don't send the cookie on cross-site requests at all.
2. Double-submit token — the SPA fetches `GET /auth/csrf` and sends the value as `X-XSRF-TOKEN`. A cross-site page
   can trigger a request but cannot read the token.

All other endpoints use the `Authorization` header, which a cross-site page cannot set, so they are not
CSRF-able. `/auth/login` is excluded deliberately: it carries no existing credential and requires a JSON body,
which a cross-site form cannot send without a CORS preflight.

## Authorization

Two layers, both server-side:

1. **Role-based** (`@EnableMethodSecurity`, `@PreAuthorize("hasRole('ADMIN')")`) for organization-wide actions:
   user management, audit search.
2. **Project-level** (`ProjectAccessService`) for everything inside a project:

| Action | ADMIN | Project manager | Member | Non-member |
|--------|-------|-----------------|--------|-----------|
| View project, tasks, members, activity | ✓ | ✓ | ✓ | 404 |
| Edit project, archive, manage members, create/edit tasks | ✓ | ✓ | 403 | 404 |
| Change task status | ✓ | ✓ | own tasks only | 404 |
| Decide task completion | ✓ | ✓ (not own request) | 403 | 404 |
| Decide budget change | ✓ (not own request) | 403 | 403 | 404 |
| Restore archived project, change manager | ✓ | 403 | 403 | 404 |

**404 vs 403.** If a user can't see a project, the API answers 404 — the same as for a non-existent id — so
project ids can't be probed (prevents insecure direct object references). If they can see it but not perform the
action, 403.

**The UI is not a security boundary.** Hidden buttons and routes are for usability; every rule above is
re-checked by the backend. Responses include server-computed flags (`canManage`, `allowedStatuses`, `canDecide`)
so the UI never has to guess.

## Other measures

| Concern | Measure |
|---------|---------|
| Error leakage | Global handler returns generic messages; stack traces only in server logs; `server.error.include-*: never` |
| Secrets | Only in `.env` (gitignored) / environment; `.env.example` holds placeholders; CI needs no secrets |
| CORS | Explicit allowed origins, credentials allowed, no wildcard |
| Sort injection | Sort properties allow-listed per endpoint |
| LIKE injection | Search terms escape `%`, `_` and `\` |
| Audit integrity | Append-only via `@Immutable` and a PostgreSQL trigger; metadata limited to ids, names, statuses — never passwords or tokens |
| Containers | Non-root users in both images; database and API bound to `127.0.0.1` in dev, not published in prod |
| Headers | nginx: `X-Content-Type-Options`, `X-Frame-Options: DENY`, `Referrer-Policy`, `server_tokens off`; `Cache-Control: no-store` on token responses |

## Local development vs production

| Setting | Local (`dev,demo`) | Production (`prod`) |
|---------|-------------------|---------------------|
| Transport | Plain HTTP on localhost | HTTPS required (TLS terminated at a reverse proxy / load balancer) |
| Refresh cookie `Secure` | `false` (`REFRESH_COOKIE_SECURE`) so it works over HTTP | forced `true` |
| Swagger UI | enabled | disabled |
| Registration | configurable | disabled |
| Demo data | seeded | refused (startup fails if combined) |
| Logs | human-readable, DEBUG for app | JSON (ECS), INFO |
| Database port | `127.0.0.1:5433` | not published |

Browsers accept `Secure` cookies on `http://localhost` in Chrome and Firefox, but not everywhere, which is why
local development disables the flag explicitly instead of relying on that.

Production checklist: HTTPS everywhere, a strong generated `JWT_SECRET` and DB password from a secret manager,
`CORS_ALLOWED_ORIGINS` set to the real origin, regular backups, and an admin created via `BOOTSTRAP_ADMIN_*`
(then remove those variables).
