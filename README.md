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
- [Spring Cloud 支付系统架构设计](docs/architecture/architecture-design.md)
- [OpenAPI 授权认证规范](docs/api/openapi-authentication.md)

## 工程结构

```text
component-library
├── component-core
├── component-web
├── component-security
├── component-db
├── component-redis
├── component-mq
└── component-job

channel-library
├── payment-channel-library
└── payout-channel-library

service-gateway
service-admin
service-merchant
service-checkout
service-openapi
service-payment
service-payout
service-job
```

## 技术基线

- Java：8
- Spring Boot：2.7.x
- Spring Cloud：2021.x
- Spring Cloud Alibaba：2021.x

说明：Spring Boot 3.x 要求 Java 17 及以上；当前骨架优先满足 Java 8，因此采用 Spring Boot 2.7.x 兼容组合。

## 环境与打包

根 `pom.xml` 统一维护 `dev`、`test`、`uat`、`prod` 四套 Maven profile。默认环境为 `dev`。

```bash
mvn -Pdev clean package
mvn -Ptest clean package
mvn -Puat clean package
mvn -Pprod clean package
```

打包时会通过资源过滤把 `@profiles.active@` 写入各服务的 `application.yml`：

```yaml
spring:
  profiles:
    active: @profiles.active@
```

各服务的环境配置文件保持一致：

```text
application.yml
application-dev.yml
application-test.yml
application-uat.yml
application-prod.yml
application-sample.yml
banner.txt
seata.conf
log-config/logback-spring.xml
```

Redis、RocketMQ、数据库、分表、Seata、XXL-JOB 等基础设施配置统一放到 Nacos Config，DataId 规范见 [Nacos 配置拆分规范](docs/deployment/nacos/README.md)。

## 开放 API 边界

商户侧收单、代付、查询、退款等开放接口统一进入 `service-openapi`：

```text
merchant/client -> service-gateway -> service-openapi -> service-payment/service-payout
```

`service-openapi` 负责 JWT 授权验签、报文解密、商户基础参数校验、产品权限校验、幂等、商户通知和渠道侧回调入口。

对外收单接口使用版本路径控制，例如 `POST /api/rest/co/v2/authorization`；控制器通过 `@ApiVersion(apiVersion = 2)` 与 `{version}` 路径变量匹配。

渠道适配不作为独立微服务部署：

```text
service-payment -> payment-channel-library
service-payout  -> payout-channel-library
```

`payment-channel-library` 聚合收单支付渠道适配器，`payout-channel-library` 聚合代付渠道适配器。渠道侧回调统一进入 `service-openapi` 后，再分发到 `service-payment` 或 `service-payout` 做交易状态处理。

## 基本原则

1. 主干稳定，变更可追溯。
2. 代码遵循阿里巴巴 Java 开发手册风格，并结合支付系统的安全、幂等、审计和合规要求。
3. 所有业务变更必须经过 Pull Request、自动化检查和至少一名 Reviewer 审核。
4. 支付链路默认防重复、防篡改、防敏感信息泄露。
