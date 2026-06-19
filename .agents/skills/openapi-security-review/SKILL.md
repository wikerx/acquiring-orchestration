---
name: openapi-security-review
description: 用于审查商户 OpenAPI 安全链路，重点检查 JWT 验签、请求体解密、响应加密、防重放、IP 白名单、敏感日志、商户接口是否绕过安全注解。
---

# OpenAPI Security Review Skill

## 使用场景

当任务涉及 service-openapi、商户接口、签名、加密、防重放、JWT、回调、商户通知、安全审查时使用本 Skill。

## 审查重点

1. 商户对外 API 是否使用 POST。
2. 商户对外 API 是否走 `/api/rest/{domain}/{version}` 路径。
3. 商户对外 API 是否使用 `@VerificationAndProcessing`。
4. 请求体是否加密。
5. 响应 data 是否加密。
6. 是否有 JWT 验签。
7. 是否有防重放机制。
8. 是否存在打印完整请求明文、JWT、密钥、私钥、卡号、CVV 的日志。
9. 渠道回调是否有独立的渠道签名校验、IP 白名单、幂等和原文保存。
10. 商户通知重试接口是否存在裸露风险。
11. `/internal/**` 接口是否有内部调用边界。

## 高风险问题

以下问题标记为 P0：

1. 绕过 `@VerificationAndProcessing` 新增商户对外接口。
2. 成功响应 data 未加密。
3. 请求体解密后完整打印日志。
4. JWT、防重放、商户身份校验被删除或绕过。
5. 渠道回调无签名、无 IP 白名单、无幂等。
6. `/internal/**` 可被公网直接访问且无鉴权。

## 输出要求

输出：

1. 安全问题；
2. 风险等级；
3. 涉及文件；
4. 建议修复方式；
5. 回归测试场景；
6. 不建议改动的内容。
