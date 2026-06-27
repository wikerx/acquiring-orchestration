# Backend Constraints Reference

This reference expands the backend rules for the `acquiring-orchestration` project.

## Layering

- Controller: request validation, auth/permission annotations, calling application/service layer.
- Application/Service: business orchestration and business rules.
- Mapper: persistence only; no business rules.
- Entity: persistence model only.
- DTO/VO: external/input/output models.
- Enum: reuse existing enum conventions.

## Minimal Change Principle

- Prefer wrapping existing logic instead of rewriting it.
- Preserve existing field names where compatibility matters.
- Avoid wide refactors during bug fixes.
- If wide refactor is beneficial, propose it as a separate plan.

## Payment Safety

Always check:

- idempotency key;
- unique constraints;
- duplicate callbacks;
- duplicate MQ consumption;
- status transitions;
- terminal status immutability;
- amount precision;
- currency direction;
- exchange rate source;
- reconciliation and settlement impact.

## Time

- Database schema changes should use datetime(3).
- UI should not show milliseconds.
- Sharding should use transaction_date_time when applicable.
