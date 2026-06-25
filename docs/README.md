# 支付收单平台文档索引

本文档索引以 `acquiring-orchestration` 后端仓库为主，同时记录与 `acquiring-frontend` 前端仓库协同相关的架构约束。

## 1. 架构设计

* [支付收单平台系统架构说明](architecture/system-architecture.md)：后端与前端双仓库边界、整体服务关系、OpenAPI 授权链路、退款/授权/回调目标链路、状态机目标和当前风险。
* [支付收单系统工程约束](architecture/payment-engineering.md)：资金安全、模块边界、状态机、幂等、金额币种、OpenAPI、渠道回调、内部接口和前端限制。
* [企业级重构路线图](architecture/refactor-roadmap.md)：后续分阶段重构顺序和重点。
* [操作日志后续规划](architecture/operation-log-roadmap.md)：操作日志链路的后续演进方向。

## 2. API 文档

### OpenAPI 商户对接

* [OpenAPI 商户接入文档](api/openapi/openapi-integration-guide.md)
* [OpenAPI 鉴权与加密流程](api/openapi/openapi-security-flow.md)
* [OpenAPI 版本升级规范](api/openapi/openapi-versioning.md)

### 内部接口

* [内部管理系统接口文档](api/internal/internal-admin-api.md)
* [登录注册与权限接口文档](api/internal/internal-auth-api.md)

## 3. 开发规范

* [代码编写规范](standards/coding-standard.md)
* [代码评审规范](standards/code-review.md)
* [分支与发布管理规范](standards/git-workflow.md)
* [管理后台开发规范](standards/admin-development-standard.md)
* [公共组件使用说明](standards/component-usage.md)
* [全系统统一唯一标识生成规范](standards/global-id-generation.md)

## 4. 部署配置

* [Nacos 配置说明](deployment/nacos/README.md)

Nacos 配置样例位于 `docs/deployment/nacos/`。

## 5. SQL 脚本

* [基础 ISO 字典建表脚本](sql/base-iso-dictionary-schema.sql)

SQL 参考脚本统一放在 `docs/sql/`。

## 6. 历史归档

`archive/reports/` 用于保存一次性扫描报告、历史修复记录和问题分析文档：

* [数据库清理扫描报告](archive/reports/database-cleanup-scan-report.md)
* [菜单权限功能一致性扫描报告](archive/reports/menu-permission-function-consistency-report.md)
* [占位功能与导出实现报告](archive/reports/placeholder-function-and-export-implementation-report.md)
* [RBAC 角色授权树修复报告](archive/reports/rbac-permission-tree-fix-report.md)
* [sys_user 与 sys_account 字段职责分析](archive/reports/sys-user-account-field-analysis.md)
* [系统基础菜单审计报告](archive/reports/system-base-menu-audit.md)
* [系统管理与基础数据菜单修复报告](archive/reports/system-base-menu-repair-report.md)
* [系统菜单树层级修复报告](archive/reports/system-menu-tree-repair-report.md)

## 7. 前端协作文档

前端仓库独立维护：

```text
/Users/scott/Documents/code/ideaCodex/acquiring-frontend
```

关键文档：

* `AGENTS.md`：前端仓库 AI 协作边界。
* `PAYMENT_RULES.md`：前端支付业务展示规则。
* `UI_DESIGN_RULES.md`：前端 UI 设计规则。
* `docs/architecture/refactor-roadmap.md`：前端架构收敛路线图。
