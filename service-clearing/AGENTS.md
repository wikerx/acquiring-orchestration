# Service Clearing Rules

## Ownership

`service-clearing` consumes eligible terminal transaction facts and creates immutable fee and reserve
clearing facts. It does not choose settlement FX, post balances, or own settlement batches.

## P0 Rules

- Consume terminal transaction facts idempotently and retain the source transaction/event identity.
- Freeze the fee/reserve rule version and all calculation inputs used for the clearing result.
- A duplicate or replayed event must not create duplicate clearing facts.
- Corrections use an explicit reversal/adjustment fact; do not mutate an already consumed financial
  fact invisibly.
- Keep transaction currency amounts, fee components, reserve components, and their rounding evidence
  explicit. Do not introduce settlement exchange rates here.
- Publish clearing completion through a reliable event mechanism after commit.

## Required Verification

Cover eligible/ineligible terminal states, duplicate events, fee and reserve boundaries, zero and
partial amounts, reversal/adjustment, publication retry, and the absence of settlement FX or account
posting side effects.
