# 汇率波动与预警功能需求及技术方案

> 分析日期：2026-11-02  
> 适用范围：`acquiring-orchestration`、`acquiring-frontend/apps/admin-system`  
> 当前状态：首期功能已实现并完成本机开发库迁移、Nacos 配置和真实调度链路验证；订阅式站内通知与邮件通知保留为后续能力

## 1. 需求概述

管理系统需要在“汇率管理”下提供交易汇率和结算汇率的历史波动视图，让有权限的运营、财务和风险人员能够按汇率类型、币种对和时间范围查看趋势；系统还必须在同一汇率类型和币种对的滚动 72 小时波动超过 3% 时生成可审计的预警事件。BOC、NBP、MANUAL 等来源不再拆成独立预警维度，来源切换造成的业务汇率变化同样必须进入波动计算，防止汇损。

预警事件本身属于必须能力，通知渠道分阶段建设：第一阶段保证管理端可见、可确认、可追踪；后续再增加按授权人员投递的站内通知和可配置邮件，不向所有后台用户无差别广播。

## 2. 现状结论

### 2.1 可直接复用的能力

1. 业务汇率事实已保存在 `exchange_business_rate`，包含 `rate_type`、`source_code`、币种对、`final_rate`、生效时间和失效时间，能够支持历史趋势查询。
2. 汇率使用事实已保存在 `exchange_rate_usage_snapshot`，适合作为“该汇率被哪些交易或结算实际使用”的下钻依据。
3. 管理端已使用 ECharts，并提供 `@acquiring/shared/AnalyticsChart` 统一处理图表生命周期、响应式尺寸、加载态、空态、ARIA 和点击事件，无需引入新的图表依赖。
4. 系统已有后端驱动的菜单、路由、权限、操作日志和中英文 i18n 机制，可以沿用现有汇率管理菜单的初始化方式。
5. 系统已有可靠邮件账户、模板、发送记录及异步投递链路，后续邮件预警应复用 `AdminEmailService`，不另建 SMTP 实现。
6. `service-job` 已有分布式任务、租约、执行日志、人工触发和管理端任务调度页面，适合注册独立汇率波动评估任务；任务定义、Cron、启停和运行日志由现有调度中心统一管理，预警业务状态仍由 `service-admin` 持有。

### 2.2 不应直接复用的能力

1. `exchange_rate_usage_snapshot` 不能作为波动图的主数据源。它受交易流量影响，同一汇率可能重复出现，没有交易的汇率又会缺失，无法表示完整市场或业务汇率变化。
2. `sys_notice` 是全局通知公告表，没有接收人、逐用户已读状态、确认状态和投递日志，不能直接充当汇率预警站内信。
3. 不按 BOC、NBP、MANUAL 等来源拆分曲线或预警。不同来源生成的同一类业务汇率最终服务于相同交易或结算场景，来源切换产生的跳变属于需要监控的真实汇损风险；`source_code` 仅作为数据点和预警证据保留。
4. 前端 JavaScript 数值只能用于图表坐标展示，不能承担 3% 阈值判断或任何资金汇率决策。

## 3. 功能范围与优先级

### 3.1 P0 必须实现

- [x] 新增“汇率波动”菜单，分别查看 `TRANSACTION_RATE` 和 `SETTLEMENT_RATE`。
- [x] 支持按币种对和时间范围查看 `final_rate` 趋势，同一汇率类型内汇总全部来源。
- [x] 展示每个维度的最新汇率、72 小时最低值、最高值、波动率、样本数、最后更新时间和预警状态。
- [x] 后端按滚动 72 小时窗口计算波动率，超过 3% 时生成预警事件。
- [x] 新增“汇率预警事件”菜单，支持查询、详情、确认、恢复/关闭审计和导出。
- [x] 同一规则和汇率维度在持续超阈期间只保留一个活动事件，禁止每次调度重复告警。
- [x] 菜单、查询、详情、新增、修改、删除、启停、确认、导入、导出等真实功能按页面能力细分权限，并完整接入 API 鉴权、按钮权限、操作日志和中英文 i18n。
- [x] 在 `service-job` 注册独立汇率波动评估任务，通过现有任务调度页面统一维护 Cron、启停、手工执行和运行日志。
- [x] 增加必要 SQL 索引、迁移前检查、迁移脚本、迁移后检查和回滚/止损脚本。

