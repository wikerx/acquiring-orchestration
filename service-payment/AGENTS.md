# Service Payment Rules

## Ownership

`service-payment` owns acquiring transaction facts, operations, legal state transitions, channel
execution records, idempotency, callback processing, and transaction events.

## P0 Rules

- A transaction and each operation have stable business identifiers and a documented idempotency
  scope. Durable uniqueness or conditional persistence must protect duplicate requests.
- Validate every transition against persisted current state. Terminal facts cannot be overwritten by
  a late response, callback, retry, poll, or duplicate message.
- Keep platform state, channel raw state, request-processing result, and merchant-visible result
  separate. An unknown channel outcome is not a confirmed failure.
- Persist channel request/response evidence with redaction and correlation identifiers. Never expose
  raw channel payloads directly to merchants.
- Publish events through a transactionally reliable outbox or equivalent mechanism after the owning
  fact is committed. Consumers and callback workers remain idempotent.
- Model transaction, authorized, captured, refunded, fee, and settlement amounts explicitly with
  currency and precision. Do not reconstruct money from display strings or floating-point values.
- A refund, void, capture, incremental authorization, reversal, and callback must verify the original
  transaction, allowed amount, cumulative amount, operation order, and legal state.

## Boundaries

- API/internal controllers adapt requests; application services orchestrate; state/domain services
  decide rules; repositories persist facts; channel adapters live under `channel-library`.
- Risk, clearing, settlement, and merchant notification are downstream collaborators, not reasons to
  duplicate their facts in this service.
- Do not expand a simulation or compatibility class into the permanent transaction engine. Propose a
  scoped migration when ownership is unclear.

## Required Verification

Behavior changes cover the normal case plus duplicate request, concurrent request where relevant,
illegal/terminal transition, channel decline, timeout or unknown result, duplicate callback/message,
and outbox retry. Money changes include zero, boundary, exponent, rounding, partial and cumulative
operation cases as applicable.
