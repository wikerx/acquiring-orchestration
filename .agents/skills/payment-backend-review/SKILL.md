---
name: payment-backend-review
description: 用于审查跨境收单支付系统后端代码，重点检查模块边界、分层、支付核心链路、Controller 是否过重、DTO/VO/DO 是否混用、公共模块是否变成业务垃圾桶。
---

# Payment Backend Review Skill

## 使用场景

当任务涉及后端代码审查、模块重构、包结构调整、支付系统工程质量评估时使用本 Skill。

## 审查重点

1. 检查是否违反模块边界。
2. 检查 Controller 是否只做参数接收、权限校验、调用应用服务和返回结果。
3. 检查 ApplicationService 是否承担业务编排职责。
4. 检查 Service 是否承担核心业务规则。
5. 检查 Mapper / Repository 是否只做数据访问。
6. 检查 DTO、VO、DO、Entity、Request、Response 是否混用。
7. 检查是否把具体业务逻辑放入 component-library。
8. 检查是否在 channel-library 中写平台交易状态机。
9. 检查是否新增无意义工具类、Manager、Helper、Processor。
10. 检查是否生成模板化注释和临时 Markdown 报告。

## 输出要求

输出分为：

1. 问题清单；
2. 风险等级：P0 / P1 / P2 / P3；
3. 建议修改范围；
4. 不建议修改的范围；
5. 验证方式。

## 禁止行为

1. 不要直接大范围重构。
2. 不要一次性搬迁所有包。
3. 不要修改接口字段、路径、签名、加密规则。
4. 不要删除看似无用但可能被框架扫描的代码。
