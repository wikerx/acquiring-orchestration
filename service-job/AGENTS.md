# Service Job Rules

## Ownership

`service-job` owns job definitions, scheduling controls, execution records, leases, and dispatch. A
job coordinates an owner service; it does not take ownership of that service's business state.

## P0 Rules

- Treat scheduler triggers, retries, failover, and manual execution as at least once. Every
  side-effecting job defines a durable idempotency key or delegates to an idempotent owner command.
- Use bounded leases with explicit owner identity, expiry, heartbeat/renewal, and stale-owner
  recovery. A process-local flag is not distributed exclusion.
- Job success means the owned command completed according to its contract, not merely that dispatch
  returned or a message was accepted.
- Preserve original business time and shard/routing identity. Scheduler wall-clock time is execution
  metadata and must not replace source facts.
- Job parameters, logs, and execution records must exclude credentials, tokens, PAN, CVV, decrypted
  payloads, and unredacted account or identity data.

## Verification

Cover duplicate triggers, concurrent workers, lease expiry/takeover, retry exhaustion, partial batch
failure, timeout/unknown result, disabled jobs, manual authorization, audit history, and restart
recovery. Verify the owner service's duplicate and stale-state behavior for changed dispatches.
