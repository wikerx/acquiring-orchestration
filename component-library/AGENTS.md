# Component Library Rules

## Ownership

`component-library` contains reusable technical capabilities shared by services. It does not own
merchant, payment, payout, risk, clearing, or settlement business decisions. A component may expose
an extension point, but the consuming service owns policy and business state.

## P0 Rules

- Preserve compatibility for shared public types, configuration keys, bean names, serialization,
  cache keys, message envelopes, and database helpers unless a coordinated migration is approved.
- Do not add service-specific status machines, routing policy, fee logic, merchant decisions, or
  channel outcomes to a shared component.
- Security, HTTP, Redis, MQ, database, and web components must fail closed where uncertainty could
  expose data, weaken authentication, or duplicate a money-side effect.
- Never log secrets, credentials, JWTs, decrypted payloads, PAN, CVV, complete account data, or raw
  message bodies. Shared redaction utilities must be conservative when input cannot be parsed.
- Shared retry, cache, lock, inbox, and outbox helpers must document ownership, idempotency scope,
  timeout, failure, and cleanup semantics. Redis alone is not durable money protection.

## Architecture And Verification

- Keep each child module narrowly technical; avoid generic business `Manager`, `Helper`, or `Util`
  classes that accumulate service logic.
- Add dependencies only when every intended consumer can accept their runtime and transitive impact.
- Test compatibility, concurrency, timeout, duplicate delivery, redaction, and failure cleanup for
  the changed capability. Run the affected child module and at least one owning consumer when a
  shared contract changes.
