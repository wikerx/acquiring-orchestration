-- 风控审计季度分表发布后的回退/隔离指引。
-- 本脚本故意不删除物理表、定位表或模板字段，避免覆盖切换后已经写入的新审计事实。
-- 1. 在 Nacos 将 risk-audit-sharding.enabled 改为 false。
-- 2. 按 service-data -> service-admin 顺序滚动重启，使 riskAudit 回到主库原单表兼容别名。
-- 3. 在恢复 MQ 消费前执行以下只读差异检查；如物理表存在单表没有的新事实，必须先走前向合并审批。

SELECT 'Q3_RECORD_ONLY_IN_PHYSICAL' AS check_name, COUNT(*) AS difference_count
FROM risk_evaluation_record_202603 physical
LEFT JOIN risk_evaluation_record source ON source.risk_record_no = physical.risk_record_no
WHERE source.id IS NULL;

SELECT 'Q4_RECORD_ONLY_IN_PHYSICAL' AS check_name, COUNT(*) AS difference_count
FROM risk_evaluation_record_202604 physical
LEFT JOIN risk_evaluation_record source ON source.risk_record_no = physical.risk_record_no
WHERE source.id IS NULL;

SELECT 'Q3_DETAIL_ONLY_IN_PHYSICAL' AS check_name, COUNT(*) AS difference_count
FROM risk_evaluation_hit_detail_202603 physical
LEFT JOIN risk_evaluation_hit_detail source ON source.id = physical.id
WHERE source.id IS NULL;

SELECT 'Q4_DETAIL_ONLY_IN_PHYSICAL' AS check_name, COUNT(*) AS difference_count
FROM risk_evaluation_hit_detail_202604 physical
LEFT JOIN risk_evaluation_hit_detail source ON source.id = physical.id
WHERE source.id IS NULL;

-- 只有四项 difference_count 全部为 0 时，关闭开关后的原单表才是完整回退读写面。
-- 物理表、定位表和 evaluation_time 字段保持原状，待问题修复并重新后检后可再次启用。
