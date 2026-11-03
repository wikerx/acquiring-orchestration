# Service Checkout Rules

## Ownership

`service-checkout` owns the hosted payer-session protocol and its controlled handoff to payment
services. It does not decide platform transaction state or fabricate payment results.

## P0 Rules

- Bind every checkout session to its merchant, order, amount, currency, expiry, allowed origin, and
  return/callback policy. Client input cannot change an established session fact.
- Session tokens are unguessable, purpose-bound, time-bounded, and replay controlled. Redirect and
  return URLs follow the approved origin policy.
- CVV is transient only and is never persisted or logged. PAN, tokens, cardholder data, decrypted
  payloads, and complete payment responses must not enter logs, URLs, analytics, or error messages.
- Prevent unsafe duplicate submission. HTTP success is not payment success, and timeout or unknown
  outcomes must remain pending/unknown rather than trigger an automatic duplicate payment.
- PAYER messages are concise and safe; merchant or INTERNAL diagnostics must not leak into the payer
  response.

## Verification

Cover session ownership, expiry, replay, origin/redirect validation, duplicate submission, success,
decline, pending/unknown, challenge/3DS handoff, malformed/tampered input, audience messages, and
sensitive-data lifecycle.
