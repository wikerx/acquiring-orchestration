# Channel Library Rules

## Ownership

`channel-library` defines payment/payout channel contracts, execution support, and channel-specific
adapters. It translates between platform requests and channel protocols.

## P0 Rules

- Keep platform transaction state machines and merchant-specific rules out of channel adapters.
- Preserve the channel's raw status/code as evidence, then map it explicitly to the platform's
  normalized outcome and failure classification.
- Model success, confirmed decline, retryable technical failure, timeout, and unknown outcome
  separately. Do not convert uncertainty into a confirmed failure.
- Apply channel-specific authentication, signature, timeout, retry, callback, and redaction rules.
- Never log PAN, CVV, credentials, keys, complete payloads, or unredacted beneficiary data.
- Retries must respect operation idempotency and the channel's duplicate-request semantics.

## Required Verification

Adapter changes cover request mapping, success, decline, malformed response, timeout/unknown result,
signature failure, duplicate callback, status mapping, and log redaction. Live tests remain opt-in and
must never use production credentials by default.
