# AGENTS.md

# Payment Acquiring System Coding Rules

## Project Overview

This project is a payment acquiring platform.

Technology stack:

* Java 17
* Spring Boot 3.x
* Spring Cloud
* MyBatis Plus
* MySQL 8.x
* Redis 6.x
* RocketMQ 5.x
* Nacos 2.x

Architecture style:

* DDD
* Microservices
* Domain Driven Design
* Clean Architecture

---

## Primary Goal

Always prioritize:

1. Correctness
2. Maintainability
3. Simplicity
4. Readability
5. Security

Never sacrifice readability for clever code.

---

## Token Optimization Rules

Before coding:

* Read only required files.
* Never analyze the entire repository unless explicitly requested.
* Never scan unrelated modules.
* Never rewrite large files when a small patch is sufficient.
* Minimize code changes.
* Reuse existing code whenever possible.

Output:

* Summary
* Changed files
* Risks

Keep explanations concise.

---

## Architecture Rules

Strictly follow module boundaries.

Allowed dependencies:

Domain
↓
Application
↓
Infrastructure

Forbidden:

Infrastructure
↓
Domain

Do not bypass layers.

Do not introduce cyclic dependencies.

---

## Package Structure

Use:

com.scott.xxx

Example:

com.scott.payment
com.scott.risk
com.scott.clearing
com.scott.settlement
com.scott.gateway
com.scott.channel

---

## Coding Style

Follow Alibaba Java Development Manual.

### Naming

Use meaningful names.

Avoid:

tmp
data
obj
test

Prefer:

paymentOrder
merchantInfo
riskResult

---

### Constants

No magic values.

Use constants.

Bad:

if (status == 1)

Good:

if (status == PaymentStatus.SUCCESS)

---

### Method Rules

Keep methods small.

Target:

* < 80 lines

Prefer:

* Early return
* Guard clause

Avoid:

* Deep nesting

Maximum nesting:

3 levels

---

### Constructor Injection

Always use constructor injection.

Do not use field injection.

Bad:

@Autowired
private OrderService orderService;

Good:

private final OrderService orderService;

public PaymentService(OrderService orderService) {
this.orderService = orderService;
}

---

### Lombok

Allowed:

* @Getter
* @Setter
* @Data
* @Builder
* @RequiredArgsConstructor

Avoid excessive Lombok usage.

---

## Money Rules

Never use:

* double
* float

Always use:

BigDecimal

Example:

BigDecimal amount;

Database:

DECIMAL(18,6)

---

## Time Rules

Use:

* LocalDateTime
* Instant

Avoid:

* java.util.Date

Database:

DATETIME(3)

---

## API Rules

Separate:

* Request DTO
* Response DTO
* Entity

Never expose Entity directly.

Use:

xxxRequest
xxxResponse

Example:

CreateOrderRequest
CreateOrderResponse

---

## Validation Rules

Use Bean Validation.

Example:

@NotBlank
@NotNull
@Size
@Min
@Max

Never trust client input.

---

## Security Rules

Never hardcode:

* password
* secret
* token
* private key

Never log:

* card number
* CVV
* password
* API secret
* JWT
* token

Mask sensitive data.

---

## Payment API Rules

Merchant authentication:

JWT HS256

Merchant JWT claims must include:

aud
iss
jti
iat
exp
merchantId

JWT expiration must be <= 180 seconds.

Request body encryption:

RSA-OAEP-256 + AES-256-GCM

Legacy AppId/Timestamp/Nonce/HMAC-SHA256 is only for backward-compatible parameter signing when explicitly requested.

---

## Idempotency Rules

All payment operations must support idempotency.

Examples:

Create Payment
Refund
Capture
Payout

Must support:

Idempotency-Key

---

## Database Rules

MySQL 8 compatible SQL only.

Database:

utf8mb4

Collation:

utf8mb4_0900_ai_ci

Table naming:

snake_case

Column naming:

snake_case

Primary key:

BIGINT

Required columns:

created_at
updated_at
deleted

---

## MyBatis Plus Rules

Prefer:

LambdaQueryWrapper

Avoid raw SQL unless necessary.

Use pagination plugin.

Prevent full table updates.

Prevent full table deletes.

---

## Redis Rules

All Redis keys must have prefix.

Example:

payment:order:
risk:blacklist:
merchant:info:

Always set TTL when appropriate.

---

## RocketMQ Rules

Messages must be:

* Idempotent
* Retry-safe

Consumers must handle duplicate delivery.

Never assume exactly-once delivery.

---

## Logging Rules

Always include:

* traceId
* requestId
* orderNo

Log levels:

INFO:
Business flow

WARN:
Recoverable exception

ERROR:
System failure

---

## Exception Rules

Do not catch Exception unless necessary.

Use business exceptions.

Return stable error codes.

Never expose stack trace to clients.

---

## Testing Rules

Add tests for:

* Money calculation
* Signature verification
* Encryption
* Idempotency
* Risk rules

Avoid external dependencies in unit tests.

---

## Dependency Rules

Do not introduce new dependencies unless necessary.

Before adding dependency:

1. Check existing project.
2. Check Spring ecosystem.
3. Check JDK built-in capability.

Prefer fewer dependencies.

---

## Refactoring Rules

Do not refactor unrelated code.

Do not rename modules.

Do not change public APIs without request.

Prefer incremental improvement.

---

## Response Rules

Before coding:

List:

1. Files to modify
2. Reason
3. Expected impact

After coding:

List:

1. Changed files
2. Summary
3. Risks

Keep output concise.
