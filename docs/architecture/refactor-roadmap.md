# 企业级重构路线图

## 1. 目标

在保持现有模块边界基本稳定的前提下，把当前工程逐步收敛为更适合企业长期演进的结构：

1. 后端统一采用 `interfaces -> application -> domain -> infrastructure` 的服务内分层；
2. `service-openapi` 收敛为接入层，`service-payment` / `service-payout` 收敛为交易核心域；
3. `component-library` 只保留基础技术能力，不继续沉淀业务语义；
4. 前端统一采用 `app-shell -> store -> api -> shared` 的分层方式，避免页面直接处理通用协议解包；
5. 前后端接口保持稳定，优先做结构性收敛，不先做大规模接口变更。

## 2. 当前问题

### 2.1 后端

1. `service-openapi` 已经具备 `application` 目录，但控制器仍直接依赖业务服务，应用层编排还没有真正承担职责。
2. `service-payment` 已经预留 `application`、`domain`、`infrastructure`，但内部请求入口仍直接调用 `service` 层，企业版分层样板还不够完整。
3. `service-openapi` 对 `db`、`redis`、`mq`、`http` 依赖较重，后续如果不控制职责，容易演变成接入层和交易层的混合体。
4. `service-payment` / `service-payout` 目录结构相似，后续若没有统一交易骨架，会出现重复实现。

### 2.2 前端

1. `admin-system` 与 `merchant-portal` 的请求层风格不一致。
2. `merchant-portal` 的页面和路由层仍在处理 `CommonResult` 解包，业务对象边界不够清晰。
3. `packages/shared` 已具备统一 HTTP 客户端能力，但应用侧尚未完全围绕共享层收口。
4. 根 README 的本地代理端口说明与实际配置有偏差，联调信息存在漂移风险。

## 3. 目标结构

### 3.1 后端服务内分层

```text
service-xxx
└── src/main/java/com/scott/payment/xxx
    ├── interfaces
    │   ├── api
    │   ├── dto
    │   └── converter
    ├── application
    │   ├── command
    │   ├── query
    │   └── service
    ├── domain
    │   ├── model
    │   ├── service
    │   ├── repository
    │   └── event
    └── infrastructure
        ├── persistence
        ├── rpc
        ├── mq
        └── config
```

### 3.2 前端应用分层

```text
apps/xxx/src
├── api
├── stores
├── router
├── layouts
├── pages or views
└── styles

packages/shared/src
├── auth
├── http
├── result
├── types
└── utils
```

## 4. 分阶段方案

### 第一阶段：结构样板收敛

1. 在 `service-openapi` 的支付链路上补齐应用层入口；
2. 在 `service-payment` 的内部授权链路上补齐应用层入口；
3. 统一 `merchant-portal` 的 API 层职责，由 API 层返回业务对象，页面不再直接处理 `CommonResult`；
4. 补充本路线图文档，作为后续重构基线。

### 第二阶段：服务内目录与命名统一

1. 将 `api/internal`、`service`、`entity`、`mapper` 等目录逐步向目标结构归位；
2. 把 `service-openapi` 中偏交易核心的逻辑继续向 `service-payment` / `service-payout` 回收；
3. 为 `service-payment` / `service-payout` 抽取统一交易命令、结果、事件骨架。

### 第三阶段：前端共享层升级

1. 将 `admin-system` 完全切换到 `packages/shared` 的统一请求封装；
2. 统一 `admin-system` / `merchant-portal` 的 Session 持久化和鉴权处理；
3. 为 `cashier` 预留收银会话、支付方式、下单提交的共享模型。

### 第四阶段：核心域沉淀

1. 为支付、代付、通知、路由、风控、对账、清结算建立清晰领域模型；
2. 在交易核心引入显式状态机、事件流和幂等骨架；
3. 明确 `component-library` 与业务域之间的硬边界。

## 5. 本次第一阶段落地内容

1. `service-openapi` 新增支付应用服务，控制器不再直接调用支付业务服务。
2. `service-payment` 新增授权应用服务，内部接口不再直接调用交易服务实现。
3. `merchant-portal` 统一由 API 层解包返回业务对象，路由和页面层只处理业务结果。
4. 第二阶段已继续把 `service-openapi` 的 `payout / iso` 链路补齐应用层样板，并把 `admin-system` 的认证 API 切到共享 HTTP 客户端。
5. 第三阶段已为 `service-payout` 建立最小内部应用骨架，并继续把 `admin-system` 的 `config / dict / country / oper-log` 模块迁移到共享 HTTP 客户端。
6. 第四阶段已补上 `service-openapi -> service-payout` 的内部客户端调用样板，并迁移 `admin-system` 的 `role / dept / menu / notice` 模块到共享 HTTP 客户端。
7. 第五阶段已将商户身份从 OpenAPI 请求上下文接入服务层，并继续迁移 `admin-system` 的 `post / user / login-log / currency / regionCurrency / monitor / merchant-info` 及 `crud` 帮助层。
8. 第六阶段已为 `service-merchant`、`service-admin` 认证入口补齐应用层编排样板，统一后台与商户侧的登录注册链路分层方式。
9. 第七阶段已继续将 `service-admin` 的用户、角色、菜单、字典、配置、日志、商户资料、基础资料与监控入口收敛到应用层，后台控制器仅保留接口职责。
10. 后续可继续在 `service-payout` 更完整交易链路和极少数仍依赖旧请求层的模块上复制同样的收敛方式。

## 6. 风险

1. 当前第一阶段只做结构性收敛，不会一次性迁移所有目录命名，因此会存在新旧结构并行一段时间。
2. `service-openapi` 与 `service-payment` 的真实领域模型尚未建立，本阶段仍以样板分层为主。
3. 前端 `admin-system` 仍保留旧请求封装，本阶段先统一 `merchant-portal`，避免一次性改动过大。
