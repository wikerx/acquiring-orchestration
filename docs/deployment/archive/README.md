# 终态记录按月快照归档

本阶段只提供归档能力，**不迁移现有数据、不删除热表行、不修改业务读写路径**。`archive_terminal_snapshot.py` 按自然月和显式截止日，从固定白名单表只读复制到独立的 `表名_YYYYMM_时间.sql.gz` 文件，并附带 SHA-256、行数和筛选条件清单。文件按月份拆分，便于独立保管和核验；这**不是 MySQL 物理分表**，也不会降低当前热表容量。

## 范围和边界

| 表 | 本阶段允许复制的记录 |
| --- | --- |
| `sys_mq_outbox` | `SENT`；`CLOSED` 不纳入 |
| `settlement_event_outbox` | `SENT` |
| `settlement_projection_task` | `COMPLETED`；`FAILED` 不纳入 |
| `sys_oper_log` | 已写入的操作日志 |
| `sys_job_run_log` | `SUCCESS`、`FAILED`、`TIMEOUT`、`CANCELLED` |

筛选同时使用表中创建时间的自然月半开区间和 `--before` 日期上界。该能力只复制，不改变唯一键、状态、重试、对账或资金事实。将来若要从热表删除记录，须先设计并验证持久幂等键保留、历史查询路由、恢复、回滚和保留期；现阶段不能直接清理 Outbox 或投影任务，月文件也不能代替这些约束。

## 本机操作

前提：Docker 中存在 MySQL 客户端容器；容器环境变量 `MYSQL_ROOT_PASSWORD` 可用。目标目录由操作者预先创建，权限必须是 `0700`，归档文件和清单仅本机操作者可读。目录所在磁盘还应有独立备份和静态加密措施；脚本不上传文件，也不自动设置保留期。

先只读盘点：

```bash
python3 docs/deployment/archive/archive_terminal_snapshot.py \
  --table sys_mq_outbox --month 202608 --before 2026-09-01 --dry-run
```

仅在单独批准**实际复制历史数据**后，才执行以下命令；该命令仍不会删除源数据：

```bash
mkdir -m 700 /private/path/to/archive
python3 docs/deployment/archive/archive_terminal_snapshot.py \
  --table sys_mq_outbox --month 202608 --before 2026-09-01 \
  --output-dir /private/path/to/archive
python3 docs/deployment/archive/archive_terminal_snapshot.py \
  --verify /private/path/to/archive/sys_mq_outbox_202608_<UTC时间>.manifest.json
```

程序在快照前后分别计数，并要求压缩文件内逐行 `INSERT` 数量相等；不一致或导出失败时不发布快照。校验命令检查文件 SHA-256 和行数。仅比较行数无法证明并发更新时内容完全一致，因此正式执行应选择已稳定的旧月份，且在隔离库做恢复抽验后才可认定可恢复。**不要将归档 SQL 直接导回当前业务库**；恢复时先在隔离环境验证，再决定查询或修复方案。源表数据仍是当前业务查询和唯一约束的事实来源。

## 分表后续门槛

如需数据库物理月表或热表缩容，需另行确定 archive schema、跨月查询入口、全局幂等键墓碑、资金/审计保留期、批量迁移与逐批对账、故障恢复和回滚方案。尤其 `sys_mq_outbox.event_id`、`settlement_event_outbox.event_no`、`settlement_projection_task.task_no`、`sys_oper_log.idempotent_key` 等唯一身份不能随着热表删除而失效。在这些门槛通过前，不执行迁移或删除。
