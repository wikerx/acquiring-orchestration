# Service Data Development Rules

## Scope And Ownership

`service-data` owns asynchronous non-core persistence, merchant callback delivery, audit ingestion,
and the controlled card vault. It does not own payment, payout, clearing, settlement, or risk state
machines. A callback task reflects an already committed upstream fact and must never rewrite it.

## Merchant Callback Invariants

- Treat callback delivery as at least once. The stable callback event ID is the merchant-visible
  idempotency key; database version CAS protects each local state transition.
- Commit the attempt log, task result, and next retry outbox in one transaction on the transaction
  shard. Never acknowledge an MQ command from an in-memory result alone.
- `SUCCESS` and `CLOSED` are terminal for the automatic plan. A manual retry is a separate command
  with its own stable event ID and must not silently restart or truncate the automatic plan.
- An unknown transport result remains retryable. A target-policy rejection or non-transient HTTP
  client error closes the automatic plan. Do not map uncertainty to success.
- Production and unknown profiles allow only public HTTPS callback targets. Validate both the URL
  before sending and every DNS address used by the actual connection. Redirects, automatic HTTP
  retries, system proxies, and cookies stay disabled.
- JWTs, callback ciphertext, security material, raw merchant responses, and callback URLs with
  query data must not enter application logs or audit tables. Store only approved masked metadata,
  byte counts, or irreversible digests.

## Messaging And Persistence Invariants

- Assume RocketMQ redelivery. Redis is an acceleration layer only; database unique constraints,
  inbox records, or conditional updates provide final idempotency.
- On persistence failure, release only the Redis claim acquired by the current consumer. A stale
  Redis duplicate must not suppress a database write that is still missing.
- Preserve the event's original business time and the transaction shard key. Consumer wall-clock
  time is audit metadata and must not replace source time.
- Mapper SQL owns data access only. State updates require the expected current state and version,
  and terminal rows must not be overwritten by recovery or duplicate messages.

## Card Vault Invariants

- CVV is never accepted, persisted, logged, or represented by an at-rest model.
- Transfer envelopes must authenticate their metadata. At-rest PAN, expiry, and cardholder fields
  use authenticated encryption with record- and purpose-bound AAD; each record uses a fresh DEK.
- PAN lookup material uses keyed HMAC with a versioned secret. A raw PAN hash is not an acceptable
  index. Plaintext and DEKs remain within the shortest possible call scope and are never logged.
- Message ID and merchant/transaction/checkout identity must agree when a unique-key duplicate is
  treated as idempotent. A conflicting duplicate fails closed.

## Code And Verification

- Every class and method has concrete multiline Chinese Javadoc. Comments explain ownership,
  idempotency, state transitions, security boundaries, failure behavior, or compatibility; do not
  add author/date/version templates, `{@inheritDoc}`, or generated field descriptions.
- Prefer typed request, response, configuration, status, and failure models. Maps remain acceptable
  only for genuinely dynamic infrastructure collections such as a bounded in-process cache.
- Logs use stable event codes and traceable business identifiers while excluding raw payloads and
  security material.
- Callback changes require tests for success, permanent rejection, transient failure, duplicate
  delivery, CAS conflict, stale-processing recovery, SSRF/DNS rebinding, and redaction. Card-vault
  changes require encryption round-trip, AAD tamper rejection, duplicate identity, and no-CVV
  coverage. Run `mvn -pl service-data -am test` with JDK 17 after focused tests pass.