### 3.2 P1 重要功能

- [x] 新增“汇率预警规则”菜单，支持查询、详情、新增、修改、删除、启停、导入和导出更严格的个性化规则，但不能删除、停用或放宽系统强制的 72 小时 3% 基线。
- [ ] 为有汇率预警权限的指定角色、部门或用户提供站内通知和未读状态，不通知无关用户。
- [ ] 支持配置邮件收件人组，复用邮件模板和可靠投递链路，并保存每次通知结果。
- [x] 将汇率预警事件接入系统监控“告警中心”，统一展示但不丢失汇率专属详情。

### 3.3 P2 可选功能

- [ ] 从趋势点下钻到对应业务汇率记录、原始报价、规则和实际使用快照。
- [ ] 支持同比、环比、来源切换审计和异常区间标注，但来源不作为主曲线或预警拆分维度。
- [ ] 支持预警静默时段、升级策略和多级阈值。

## 4. 业务口径

### 4.1 趋势事实

波动图的主事实定义如下：

| 项目 | 取值 |
|---|---|
| 数据表 | `exchange_business_rate` |
| 汇率值 | `final_rate` |
| 时间轴 | `effective_time` |
| 汇率类型 | `rate_type` |
| 币种对 | `base_currency + quote_currency` |
| 来源信息 | `source_code` 仅作为数据点审计证据，不参与曲线或预警分组 |
| 有效记录 | `deleted = 0`，保留 `ENABLED` 和 `EXPIRED` 历史版本，排除 `DISABLED` 人工停用记录 |

交易汇率和结算汇率必须分别查询、分别聚合、分别计算预警。币种方向也必须固定，`USD/CNY` 与 `CNY/USD` 是两个不同维度，禁止通过倒数自动合并。

### 4.2 已确认的 72 小时波动定义

“72 小时波动超过 3%”存在两种常见解释：

1. 区间振幅：`(窗口最高汇率 - 窗口最低汇率) / 窗口最低汇率 * 100%`。
2. 首尾变化：`abs(最新汇率 - 72 小时前汇率) / 72 小时前汇率 * 100%`。

本方案已确认并实现区间振幅。它能发现 72 小时内曾经发生但随后回落的明显波动，符合“72 小时内波动超过 3%”的风险监控语义。所有计算使用 `BigDecimal`，比例保留足够精度后再与 `3.0000%` 比较。

窗口内至少需要两个不同生效时间的数据点。样本不足时返回 `INSUFFICIENT_DATA`，不得按零波动处理。

### 4.3 跨来源汇总口径

推荐预警维度为：

```text
rateType + baseCurrency + quoteCurrency
```

同一币种对的 `TRANSACTION_RATE` 汇总 BOC、NBP、MANUAL 等全部来源形成一条交易汇率序列；`SETTLEMENT_RATE` 使用相同方式形成独立的结算汇率序列。两类汇率始终分开，类型内部不再按来源二次拆分。

查询和预警读取所有符合汇率类型、币种对和时间窗口的 `final_rate`，按 `effective_time, create_time, id` 稳定排序。来源切换造成的跳变直接计入窗口最高值、最低值和波动率。图表数据点、详情和预警事件仍保存对应 `source_code`、业务汇率 ID 和规则 ID，便于事后确认是哪一来源或手工操作造成波动。

