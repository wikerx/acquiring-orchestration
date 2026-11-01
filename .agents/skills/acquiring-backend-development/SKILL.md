---
name: acquiring-backend-development
description: Implement, refactor, review, or verify Java backend, SQL, configuration, messaging, payment, payout, clearing, settlement, risk, channel, and OpenAPI changes in the acquiring-orchestration repository. Use for backend work in this repository; do not use for the frontend or merchant Java SDK repositories.
---

# Acquiring Backend Development

Use the repository's rules as the decision boundary; this Skill defines the execution workflow.

## Start With Scope

1. Read the root and closest directory-level `AGENTS.md`.
2. Identify the owning module, upstream callers, downstream consumers, persistent facts, public
   contracts, and relevant tests.
3. Classify requirements as `P0/P1/P2` and the change as `R0/R1/R2/R3`.
4. Read [risk and verification](references/risk-and-verification.md) for `R2/R3`, or when the task
   touches payment correctness, OpenAPI security, SQL, messages, or financial calculations.

## Confirmation Gate

Before editing, present:

- the requirement and assumptions;
- affected modules and files;
- source-of-truth evidence;
- protected invariants and compatibility concerns;
- proposed tests and rollback/containment for `R3`.

Wait for explicit approval. Approval covers only the described scope. Stop and re-plan if a new
contract, migration, security boundary, or cross-module owner becomes necessary.

## Implementation Rules

- Preserve unrelated and pre-existing changes in a dirty worktree.
- Prefer the smallest coherent change and the repository's existing abstractions.
- Change the owning layer; do not duplicate a fact or rule in a convenient caller.
- Do not invent API fields, statuses, codes, database meanings, channel outcomes, or configuration.
- Add behavior tests with the change. A regression test must exercise the failure or invariant, not
  merely match generated text.
- Treat new/touched-code rules separately from legacy debt. Do not expand the task into cleanup.

## Conditional Review

Apply the relevant checklist in the reference:

- merchant OpenAPI, callbacks, encryption, authentication, error mapping;
- payment/payout idempotency and state transitions;
- amount, currency, fee, reserve, clearing, rate, settlement;
- SQL/MyBatis, indexes, conditional updates, migration compatibility;
- channel execution, timeout/unknown result, retries and status mapping;
- messages, inbox/outbox, schedulers and duplicate delivery.

Existing specialized review Skills may supplement this workflow, but they do not replace the closest
`AGENTS.md` or the source-of-truth order.

## Verification And Handoff

Run fresh, targeted checks first. Report exact commands, exit codes, skipped checks, failures, and
remaining risk. Do not claim a module, test, or build passes without current command output.

After implementation, summarize changed files, behavior, compatibility impact, evidence, and any
accepted exception. Ask before a broad environment-dependent regression run.
