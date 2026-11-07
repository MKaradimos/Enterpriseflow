# ADR 0001 — Modular monolith

**Status:** Accepted
**Date:** 2026-11-07

## Context

EnterpriseFlow has several business areas (users, projects, tasks, approvals, audit, notifications)
that interact closely: an approval changes a task, a task change writes an audit entry, and so on.
The team is one developer and the system runs as a single deployment.

## Decision

Build a single Spring Boot application organized into feature modules
(`auth`, `user`, `project`, `task`, `approval`, `audit`, `notification`, `dashboard`). Each package holds its
controller, service, repository and entities, with request/response records in a `dto` sub-package.

Modules communicate through each other's public services and entity methods, or through Spring application
events — never with raw queries against another module's tables. Cross-module side effects (revoking sessions on deactivation, unassigning tasks when a
member leaves) are events, which keeps the dependency graph acyclic.

## Consequences

- One deployment, one database, local transactions: no distributed consistency problems.
- Clear module boundaries keep the option of extracting a service later if a real need appears.
- Boundaries are a convention today; adding ArchUnit tests to enforce them in the build is a planned improvement.

## Alternatives considered

- **Microservices** — rejected: network calls, distributed transactions and per-service
  infrastructure add cost without a scaling or team-autonomy need to justify them.
- **Layer-based packages** (`controllers/`, `services/`) — rejected: spreads every feature across
  the codebase and hides the boundaries between business areas.
