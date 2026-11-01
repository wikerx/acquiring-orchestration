# Acquiring Orchestration Development Rules

## Scope

This repository is the Java 17 backend for the Vexra acquiring platform. These rules apply to
all code, configuration, SQL, tests, and technical documentation in this repository. A closer
`AGENTS.md` adds rules for its directory and takes precedence within that scope.

## Source Of Truth

When sources disagree, resolve the conflict before editing. Do not automatically choose the
strictest sentence.

1. The user's approved requirement and an approved, versioned contract or ADR.
2. Executable contracts: database schema, API models, tests, configuration schema, and public
   SDK behavior.
3. The closest applicable `AGENTS.md`.
4. Maintained documents under `docs/standards`, `docs/api`, and `docs/architecture`.
5. General manuals, historical plans, examples, and generic best practices.

Current code proves current behavior, not necessarily intended behavior. A document proves intent,
not that the code already implements it. If resolving a conflict changes an external contract,
money movement, state transition, security boundary, or database meaning, stop and ask for an
explicit decision.

## Requirement Levels

- `P0`: blocking invariant. Never bypass it silently.
- `P1`: mandatory for new and touched code. Existing violations outside the task become recorded
  technical debt, not an excuse for broad cleanup.
- `P2`: recommended improvement. Apply when it remains inside the approved scope.

Use `R0` through `R3` for change risk:

- `R0`: documentation or comment only, no behavior change.
- `R1`: local implementation change with no contract or persistent-state impact.
- `R2`: cross-module behavior, API, SQL, cache, message, permission, or configuration change.
- `R3`: money, settlement, state machine, idempotency, cryptography, public OpenAPI, callback,
  reconciliation, or production migration change.

`R2` and `R3` require explicit impact analysis and focused regression evidence. `R3` also requires
rollback or containment notes.

## Mandatory Workflow

Before editing:

1. Read the closest `AGENTS.md`, existing implementation, callers, contracts, and relevant tests.
2. State the affected modules/files, requirement level, risk level, invariants, and verification.
3. Obtain explicit approval before code modification. Read-only inspection is allowed beforehand.
4. Preserve all existing user changes. Never clean, reset, reformat, or rewrite unrelated files.

During implementation:

- Keep the change small and reviewable. Do not perform opportunistic refactors.
- Reuse existing types, result models, enums, exceptions, converters, and infrastructure.
- Treat compatibility shims as intentional until their callers and migration window are proven.
- Add or update tests in the same change when behavior changes.

After implementation:

1. Run the narrowest meaningful fresh verification first.
2. Report commands, exit codes, failures, skipped checks, and unverified risk accurately.
3. Ask before running a broad, environment-dependent regression suite.
4. Never claim success from old output or from code inspection alone.

## P0 Platform Invariants

### Money And Rates

- Use `BigDecimal` or an explicit minor-unit value object for money, fees, reserves, and rates.
- A value must carry or unambiguously derive its currency, exponent, rounding point, and rounding
  mode. Never assume every currency has two fraction digits.
- Do not use `double` or `float` in financial calculations and do not round before the business
  boundary requires it.
- Keep transaction, authorization, capture, refund, fee, reserve, clearing, and settlement amounts
  semantically distinct.

### Idempotency And State

- Every externally repeatable write, callback, scheduled retry, and message consumer must define
  an idempotency key and durable protection.
- Redis alone is not final protection for money movement. Use a unique constraint, conditional
  update, persisted inbox/outbox, or equivalent durable mechanism.
- Validate transitions against the current persisted state. Terminal states are immutable unless
  an explicit, audited compensating transition exists.
- Do not publish a business event or notify a merchant before the owning transaction commits.
- Assume messages and callbacks are delivered at least once.

### Security And Privacy

- Never log or expose PAN, CVV, private keys, secrets, JWTs, API keys, decrypted payloads, or full
  identity/bank data. Tests and documentation follow the same rule.
- External input is untrusted. Authentication, authorization, replay protection, validation, and
  audience-specific error mapping must remain server controlled.
