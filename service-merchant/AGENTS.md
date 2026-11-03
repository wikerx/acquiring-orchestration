# Service Merchant Rules

## Ownership

`service-merchant` owns authenticated merchant-portal use cases and merchant-scoped projections. It
does not own platform administration or core payment, payout, clearing, settlement, and risk state.

## P0 Rules

- Resolve merchant identity and data scope from the trusted authenticated session. Request
  parameters cannot select another merchant or elevate field/action permissions.
- Use owner-service commands and queries; never update core transaction or financial tables to make
  a portal action appear successful.
- Merchant-visible codes/messages must remain stable, actionable, and less detailed than INTERNAL
  diagnostics. Do not expose raw channel messages, internal topology, SQL, exception stacks, or
  operator-only remediation data.
- Key, callback, IP allowlist, user, and security-setting changes require authorization, audit,
  idempotency where repeatable, and safe cache invalidation after commit.
- Exports and queries enforce the same tenant and field permissions as interactive pages.

## Verification

Cover cross-merchant access denial, field/action permissions, duplicate writes, stale state,
owner-service failures, audience-specific error projection, audit records, export scope, and
sensitive-data redaction.
