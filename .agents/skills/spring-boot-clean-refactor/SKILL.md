---
name: spring-boot-clean-refactor
description: 用于 Spring Boot 后端小步重构，重点检查 Controller 瘦身、ApplicationService 编排、Service 规则下沉、Mapper 只做数据访问、构造器注入、异常和返回规范。
---

# Spring Boot Clean Refactor Skill

## 使用场景

当任务涉及 Spring Boot Controller、ApplicationService、Service、Mapper、DTO、VO、DO、异常处理、返回模型、包结构重构时使用本 Skill。

## 分层规则

1. Controller 只负责参数接收、基础校验、权限控制、调用应用服务、返回结果。
2. ApplicationService 负责业务编排。
3. Service 负责核心业务规则。
4. Mapper / Repository 只负责数据访问。
5. Converter 负责对象转换。
6. DO 不直接作为外部接口入参或出参。
7. 不在 Controller 中写事务逻辑、数据库组合查询逻辑、复杂业务判断。
8. 不在 Mapper 中写业务判断。
9. 不新增无意义 Manager、Helper、Processor、Util。

## 重构原则

1. 小步修改。
2. 不一次性搬包。
3. 不一次性重命名大量类。
4. 不修改接口契约。
5. 不修改数据库字段含义。
6. 不引入新依赖。
7. 修改后说明风险和测试场景。

## 输出要求

输出：

1. 当前分层问题；
2. 建议重构步骤；
3. 本次可改范围；
4. 本次不建议改范围；
5. 验证方式。