同一时间存在多条业务汇率时，波动计算不得覆盖或去重，所有值都参与最高值和最低值计算；长时间范围降采样必须保留每个时间桶的最高值、最低值及其来源，不能只保留平均值而掩盖异常波动。

### 4.4 预警防抖与恢复

1. 由 `service-job` 独立任务默认每 5 分钟评估一次最近 72 小时窗口，Cron 可在现有任务调度页面受控维护。
2. 波动率大于 `3.0000%` 时触发，等于 3% 不触发。
3. 同一规则和汇率维度只能存在一个 `OPEN` 或 `ACKNOWLEDGED` 活动事件。
4. 持续超阈时更新事件的最大波动率、窗口和最后发现时间，不重复创建事件或重复发送通知。
5. 波动率连续两次低于 `2.0000%` 后自动转为 `RESOLVED`，形成迟滞区间，避免在 3% 附近反复触发。
6. 恢复后再次超过 3% 时可创建新事件；人工关闭不能掩盖仍在持续的超阈事实。

## 5. 页面与交互设计

### 5.1 菜单结构

在“汇率管理”目录下新增：

| 菜单编码 | 中文 | 英文 | 阶段 |
|---|---|---|---|
| `exchange_rate_volatility` | 汇率波动 | Rate Volatility | P0 |
| `exchange_rate_alert_event` | 汇率预警事件 | Rate Alert Events | P0 |
| `exchange_rate_alert_rule` | 汇率预警规则 | Rate Alert Rules | P1 |

菜单名称由后端返回的稳定 `menu_code` 映射前端 i18n，数据库中文名称只作为无翻译时的兼容回退。

### 5.2 汇率波动页

1. 使用分段标签切换“交易汇率”和“结算汇率”，切换后独立保存筛选状态。
2. 默认查询最近 72 小时，提供 24 小时、72 小时、7 天、30 天预设；自定义范围最大 90 天。
3. 币种对是必选条件，支持多选但默认最多 6 个系列；每个币种对在当前板块只形成一个跨来源汇总系列。
4. 默认展示“相对波动率”模式，便于比较数值量级不同的币种对；单币种对可切换为“实际汇率”模式。
5. 图表下方使用 `StandardTable` 展示全部匹配币种对的 72 小时统计摘要和预警状态。
6. 图表点击数据点后可查看业务汇率 ID、该点实际来源、原始汇率 ID、规则 ID、生效/失效时间；P2 再增加实际使用快照下钻。
7. 页面完整处理加载、空数据、样本不足、接口失败、无权限、长币种名称和窄屏状态。

### 5.3 汇率预警事件页

支持按汇率类型、币种对、状态、触发时间、确认人和事件编号筛选。列表展示触发值、阈值、窗口最高/最低汇率、最高/最低值来源、样本数、首次触发时间、最后发现时间和当前状态。

详情必须冻结触发时的规则、窗口、汇率点摘要和通知结果，不能随着规则修改而改变。确认、关闭等操作需要填写备注并记录操作人、操作时间和版本号。

### 5.4 图表技术选型

复用当前 ECharts 和 `@acquiring/shared/AnalyticsChart`，在 Admin 汇率模块内增加受控的汇率趋势 option builder，不新增图表库。需要复用以下现有能力：

- `ResizeObserver` 响应式尺寸；
- `DataZoom` 时间区间缩放；
- `Tooltip` 精确值展示；
- `MarkLine` 3% 阈值标记；
- loading、empty、ARIA 和 reduced-motion；
- 管理端既有颜色 Token 和暗色/主题兼容。

## 6. 后端架构

### 6.1 模块职责

| 模块 | 职责 |
|---|---|
| `service-admin` | 波动查询、规则和事件所有权、阈值计算、活动事件幂等、权限、审计、通知编排 |
| `service-job` | 注册独立 `EXCHANGE_RATE_VOLATILITY_MONITOR` 任务，复用现有任务定义、分布式租约、Cron、手工执行和运行日志；调用 Admin 幂等评估命令，不直接写预警表 |
| `acquiring-frontend/apps/admin-system` | 趋势和事件展示、筛选、权限按钮、i18n；不做阈值判断 |
| `component-library` | 仅复用现有内部鉴权、邮件、Outbox 等技术能力，不下沉汇率业务规则 |

