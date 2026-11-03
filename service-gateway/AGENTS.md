# Service Gateway Rules

## Ownership

`service-gateway` owns ingress routing controls, trace/request identity, approved origin handling,
and gateway-level traffic policy. It does not own merchant authentication facts, payment state,
channel mapping, or business-result interpretation.

## P0 Rules

- Preserve trusted proxy, client IP, host, scheme, path, header, and trace semantics. Never trust a
  forwarded header merely because it is present.
- Route and filter ordering is a security boundary. Authentication, replay, decryption, and
  authorization owned by downstream services must not be bypassed by a route exception.
- CORS, redirect, origin, rate-limit, body-size, and timeout policy must fail closed in production.
- Do not log authorization headers, cookies, tokens, encrypted or decrypted bodies, PAN, CVV, or
  unredacted query parameters. Trace identifiers must not contain user-controlled secrets.
- A timeout or routing failure is a transport outcome, not a confirmed payment failure.

## Verification

Cover route matching, filter order, trusted/untrusted forwarding headers, trace propagation, CORS and
origin rejection, size/timeout handling, error mapping, and sensitive-log behavior. Verify affected
downstream contracts when a route or header changes.
