---
name: codex-execution-governance
description: Use for every acquiring-orchestration coding task to enforce a confirmation-first workflow: analyze first, present an execution plan table with priority and recommended skills, wait for user approval before editing, summarize changes, wait for approval before full regression testing, force frontend-design for frontend work, follow payment backend constraints, and optimize for accuracy, professionalism, speed, and token efficiency.
---

# Codex Execution Governance

## 1. Purpose

This skill governs how Codex should execute coding tasks in the `acquiring-orchestration` payment platform project.

Use this skill when the user asks Codex to:

- modify code;
- fix a bug;
- implement a feature;
- refactor code;
- change frontend UI;
- change database / SQL / MyBatis;
- change OpenAPI / callback / security logic;
- change payment, settlement, reconciliation, risk, FX, VA, or merchant configuration logic;
- run tests or regression checks;
- analyze a PRD and prepare implementation.

The goal is to make Codex accurate, professional, fast, token-efficient, and safe for a payment backend project.

---

## 2. Core Rule

Do not directly modify code after receiving a task.

Before editing files, Codex must:

1. analyze the requirement and affected area;
2. identify recommended skills to use;
3. present an execution plan table;
4. wait for explicit user approval.

Only after the user confirms execution may Codex modify code.

Accepted approval phrases include:

- `确认执行`
- `开始执行`
- `按方案执行`
- `执行全部`
- `只执行第 X 项`
- `approve`
- `go ahead`

If the user only asks for analysis, do not modify code.

---

## 3. Mandatory Workflow

### Phase 1: Pre-execution analysis

Before changing code, output a concise but complete plan.

Required output format:

```markdown
## 执行前分析

### 1. 任务理解
- ...

### 2. 建议使用的 Skill
| Skill | 使用原因 |
|---|---|
| ... | ... |

### 3. 准备执行的内容
| 优先级 | 执行项 | 涉及范围 / 文件 | 准备怎么做 | 风险等级 | 验证方式 |
|---|---|---|---|---|---|
| P0 | ... | ... | ... | 高/中/低 | ... |

### 4. 暂不处理的内容
- ...

### 5. 需要你确认
请确认是否执行：
A. 全部执行  
B. 只执行指定项  
C. 先调整方案  
```

Rules:

- Keep the plan focused and token-efficient.
- Use tables when listing execution items.
- Do not list more than 8 execution items unless the task is explicitly large.
- Mark priorities as `P0`, `P1`, `P2`.
- If the task involves frontend UI, the plan must include `$frontend-design`.
- If frontend behavior needs verification, include `$playwright`.
- If code will be changed, include `$verification-before-completion`.
- If a full regression plan is needed, include `$test-regression-plan`.

---

### Phase 2: Wait for confirmation

After outputting the plan, stop.

Do not modify files, run risky commands, or perform implementation until the user confirms.

Reading files, searching code, and inspecting context are allowed during analysis.

---

### Phase 3: Execute approved scope

After confirmation:

1. Execute only the approved scope.
2. Make small, reviewable changes.
3. Prefer minimal-impact changes.
4. Follow existing code style, naming, enums, exceptions, response wrappers, DTO/VO patterns, and module conventions.
5. Avoid broad rewrites unless explicitly approved.
6. Keep the user updated if execution becomes materially different from the approved plan.

If new risks are discovered during execution, stop and ask for confirmation before expanding scope.

---

### Phase 4: Post-change summary

After implementation, before full regression testing, output:

```markdown
## 本次修改说明

### 1. 已修改内容
| 模块 | 文件 | 修改内容 | 原因 |
|---|---|---|---|
| ... | ... | ... | ... |

### 2. 使用的 Skill
| Skill | 用途 |
|---|---|
| ... | ... |

### 3. 已执行的基础验证
| 命令 / 检查 | 结果 | 说明 |
|---|---|---|
| ... | 通过/失败/未执行 | ... |

### 4. 可能影响范围
| 范围 | 影响说明 | 风险等级 |
|---|---|---|
| ... | ... | 高/中/低 |

### 5. 是否执行完整回归测试
请确认是否执行完整回归测试：
A. 执行完整回归  
B. 只执行指定范围回归  
C. 暂不回归  
```

Then wait for user confirmation before full regression testing.

---

### Phase 5: Full regression testing after confirmation

Only after the user confirms regression testing, execute a full regression plan.

Regression output format:

```markdown
## 完整回归测试结果

### 1. 回归范围
| 范围 | 测试内容 | 原因 |
|---|---|---|
| ... | ... | ... |

### 2. 执行结果
| 测试项 | 命令 / 操作 | 结果 | 说明 |
|---|---|---|---|
| ... | ... | 通过/失败/未执行 | ... |

### 3. 发现的问题
| 问题 | 影响 | 建议 |
|---|---|---|
| ... | ... | ... |

### 4. 最终结论
- ...
```

If a test cannot be run, explicitly state why.

Never claim “completed”, “fixed”, or “verified” without fresh verification evidence.

---

## 4. Skill Selection Rules

### 4.1 Always consider this skill first

This skill is the governance skill. It controls the workflow.

When a task also needs another skill, recommend and use the other skill together with this skill.

---

### 4.2 Frontend tasks

If the task touches frontend UI, page layout, style, interaction, visual design, header, footer, logo, icons, forms, tables, dashboards, checkout pages, admin system, or merchant system:

Must use:

```text
$frontend-design
```

