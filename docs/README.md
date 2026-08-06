# 支付收单平台文档索引

本文档索引以 `acquiring-orchestration` 后端仓库为主，同时记录与 `acquiring-frontend` 前端仓库协同相关的架构约束。

## 1. 架构设计

* [支付收单平台系统架构说明](architecture/system-architecture.md)：后端与前端双仓库边界、整体服务关系、OpenAPI 授权链路、退款/授权/回调目标链路、状态机目标和当前风险。
* [支付收单系统工程约束](architecture/payment-engineering.md)：资金安全、模块边界、状态机、幂等、金额币种、OpenAPI、渠道回调、内部接口和前端限制。
* [Hosted Checkout V1 数据库与状态机设计草案](architecture/hosted-checkout-v1-database-design.md)：自建收银台的会话表、token、支付尝试、事件、安全事件和 MPGS 3DS 状态机设计。
* [ShardingSphere SQL 兼容矩阵](architecture/shardingsphere-sql-compatibility-matrix.md)：动态 Mapper、分片键 SQL、事务、分页、治理和回归证据基线。
* [企业级重构路线图](architecture/refactor-roadmap.md)：后续分阶段重构顺序和重点。
* [操作日志后续规划](architecture/operation-log-roadmap.md)：操作日志链路的后续演进方向。

## 2. API 文档

### OpenAPI 商户对接

* [OpenAPI 商户接入文档](api/openapi/openapi-integration-guide.md)
* [OpenAPI 鉴权与加密流程](api/openapi/openapi-security-flow.md)
* [OpenAPI 版本升级规范](api/openapi/openapi-versioning.md)
* [Hosted Checkout V1 接口契约草案](api/openapi/hosted-checkout-api-contract.md)

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
* [ShardingSphere 发布、灰度与回滚手册](deployment/shardingsphere-rollout-rollback-runbook.md)
* [Redis 监控与告警处置手册](deployment/prometheus/redis-monitoring-runbook.md)

Nacos 配置样例位于 `docs/deployment/nacos/`，Prometheus 规则位于
`docs/deployment/prometheus/`。

## 5. Redis 专项治理

`redis/` 记录 `REDIS-GOVERNANCE-001` 的技术基线、全量扫描、目标设计和后续整改门禁：

* [Redis 技术版本与扫描基线](redis/01-redis-version-baseline.md)
* [Redis 当前使用清单](redis/02-redis-current-usage-inventory.md)
* [Redis 问题清单](redis/03-redis-problem-list.md)
* [Redis 风险评估](redis/04-redis-risk-assessment.md)
* [Redis 目标设计](redis/05-redis-target-design.md)
* [Redis 数据结构映射](redis/06-redis-data-type-mapping.md)
* [Redis Cache Catalog](redis/07-redis-cache-catalog.md)
* [Redis 分阶段整改计划](redis/08-redis-change-plan.md)
* [Redis 变更报告](redis/09-redis-change-report.md)
* [Redis 测试报告](redis/10-redis-test-report.md)
* [Redis 基础性能报告](redis/11-redis-performance-report.md)
* [Redis 故障演练报告](redis/12-redis-failure-drill-report.md)
* [Redis 专项治理验收报告](redis/13-redis-acceptance-report.md)

阶段 0～8 的代码治理和阶段 9 本地 Redis/Cluster 验证已完成。生产准入仍受真实拓扑、
完整 shadow 周期、监控、容量、故障演练、注释存量和完整回归门禁约束，详见验收报告。

## 6. SQL 脚本

* [ShardingSphere 测试分表退役草案](sql/shardingsphere-test-table-retirement-20260802.sql)
* [ShardingSphere 季度号段修复草案](sql/shardingsphere-auto-increment-repair-20260802.sql)

SQL 参考脚本统一放在 `docs/sql/`。

## 7. 历史归档

`archive/reports/` 用于保存一次性扫描报告、历史修复记录和问题分析文档：

* [数据库清理扫描报告](archive/reports/database-cleanup-scan-report.md)
* [菜单权限功能一致性扫描报告](archive/reports/menu-permission-function-consistency-report.md)
* [占位功能与导出实现报告](archive/reports/placeholder-function-and-export-implementation-report.md)
* [RBAC 角色授权树修复报告](archive/reports/rbac-permission-tree-fix-report.md)
* [sys_user 与 sys_account 字段职责分析](archive/reports/sys-user-account-field-analysis.md)
* [系统基础菜单审计报告](archive/reports/system-base-menu-audit.md)
* [系统管理与基础数据菜单修复报告](archive/reports/system-base-menu-repair-report.md)
* [系统菜单树层级修复报告](archive/reports/system-menu-tree-repair-report.md)
* [交易季度分表专项扫描与整改方案](archive/reports/transaction-sharding-scan-and-remediation-report.md)

## 8. 前端协作文档

前端仓库独立维护：

```text
/Users/scott/Documents/code/ideaCodex/acquiring-frontend
```

关键文档：

* `AGENTS.md`：前端仓库 AI 协作边界。
* `PAYMENT_RULES.md`：前端支付业务展示规则。
* `UI_DESIGN_RULES.md`：前端 UI 设计规则。
* `docs/architecture/refactor-roadmap.md`：前端架构收敛路线图。
