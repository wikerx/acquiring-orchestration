# 本机可靠性监控演练

该目录只用于本机开发验收，加载正式可靠性规则并提供一条可恢复的演练告警。演练不会写业务数据库，也不能替代 UAT/生产准入。

## 启动

先启动本机 Webhook/指标接收器：

```bash
python3 alert-webhook-receiver.py
```

再启动 Prometheus 与 Alertmanager：

```bash
docker compose -f compose.yml up -d
```

Prometheus 和 Alertmanager 只绑定 `127.0.0.1`，应用指标通过 Docker Desktop 的 `host.docker.internal` 采集。

完整验收要求以下服务均已启动并暴露 `/actuator/prometheus`：

| 服务 | 端口 |
| --- | --- |
| service-admin | 8001 |
| service-merchant | 8002 |
| service-openapi | 8004 |
| service-payment | 8005 |
| service-payout | 8006 |
| service-risk | 8008 |
| service-data | 8009 |
| service-clearing | 8010 |
| service-settlement | 8011 |

若本机只启动部分服务，其余 target 显示 down 是环境未启动，不得把它解释为代码验收通过；
全系统验收前必须启动完整服务集合，或在交付结果中逐项标记未执行。

## 通知触发与恢复

```bash
curl -X POST http://127.0.0.1:19093/drill/firing
curl http://127.0.0.1:19093/events
curl -X POST http://127.0.0.1:19093/drill/resolved
curl http://127.0.0.1:19093/events
```

验收时必须同时确认 Prometheus target 为 `up`、规则已加载、Alertmanager 收到 firing 和 resolved 两种通知。

正式 Outbox 告警还需要核对 `job`、`application`、`environment`、`outbox_type`，并与数据库只读汇总一致。
交易 Outbox 的 CLOSED/FAILED 只能进入专项分析，禁止使用操作日志补偿入口重放。
