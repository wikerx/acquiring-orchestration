# Service Payout Rules

## Ownership

`service-payout` owns payout applications, review decisions, payout transaction state, channel
execution, returns, callbacks, idempotency, and payout events.

## P0 Rules

- Separate application/review state, execution state, channel state, and accounting state.
- Creation, approval/rejection, submission, callback, return, retry, and message consumption require
  durable idempotency and legal state transitions.
- Maker-checker rules and operator identity are server controlled and auditable where approval is
  required. A user must not approve their own restricted action.
- A timeout or missing channel response is an unknown outcome until reconciled; do not mark it failed
  and automatically resubmit without duplicate-payment protection.
- Validate beneficiary, amount, currency, fee, channel limits, and cumulative effects before channel
  execution. Never log full bank-account or identity data.
- Publish committed events reliably and make downstream processing idempotent.

## Boundaries And Verification

Do not place payout state rules in OpenAPI, Admin, or a channel adapter. Changes cover duplicate and
concurrent requests, illegal/terminal transitions, approval separation, channel decline, timeout,
duplicate callback, return processing, message retry, and sensitive-data redaction as applicable.