`service-job` 通过内部鉴权接口调用 `service-admin` 的评估命令。任务处理器编码建议为 `exchangeRateVolatilityMonitorJob`，任务分组为 `exchange`，默认 Cron 为每 5 分钟一次，`DISTRIBUTED + SYNC + allowConcurrent=0`，失败按现有任务框架记录并重试。任务在管理系统现有“任务调度”和“任务日志”页面统一管理，不另建重复的调度配置页面。

该任务属于系统内置强制风险任务：允许具有权限的用户修改 Cron、超时、重试参数、启停和手工执行，但禁止通过普通任务删除接口删除或更换处理器。暂停和关键配置修改必须记录操作人、原因和操作日志；任务停用或连续两次未按期成功执行时，系统监控必须产生“汇率预警任务失效”运维告警。若现有 `service-job -> service-admin` 调用边不存在，实施时必须同时补齐 Nacos 环境配置、密钥轮换脚本、启动校验和回滚说明。

### 6.2 建议接口

| 方法 | 路径 | 权限/调用方 | 用途 |
|---|---|---|---|
| `POST` | `/admin/exchange/volatility/overview` | `exchange:volatility:list` | 查询币种对摘要 |
| `POST` | `/admin/exchange/volatility/trend` | `exchange:volatility:list` | 查询趋势点 |
| `POST` | `/admin/exchange/volatility/export` | `exchange:volatility:export` | 导出趋势摘要 |
| `POST` | `/admin/exchange/alert-events/search` | `exchange:alert-event:list` | 查询预警事件 |
| `GET` | `/admin/exchange/alert-events/{id}` | `exchange:alert-event:query` | 查询事件详情 |
| `PUT` | `/admin/exchange/alert-events/{id}/acknowledge` | `exchange:alert-event:ack` | 确认事件 |
| `PUT` | `/admin/exchange/alert-events/{id}/close` | `exchange:alert-event:close` | 关闭已恢复事件 |
| `POST` | `/admin/exchange/alert-events/export` | `exchange:alert-event:export` | 导出事件 |
| `POST` | `/internal/admin/exchange/alerts/evaluate` | `service-job` 内部鉴权 | 执行一次幂等评估 |

P1 规则接口按操作独立授权：

| 方法 | 路径 | 权限 | 用途 |
|---|---|---|---|
| `POST` | `/admin/exchange/alert-rules/search` | `exchange:alert-rule:list` | 分页查询规则 |
| `GET` | `/admin/exchange/alert-rules/{id}` | `exchange:alert-rule:query` | 查询规则详情 |
| `POST` | `/admin/exchange/alert-rules` | `exchange:alert-rule:add` | 新增自定义规则 |
| `PUT` | `/admin/exchange/alert-rules/{id}` | `exchange:alert-rule:edit` | 修改自定义规则 |
| `DELETE` | `/admin/exchange/alert-rules/{id}` | `exchange:alert-rule:remove` | 删除非系统规则 |
| `PUT` | `/admin/exchange/alert-rules/{id}/enable` | `exchange:alert-rule:start` | 启用规则 |
| `PUT` | `/admin/exchange/alert-rules/{id}/disable` | `exchange:alert-rule:stop` | 停用非系统规则 |
| `POST` | `/admin/exchange/alert-rules/import` | `exchange:alert-rule:import` | 导入并逐行校验规则 |
| `POST` | `/admin/exchange/alert-rules/export` | `exchange:alert-rule:export` | 导出规则 |

所有 `BigDecimal` 汇率和百分比字段对前端返回字符串，避免 JSON 浮点精度丢失。导入使用事务内逐行校验和结果明细，不能用部分失败的静默导入覆盖系统强制规则。

