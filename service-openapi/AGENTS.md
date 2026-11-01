# Service OpenAPI Rules

## Ownership

`service-openapi` is the merchant-facing protocol and security boundary. It authenticates the
merchant, validates and decrypts requests, applies replay protection, maps the request to an
internal use case, encrypts the response payload, and maps internal results to the published
OpenAPI contract. It does not own payment or payout transaction facts.

## P0 Rules

- A merchant API must follow the published versioned path, method, headers, encryption, validation,
  replay, permission, and response-envelope contract.
- Use `@VerificationAndProcessing` for merchant encrypted APIs. Browser checkout endpoints, health
  probes, channel callbacks, and internal endpoints are exceptions only when their separate trust
  boundary is explicit and tested.
- Keep V1.3 request-level results separate from transaction results. An outer `A0000` does not prove
  that the payment, refund, or payout succeeded.
- Public codes come from `OpenApiResultCode` and its registry. Do not invent strings or derive
  business meaning from messages or HTTP status alone.
- Response audience selection is server controlled. Client input cannot request INTERNAL details.
- Never log decrypted request/response bodies, complete ciphertext, authentication material, PAN,
  CVV, keys, or secrets.
- Channel callbacks use channel-specific signature/authentication, IP policy where supported,
  durable idempotency, raw-evidence retention policy, and legal state-transition checks. Do not
  apply the merchant request annotation blindly.

## Boundaries

- Controllers adapt HTTP only. Application services orchestrate the use case and internal client.
- Call the owning service through its declared client contract; do not persist payment or payout
  master records here.
- Preserve compatibility for published paths, headers, algorithms, encrypted `data`, fields,
  codes, and error semantics. Coordinate compatible SDK changes in the same work item.
- Local key providers and diagnostic modes are development aids, not production fallbacks.

## Required Verification

For a changed security or contract path, cover applicable cases: valid request, missing/invalid
authentication, decryption failure, replay, merchant/key mismatch, IP rejection, validation error,
encrypted success response, stable error mapping, and absence of sensitive log output.

Read `docs/api/openapi/openapi-security-flow.md` and `docs/api/openapi/openapi-versioning.md` when
changing the protocol or version behavior.