- A path named `/internal/**` is not a security boundary by itself.
- Production configuration must fail closed for security controls; it must not silently enable
  mock channels, plaintext secrets, default passwords, or replay-protection bypasses.

## Architecture And Code Rules

- API/Controller code adapts protocols and triggers validation; Application services orchestrate
  use cases; domain/services own business rules; Mapper/Repository owns data access; Converter owns
  ordinary object mapping.
- Do not return DO/Entity objects from external interfaces or place business rules in Mapper code.
- New and materially touched dependencies use constructor injection. Do not batch-convert legacy
  field injection outside the task.
- `component-library` contains cross-service technical capabilities, not service-specific business
  rules. `channel-library` contains adapters, not the platform transaction state machine.
- Public contracts, database meanings, event schemas, and error-code semantics require an explicit
  compatibility decision. Do not infer a new contract from a frontend need.
- Comments explain business intent, invariants, compatibility, or non-obvious tradeoffs. Do not add
  author/date/email/status headers or comments that restate code.
- Logs use concise Chinese event descriptions, stable English event codes, and lowerCamelCase field
  names. Use SLF4J `field: {}` placeholders separated by ASCII commas; do not concatenate messages
  or wrap values as `[{}]`. Preserve event codes used by monitoring and keep protocol values in their
  contract language.
- Use `info` for normal key flows, `debug` for optional diagnostics, `warn` for recoverable or
  uncertain outcomes, and `error` for confirmed business failures or technical exceptions. Logging
  must not expose sensitive data or replace structured error handling and load-test continuation.
- Concrete instants use `LocalDateTime` in Java and normally `DATETIME(3)` in MySQL. Business dates
  remain `DATE`. Follow the owning table's established audit-column names; do not mix conventions
  within one table.

## Module Ownership

- `service-gateway`: routing, ingress controls, and trace/request identity.
- `service-openapi`: merchant protocol and security boundary; it does not own payment facts.
- `service-payment`: acquiring transaction facts, state machine, channel execution, and events.
- `service-payout`: payout facts, review, state machine, channel execution, and events.
- `service-clearing`: immutable terminal-transaction fee/reserve clearing facts; no settlement FX or
  account posting.
- `service-settlement`: batch FX lock, aggregation, account posting, reserve funding, and outbox.
- `service-risk`: risk decisions, 3DS policy, and limit reservation.
- `service-data`: asynchronous non-core data processing; it does not own clearing.
- `service-admin` / `service-merchant`: operator and merchant use cases, not payment-core ownership.
- `finance-library`: pure financial models and calculations with no Spring or persistence.
- `component-library`: shared technical components.
- `channel-library`: channel SPI, adapters, and channel-specific mapping.

Do not continue growing a class merely because it currently contains a simulation or legacy
implementation. Respect the ownership above and propose a scoped migration when needed.

## Verification Baseline

- Java baseline: JDK 17. Record `java -version` and `mvn -version` when build evidence matters.
- Local change: run the affected module's tests.
- Cross-module change: run the owner module and dependent modules with `-am` and
  `-Dsurefire.failIfNoSpecifiedTests=false` when selecting tests.
- SQL change: verify migration, post-check, compatibility, rollback/containment, indexes, and
  affected Mapper/DO mappings. Never execute a production migration without explicit approval.
- `R3` change: cover success, rejection, duplicate delivery, illegal transition, timeout/unknown
  result, and sensitive-log behavior as applicable.

## Documentation

- Update the nearest maintained document; do not create a report for a small code change.
- Durable standards belong in `docs/standards`; architecture decisions in `docs/architecture`;
  API contracts in `docs/api`; rollout/runbooks in `docs/deployment`; reviewed SQL in `docs/sql`.
- Historical execution plans are context, not active instructions.

## Project Skill Routing

For backend implementation, refactoring, SQL, configuration, or backend review, use
`$acquiring-backend-development` from `.agents/skills/acquiring-backend-development`.
Its workflow routes payment, OpenAPI security, idempotency/state, money/rate, SQL/MyBatis, and
regression concerns without duplicating all rules here.