### 6.3 建议数据模型

#### `exchange_rate_alert_rule`

保存规则编码、汇率类型、币种范围、窗口分钟数、触发阈值、恢复阈值、最小样本数、级别、系统强制标识、启停状态和通知策略。来源不进入规则范围。系统内置的 72 小时 3% 规则不能删除、停用或将阈值放宽到 3% 以上。

#### `exchange_rate_alert_event`

保存事件编号、规则快照、汇率类型、币种对、窗口起止、样本数、最低/最高/最新汇率及其来源、业务汇率 ID、波动率、阈值、状态、首次触发时间、最后发现时间、恢复时间、确认/关闭人及版本号。来源字段只保存触发证据，不参与活动事件唯一键。

活动事件使用稳定 `active_event_key` 唯一约束防止并发重复创建；进入终态时清空活动键。状态更新使用版本 CAS，调度重复触发和多节点并发必须得到幂等结果。

#### `exchange_rate_alert_notify_log`

P1 保存事件、通知类型、接收人快照、模板、投递状态、开始/完成时间和失败原因。通知失败不能回滚预警事件，必须可重试且不重复发送成功记录。

#### `exchange_rate_alert_user_state`

仅在实现逐用户站内通知时新增，保存事件、用户、已读时间和确认状态。不得用 `sys_notice` 模拟逐用户已读。

### 6.4 索引建议

为趋势和窗口聚合增加覆盖查询方向的复合索引：

```text
(rate_type, base_currency, quote_currency, effective_time, deleted)
```

预警事件至少需要活动键唯一索引、状态与触发时间索引、币种对与触发时间索引。最终索引顺序必须基于本机数据量和 `EXPLAIN ANALYZE` 验证，不能仅凭字段直觉上线。

## 7. 权限设计

### 7.1 P0 汇率波动权限

| 权限 | 功能 | 国际化动作 |
|---|---|---|
| `exchange:volatility:list` | 菜单访问、分页和趋势查询 | 查询 / Search |
| `exchange:volatility:query` | 查看数据点和统计详情 | 详情 / Details |
| `exchange:volatility:export` | 导出波动摘要和趋势数据 | 导出 / Export |

汇率波动页是只读分析页，不提供没有业务意义的新增、修改、删除和导入。

### 7.2 P0 预警事件权限

| 权限 | 功能 | 国际化动作 |
|---|---|---|
| `exchange:alert-event:list` | 菜单访问和事件查询 | 查询 / Search |
| `exchange:alert-event:query` | 查看事件、规则快照和证据 | 详情 / Details |
| `exchange:alert-event:ack` | 确认活动事件 | 确认 / Acknowledge |
| `exchange:alert-event:close` | 关闭已恢复事件 | 关闭 / Close |
| `exchange:alert-event:export` | 导出事件及审计摘要 | 导出 / Export |

预警事件是不可变审计事实，不允许修改、删除或导入；此限制必须由后端保证，不能只隐藏按钮。

### 7.3 P1 预警规则权限

| 权限 | 功能 | 国际化动作 |
|---|---|---|
| `exchange:alert-rule:list` | 菜单访问和规则查询 | 查询 / Search |
| `exchange:alert-rule:query` | 查看规则详情 | 详情 / Details |
| `exchange:alert-rule:add` | 新增自定义规则 | 新增 / Add |
| `exchange:alert-rule:edit` | 修改规则 | 修改 / Edit |
| `exchange:alert-rule:remove` | 删除非系统规则 | 删除 / Delete |
| `exchange:alert-rule:start` | 启用规则 | 启用 / Enable |
| `exchange:alert-rule:stop` | 停用规则 | 停用 / Disable |
| `exchange:alert-rule:import` | 导入规则并逐行校验 | 导入 / Import |
| `exchange:alert-rule:export` | 导出规则 | 导出 / Export |

