# Acquiring Orchestration

跨境收单与支付编排系统，用于沉淀多渠道路由、交易编排、风控协同、清结算对账、幂等与补偿等核心能力。

## 仓库状态

- 主分支：`master`
- 生产分支：`prod`
- 发布标签：`prod-yyyy-mm-dd`
- 远程仓库：`https://github.com/wikerx/acquiring-orchestration.git`
- 当前阶段：空仓库初始化与工程治理规范建设

## 文档导航

- [分支与发布管理规范](docs/git-branching.md)
- [代码编写规范](docs/coding-standard.md)
- [代码评审规范](docs/code-review.md)
- [跨境支付系统工程约束](docs/payment-engineering.md)

## 基本原则

1. 主干稳定，变更可追溯。
2. 代码遵循阿里巴巴 Java 开发手册风格，并结合支付系统的安全、幂等、审计和合规要求。
3. 所有业务变更必须经过 Pull Request、自动化检查和至少一名 Reviewer 审核。
4. 支付链路默认防重复、防篡改、防敏感信息泄露。
