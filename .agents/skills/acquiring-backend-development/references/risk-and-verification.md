# Backend Risk And Verification

Read this reference for R2/R3 work and for changes involving persistent state, public contracts,
security, messages, SQL, or financial calculations.

## Ready To Start

A change is ready only when the following are known:

- business goal and explicit non-goals;
- owning module and affected contracts;
- source-of-truth evidence and unresolved conflicts;
- requirement level and risk level;
- protected invariants;
- verification scope and required environment;
- compatibility, rollout, and rollback/containment needs.

For a new or stricter startup gate, also require an environment matrix, a non-secret repository-safe
bootstrap/provisioning path, compatibility for existing persisted data, actionable failure text, and
focused startup-contract verification for every service that reads the configuration. Removing a
fallback without a usable local-development path is not a complete security change.

If an R3 item is unknown and the answer changes behavior, stop for a decision.

## Risk Levels

| Level | Typical scope | Minimum evidence |
| --- | --- | --- |
| R0 | Documentation/comment with no behavior change | Link/path validation and diff review |
| R1 | Local implementation with no contract or persistence impact | Affected tests plus compile/type checks |
| R2 | API, SQL, configuration, cache, message, permission, or cross-module behavior | Owner and dependent tests, compatibility scenarios |
| R3 | Money, settlement, state machine, idempotency, crypto, public API, callback, reconciliation, production migration | Focused automated regression, negative/duplicate paths, rollback or containment |

## Domain Checklists

### OpenAPI And Security

- Confirm the endpoint category and trust boundary before applying annotations.
- Verify authentication, merchant/key binding, decryption, validation, anti-replay, authorization,
  response encryption, audience selection, and stable code mapping.
- Preserve published paths, headers, algorithms, fields, codes, and SDK compatibility.
- Test failure paths and inspect logs for sensitive values.

### Idempotency And State

- Define the idempotency key, scope, retention, durable constraint, and duplicate response.
- Define allowed current states and the conditional write that prevents stale/terminal overwrite.
- Separate confirmed decline/failure from timeout or unknown outcome.
- Verify duplicate requests, callbacks, messages, scheduler retries, and concurrency where relevant.

### Money And Settlement

- Identify each amount's meaning, currency, exponent, sign, precision, rounding point, and source.
- Verify rate direction, source, effective time, locking scope, and aggregation order.
- Keep transaction, fee, reserve, clearing, settlement, and ledger values distinct.
- Test zero, boundaries, exponent differences, rounding ties, partial/cumulative operations, and
  reconciliation totals.

### SQL And MyBatis

- Match the owning schema and existing audit-column convention.
- Verify nullability, defaults, precision, indexes, unique constraints, soft-delete interaction, and
  online compatibility.
- State changes and claims use current-state predicates; money/idempotency writes have durable
  protection.
- A migration needs a precheck, post-check, and rollback or forward-fix strategy. Never execute it
  against a live database without explicit approval.

### Channels And Messaging

- Map channel raw evidence to platform outcomes without hiding uncertainty.
- Confirm timeout/retry behavior and provider idempotency semantics.
- Publish after commit through the reliable mechanism used by the module.
- Consumers assume at-least-once delivery and protect side effects durably.

## Verification Commands

Choose commands from the owning module rather than copying them blindly:

```bash
mvn -pl <module> -am -DskipTests compile
mvn -pl <module> -am -Dtest=<FocusedTest> -Dsurefire.failIfNoSpecifiedTests=false test
mvn -pl <module> -am test
```

Use JDK 17. Record when infrastructure prevents a check; do not convert an unavailable check into a
pass. Live channel tests and production-like migrations require separate authorization and safe test
credentials.

## Done

The change is done only when:

- approved behavior and non-goals are satisfied;
- P0 invariants and compatibility are preserved;
- tests demonstrate changed behavior and relevant negative paths;
- documentation/contracts changed with the implementation where required;
- commands and outcomes are reported accurately;
- exceptions name an owner, reason, risk, expiry/review point, and compensating control.
