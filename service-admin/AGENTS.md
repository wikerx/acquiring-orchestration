# Service Admin Rules

## Ownership

`service-admin` owns platform-operator use cases, permissions, data scope, review workflows, and
operator-facing projections. It does not directly own payment, payout, clearing, settlement, or risk
facts and must use their approved commands and query contracts.

## P0 Rules

- Enforce operator authentication, role/button/field permissions, merchant data scope, and
  maker-checker separation on the server. Frontend visibility is not authorization.
- Do not bypass owner-service state machines with direct table updates or reinterpret an owner
  service's terminal state.
- Administrative retries, approvals, rejections, repairs, and exports require stable operator
  identity, reason, idempotency, and auditable before/after facts.
- INTERNAL audience diagnostics may include sanitized channel code and channel message required for
  support, but never secrets, keys, complete payloads, PAN, CVV, or unredacted identity/account data.
- Bulk operations must define partial-failure behavior and cannot report success when only a subset
  was accepted.

## Verification

Cover permission and data-scope rejection, maker-checker conflicts, duplicate commands, stale
version/state, partial bulk failure, owner-service error mapping, audit persistence, and sensitive
field projection.