If browser verification is possible, also use:

```text
$playwright
```

Always include:

```text
$verification-before-completion
```

Recommended frontend combination:

```text
use $codex-execution-governance, $frontend-design, $playwright, $verification-before-completion.
```

---

### 4.3 Backend Spring Boot tasks

For backend changes in `acquiring-orchestration`, prefer project-specific skills first.

Recommended:

```text
$spring-boot-clean-refactor
$payment-backend-review
$verification-before-completion
```

Use the external Spring Boot skill only as a supplement:

```text
$spring-boot-skill
```

Recommended backend combination:

```text
use $codex-execution-governance, $payment-backend-review, $spring-boot-clean-refactor, $tdd, $verification-before-completion.
```

---

### 4.4 OpenAPI / callback / security tasks

Use:

```text
$openapi-security-review
$idempotency-state-machine-review
$verification-before-completion
```

Recommended combination:

```text
use $codex-execution-governance, $openapi-security-review, $idempotency-state-machine-review, $tdd, $verification-before-completion.
```

---

### 4.5 Payment state machine / callback / MQ tasks

Use:

```text
$idempotency-state-machine-review
$diagnosing-bugs
$tdd
$verification-before-completion
```

---

### 4.6 Amount / currency / FX / settlement tasks

Use:

```text
$amount-currency-rate-review
$tdd
$verification-before-completion
```

---

### 4.7 SQL / MyBatis / database tasks

Use:

```text
$sql-mybatis-review
$verification-before-completion
```

If the change affects payment amounts, idempotency, settlement, or sharding, also use the corresponding project skill.

---

### 4.8 Large refactor / architecture analysis

Use:

```text
$improve-codebase-architecture
$payment-backend-review
$spring-boot-clean-refactor
```

Do not perform broad refactoring until the user confirms.

---

### 4.9 Regression planning

Use:

```text
$test-regression-plan
```

After code modification, propose regression scope and wait for user confirmation before full regression.

---

## 5. Payment Platform Backend Constraints

Follow these rules strictly.

### 5.1 General code constraints

- Prefer minimal code changes.
- Do not introduce unnecessary frameworks.
- Do not rewrite large modules unless explicitly approved.
- Reuse existing naming, enum, exception, response wrapper, DTO, VO, entity, mapper, and service conventions.
- Keep Controller thin.
- Put business rules in Service / Application layer.
- Do not put business logic in Mapper.
- Do not introduce technical fields into PRD-like outputs unless necessary.
- When code comments are needed, keep them professional and explain business intent, not obvious implementation details.

---

### 5.2 Time constraints

- Database time fields should use `datetime(3)` when schema changes are involved.
- Management system pages must not display milliseconds.
- When sharding is involved, ensure `transaction_date_time` is provided and handled correctly.
- Use clear timezone assumptions when relevant.

---

### 5.3 Payment and settlement constraints

When touching payment backend logic, always consider:

- idempotency;
- state machine;
- terminal state immutability;
- duplicate callback handling;
- duplicate MQ consumption;
- payment / refund / chargeback / reversal / settlement status transitions;
- amount precision;
- currency conversion direction;
- FX rate source and adjustment;
- fee duplication risk;
- reconciliation and settlement impact.

---

### 5.4 OpenAPI security constraints

When touching merchant OpenAPI or callback logic, always consider:

- JWT / HMAC-SHA256;
- MD5 v1 compatibility if relevant;
- request / response `data` encryption;
- AES-256-GCM;
- RSA-OAEP-256;
- timestamp / nonce anti-replay;
- merchant ID and key binding;
- IP whitelist;
- callback signature;
- callback encryption;
- callback idempotency;
- terminal-state-only notification.

---

### 5.5 Frontend constraints

When touching frontend:

- Must use `$frontend-design`.
- Preserve existing business functions and APIs.
- Do not remove page entries without explicit approval.
- Follow existing i18n conventions.
- Keep admin / merchant / checkout visual style consistent.
- Avoid obvious template-like AI UI.
- Avoid icon blocks with obvious background color unless intentionally designed.
- Keep footer height controlled and visually aligned with header.
- Long side menus must remain scrollable and accessible.
- Validate with `$playwright` if a browser flow can be run.

---

## 6. Token and Speed Efficiency Rules

- Do not reprint large files unless necessary.
- Prefer concise tables over long prose.
- Inspect only relevant files.
- Avoid broad repository scans unless required.
- Use project-level skills before external generic skills.
- Use tests targeted to the changed scope first.
- Ask for confirmation only at the two mandated checkpoints:
  1. before code modification;
  2. before full regression testing.
- Do not ask unnecessary clarifying questions if the task can be reasonably scoped.
- If a requirement is ambiguous but low risk, state the assumption and proceed to the approval plan.

---

## 7. Final Reporting Rules

Final answer after full execution should include:

```markdown
## 最终结果

### 1. 处理结论
- ...

### 2. 修改文件
| 文件 | 修改说明 |
|---|---|
| ... | ... |

### 3. 使用的 Skill
| Skill | 用途 |
|---|---|
| ... | ... |

### 4. 验证结果
| 验证项 | 命令 / 操作 | 结果 |
|---|---|---|
| ... | ... | ... |

### 5. 影响范围
| 范围 | 说明 | 风险 |
|---|---|---|
| ... | ... | ... |

### 6. 剩余风险 / 后续建议
- ...
```

Be direct, accurate, professional, and concise.
