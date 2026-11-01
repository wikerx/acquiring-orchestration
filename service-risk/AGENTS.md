# Service Risk Development Rules

## Scope

These rules apply to `service-risk` production code, tests, configuration, SQL access, Redis
scripts, schedulers, and message consumers. The repository root `AGENTS.md` remains authoritative
for platform-wide constraints.

## Protected Invariants

- Preserve the configured stage order and short-circuit behavior. Read-only parallelism must not
  include Redis reservations, state transitions, message publication, or other side effects.
- Keep `PASS`, `SKIP`, `REVIEW`, `REJECT`, and `REQUIRE_3DS` mappings stable. Infrastructure
  failures, timeouts, invalid rule configuration, and unknown outcomes must fail closed.
- Merchant cumulative limits use six-decimal integer units. Conversion must be exact and must not
  introduce implicit rounding, `double`, or `float`.
- A cumulative-limit reservation must be durably prepared before its Redis projection is applied.
  Later rejection or failure must use the existing idempotent rollback and cancellation path.
- Redis accelerates evaluation and maintains projections; it is not the sole source of truth for
  durable reservation state or terminal decisions.
- Rule-cache generation changes are compatibility boundaries. Cache misses and cache failures may
  fall back to the owning database query only where the existing rule mode explicitly allows it.
- Payment lifecycle consumers assume at-least-once delivery. Preserve database uniqueness,
  conditional transitions, idempotency keys, and terminal-state protection.

## Architecture

- Controllers adapt internal HTTP requests; application services orchestrate; services own risk
  decisions; repositories own data access and cache coordination; Mappers contain data access only.
- Keep frequency-rule JSON in typed configuration objects. Unknown extension fields remain
  compatible, while invalid known fields produce an unavailable or review result.
- Split runtime support by responsibility when a class mixes rule evaluation, cache mechanics,
  reservations, and audit conversion. Do not introduce generic `Helper`, `Manager`, or `Util`
  classes.
- Every class and method must have concrete multiline Chinese Javadoc. Comments explain risk
  meaning, failure behavior, idempotency, compatibility, or a non-obvious implementation choice.
  Do not use `{@inheritDoc}` or author, date, email, version, status, or class-name templates.

## Logging

- Use a concise Chinese description followed by a stable English event code.
- Use lowerCamelCase fields in `field: {}` form, separated by ASCII commas.
- Never log PAN, CVV, raw card or account data, identity data, secrets, JWTs, API keys, decrypted
  payloads, raw rule inputs, or complete Redis keys. Use irreversible digests where correlation is
  necessary.

## Verification

- For local refactors, run focused tests for the changed responsibility before the full module.
- For decision, timeout, reservation, Redis, MQ, or Mapper changes, cover the affected negative,
  duplicate, unknown, and rollback paths.
- Run the full `service-risk` test suite with JDK 17 before completion. Environment-disabled MySQL
  or Redis integration tests must be reported as skipped rather than passed.