P1 订阅权限为 `exchange:alert-subscription:list/query/add/edit/remove/import/export`。任务调度复用现有 `monitor:job:list/query/edit/run/start/stop` 和 `monitor:jobLog:list/query/export` 权限；系统内置汇率监控任务不授予普通删除能力。

菜单权限、按钮权限和 API 权限必须同时存在。前端 `v-hasPermi` 只控制可见性，后端 `@RequiresPermission` 才是授权边界。菜单名称、页面标题、查询项、字段、按钮、状态、确认弹窗、校验、导入结果、导出文件名、错误提示和操作日志描述都必须提供 `zh-CN` 与 `en-US` 文案，不允许裸露 i18n key。超级管理员通过幂等 SQL 获得新增权限，其他角色不自动扩权。

## 8. 通知方案

### 8.1 P0 管理端事件

超过阈值后立即持久化预警事件，并在“汇率预警事件”菜单中展示。具有事件查询权限的用户可看到活动事件数量；这构成强制预警的最小闭环。

### 8.2 P1 站内通知

不建议通知管理系统全员。默认接收范围应为拥有汇率预警权限且被订阅配置选中的运营、财务或风险人员。每个用户具有独立未读状态，事件确认仍使用事件级权限和审计，不能把“已读”当成“已处理”。

### 8.3 P1 邮件通知

复用现有邮件模板、账户、Outbox 和发送记录能力。收件人来自受控订阅配置，不在 Nacos 或代码中硬编码个人邮箱。邮件正文仅包含币种对、来源、汇率类型、窗口、波动率、阈值、时间和管理端事件链接，不包含支付敏感数据。

## 9. 非功能要求

- 性能：默认 72 小时查询，单次最多 6 个序列、每序列最多 500 个展示点；更长范围由后端按固定时间桶降采样。
- 一致性：阈值判断读取主库；普通历史图表可读取从库，但页面要接受复制延迟并展示数据更新时间。
- 精度：数据库和 Java 使用 `DECIMAL/BigDecimal`，接口返回字符串；前端坐标转换不反向参与业务计算；跨来源汇总不得使用平均值代替最高/最低值。
- 幂等：调度至少一次、内部请求重放、多节点并发均不得产生重复活动事件或重复成功通知。
- 安全：所有接口受权限和数据范围控制；日志不打印完整请求、邮件正文或任何交易敏感数据。
- 可用性：评估失败记录任务失败并可重试，不影响汇率生成、交易或结算主链路。
- 可观测性：记录稳定事件码、评估批次号、扫描范围、维度数量、触发/更新/恢复数量和耗时。
- 兼容性：新表、新菜单和新接口为增量能力，不修改现有业务汇率、使用快照和资金计算语义。

## 10. 验收标准

### 10.1 波动图

- Given 同一交易汇率币种对在 72 小时内存在 BOC、NBP 和 MANUAL 数据点，When 用户查询交易汇率波动，Then 页面按时间顺序汇总展示为一条交易汇率曲线，每个点仍可查看实际来源。
- Given 同一币种对从 BOC 切换到 MANUAL 后出现明显跳变，When 评估任务执行，Then 两个来源的数据共同参与最高/最低值计算并触发预警，不因来源不同而拆开或忽略。
- Given 用户切换到结算汇率，When 查询相同币种对，Then 查询和统计只使用 `SETTLEMENT_RATE`。
- Given 某序列只有一个有效数据点，When 查询，Then 页面显示样本不足，不显示 0% 波动。
- Given 用户没有 `exchange:volatility:list`，When 访问路由或直接调用 API，Then 菜单不可见且后端拒绝访问。

### 10.2 强制预警

