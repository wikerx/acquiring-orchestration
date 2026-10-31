# Service Settlement Rules

## Ownership

`service-settlement` owns settlement-candidate activation, batch claim, batch-level rate locking,
aggregation, idempotent account posting, reserve funding, transaction projection, and settlement
outbox events.

## P0 Rules

- A clearing fact may enter the eligible settlement path once. Candidate activation, batch claim,
  posting, and projection each require durable idempotency.
- Lock the settlement rate with source, direction, precision, effective time, and batch identity.
  Never mix rates within a batch unless the approved contract explicitly requires it.
- Preserve original-currency inputs and target-currency outputs. Perform rounding only at the defined
  boundary and keep reconciliation evidence.
- Account posting and status change must have one clear transaction/compensation design. A retry must
  not duplicate a ledger entry or reserve movement.
- Batch state transitions are conditional and auditable. Cancellation/reversal uses explicit facts;
  it must not erase prior financial evidence.
- Maker-checker and operator restrictions are enforced server side for manual settlement actions.
- Publish settlement events after commit using a reliable outbox or equivalent mechanism.

## Required Verification

Cover competing claims, duplicate activation/posting, direct/reverse/identity rate direction, currency
exponents and rounding, insufficient/negative balance policy, partial failure, retry/recovery,
maker-checker separation, cancellation/reversal, projection retry, and reconciliation totals.
