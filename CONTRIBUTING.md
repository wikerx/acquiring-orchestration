# 贡献指南

## 开发流程

1. 从 `master` 拉取最新代码。
2. 基于任务类型创建 `feature/*`、`bugfix/*`、`release/*` 或 `hotfix/*` 分支。
3. 本地完成开发、测试和自查。
4. 提交 Pull Request 到 `master`。
5. 通过自动化检查和 Reviewer 审核后合并。

## 本地自查

提交 PR 前请确认：

- 命名、分层、异常、日志符合 [代码编写规范](docs/coding-standard.md)。
- 分支、提交和合并方式符合 [分支与发布管理规范](docs/git-branching.md)。
- PR 描述、审核关注点符合 [代码评审规范](docs/code-review.md)。
- 支付链路满足 [跨境支付系统工程约束](docs/payment-engineering.md)。

## 支付系统红线

- 禁止提交密钥、证书、真实卡号、真实账户、生产日志。
- 禁止使用浮点数处理金额。
- 禁止绕过幂等、验签、状态机校验。
- 禁止在日志中输出完整敏感信息。
- 资金、安全、合规、数据库核心表变更必须提前设计评审。