- Given 某一汇率维度最近 72 小时最低值为 `1.00000000`、最高值为 `1.03100000`，When 评估任务执行，Then 使用 `BigDecimal` 计算 `3.1000%` 并创建一个活动预警事件。
- Given 同一维度持续超过 3%，When 任务重复执行或并发执行，Then 只更新同一个活动事件，不新增重复事件。
- Given 波动率等于 `3.0000%`，When 评估，Then 不触发事件。
- Given 活动事件已确认但波动仍超过阈值，When 再次评估，Then 保持已确认状态并更新最后发现时间，不重复通知。
- Given 波动率连续两次低于恢复阈值，When 评估，Then 事件自动转为 `RESOLVED` 并记录恢复时间。
- Given 预警评估或通知失败，When 汇率生成、支付或结算继续执行，Then 主业务不被回滚或阻断，失败可被监控和重试。
- Given 调度扫描重复触发或多个 Job 节点并发执行，When 汇率波动任务运行，Then 统一调度租约和 Admin 幂等命令保证同一评估窗口不产生重复活动事件。
- Given 汇率波动任务被停用或连续两次未按期成功，When 系统监控检查任务健康度，Then 产生独立运维告警且管理端可查看任务和运行日志。

### 10.3 菜单、权限和国际化

- Given 数据库执行菜单权限迁移，When 重复执行脚本，Then 不产生重复菜单、权限或角色授权。
- Given 管理端切换 `zh-CN` 和 `en-US`，When 打开新增页面、详情和操作反馈，Then 菜单、字段、状态、按钮、校验和空态均使用对应语言且无裸露 i18n key。
- Given 用户仅有查询权限，When 打开事件页，Then 可查看但不能确认、关闭或导出。
- Given 用户切换 `zh-CN` 和 `en-US`，When 执行查询、新增、修改、删除、启停、导入或导出，Then 按页面真实能力展示对应语言的按钮、确认信息、校验结果、成功提示和失败提示。

### 10.4 性能和响应式

- Given 72 小时、6 个序列且每序列 500 个展示点，When 查询和渲染，Then 接口与页面满足实施阶段确定的 P95 目标，页面无主线程长时间阻塞。
- Given 1280px 桌面和常用窄屏视口，When 使用筛选、图表、表格和详情，Then 不发生控件重叠、页面级异常横向撑宽或文字遮挡。

## 11. 实施与回归状态

1. [x] 冻结区间振幅、跨来源汇总、连续两次低于 2% 恢复和首期管理端事件口径。
2. [x] 编写并在本机开发库执行 SQL precheck、migration、postcheck，提供 rollback/containment。
3. [x] 在 `service-admin` 实现趋势查询、规则/事件状态机、幂等评估和内部命令。
4. [x] 在 `service-job` 注册独立系统任务并接入现有任务调度页面；补齐任务保护、运行日志、任务健康告警、内部鉴权和 Nacos 配置。
5. [x] 在 Admin 前端实现菜单、API、i18n、趋势图、摘要表、规则页、事件页和统一告警中心接入，未新增图表库。
6. [x] 增加单元、权限、幂等、恢复、SQL 契约和前端类型检查覆盖。
7. [ ] 完成最终全量 Maven、前端 lint/typecheck/build 和真实浏览器验收后提交代码。

## 12. 已确认决策与后续项

### 已确认决策：72 小时波动公式

- 采用区间振幅 `(max - min) / min`，阈值比较使用 `BigDecimal`。

### 已确认决策：跨来源预警维度

- 同一汇率类型和币种对汇总所有来源计算和预警；交易汇率与结算汇率分别形成独立序列。
- `source_code` 仅保留为数据点、最高/最低值和事件的审计证据，不参与分组或活动事件唯一键。

### 已确认决策：首期通知范围

- 首期完成预警事件、权限用户可见、活动数量提示和统一告警中心接入。
- 按订阅用户投递的站内通知及邮件保留为后续项，不向管理系统全员广播。

### 已确认决策：恢复口径

- 连续两次低于 `2.0000%` 自动恢复，避免阈值附近反复触发。

