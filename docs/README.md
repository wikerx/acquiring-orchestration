# 后端项目文档索引

## 1. 架构设计

* [系统架构设计说明](architecture/system-architecture.md)：整体模块划分、服务职责、基础设施和演进方向。
* [跨境支付系统工程约束](architecture/payment-engineering.md)：支付系统工程实践、模块边界和安全约束。
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

## 4. 部署配置

* [Nacos 配置说明](deployment/nacos/README.md)

Nacos 配置样例位于 `docs/deployment/nacos/`。

## 5. SQL 脚本

* [基础 ISO 字典建表脚本](sql/base-iso-dictionary-schema.sql)

SQL 参考脚本统一放在 `docs/sql/`。

## 6. 历史归档

`archive/reports/` 用于保存一次性扫描报告、历史修复记录和问题分析文档：

* [数据库清理扫描报告](archive/reports/database-cleanup-scan-report.md)
* [RBAC 角色授权树修复报告](archive/reports/rbac-permission-tree-fix-report.md)
* [sys_user 与 sys_account 字段职责分析](archive/reports/sys-user-account-field-analysis.md)
* [系统管理与基础数据菜单修复报告](archive/reports/system-base-menu-repair-report.md)
* [系统菜单树层级修复报告](archive/reports/system-menu-tree-repair-report.md)
