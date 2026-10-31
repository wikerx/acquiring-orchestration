# Finance Library Rules

## Ownership

`finance-library` contains reusable, pure Java financial value objects and deterministic calculations.
It is versioned as one unit through `finance-library.version`.

## P0 Rules

- No Spring beans, controllers, persistence, Mapper/DO types, Redis, MQ, Nacos, network calls, clocks,
  or environment-dependent behavior.
- Functions are deterministic for explicit inputs. Pass currency, exponent, rate direction, rounding
  mode, rule version, and effective values rather than reading hidden configuration.
- Use `BigDecimal` safely: construct from strings or exact integers, compare with `compareTo`, specify
  scale/rounding at the business boundary, and never use floating point for financial values.
- `model-money` owns money/currency values, not exchange rates. Fee, reserve, and settlement models
  remain semantically separate.
- `core-reserve` must not introduce FX. Settlement conversion belongs in settlement models/core.
- Public model fields and calculation semantics are contracts. A breaking change requires a major
  version decision and migration analysis.

## Required Verification

Pure calculations require table-driven unit tests covering sign, zero, boundaries, currency
exponents, direct/reverse/identity rates, rounding ties, aggregation order, and invariants such as
conservation and maximum limits. Do not replace business assertions with snapshot-only tests.
