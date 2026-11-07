# ADR 0002 — Spring Boot 3.5 instead of 4.x

**Status:** Accepted
**Date:** 2026-11-07

## Context

Spring Boot 3.5 reached end of open-source support on 2026-06-30 (final OSS patch 3.5.16).
Spring Boot 4.1 is the current stable line.

Many enterprise codebases, including those common in client projects, still run on Boot 3.x.

## Decision

Use Spring Boot 3.5.16 to match the platform most likely encountered in enterprise client work.

## Consequences

- No further OSS security patches for the framework. Acceptable for a portfolio project;
  not acceptable for production, where commercial support or an upgrade would be required.
- The codebase avoids APIs removed in Boot 4 (e.g. `WebSecurityConfigurerAdapter` is already
  gone in 3.x; Jackson 2 usage is kept to standard `ObjectMapper` calls) so that an upgrade
  to 4.x stays a contained change.
- An upgrade to 4.x is a planned follow-up: Jakarta EE 11, Spring Security 7, Jackson 3, JUnit 6.
