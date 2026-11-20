-- REQ-001 V1.5 链接范围、付款准入快照及无订单的交互记录。
-- 以下后缀对应已核对的本机 dev。实际执行前按目标拓扑确认模板及全部业务物理节点，生产使用核准的清单。
-- 先部署能够读取两种数据契约的代码，再启用新写。已有无订单交互时保留可空字段，回退不补造订单关联。
ALTER TABLE payment_link
    ADD COLUMN allowed_methods_json JSON NULL COMMENT '带版本的链接方式范围，空值表示沿用历史银行卡品牌范围';

ALTER TABLE transaction_merchant_snapshot
    ADD COLUMN payment_admission_snapshot_json JSON NULL COMMENT '首次受理时的规范方式、授权费率及执行引用的白名单快照';

ALTER TABLE transaction_merchant_api_interaction_log
    MODIFY COLUMN transaction_id VARCHAR(64) NULL COMMENT '真实系统订单标识，建单前拒绝时为空',
    ADD COLUMN admission_context_json JSON NULL COMMENT '建单前后的规范方式、稳定尝试及受理证据摘要';

ALTER TABLE transaction_merchant_snapshot_202603
    ADD COLUMN payment_admission_snapshot_json JSON NULL COMMENT '首次受理时的规范方式、授权费率及执行引用的白名单快照';

ALTER TABLE transaction_merchant_api_interaction_log_202603
    MODIFY COLUMN transaction_id VARCHAR(64) NULL COMMENT '真实系统订单标识，建单前拒绝时为空',
    ADD COLUMN admission_context_json JSON NULL COMMENT '建单前后的规范方式、稳定尝试及受理证据摘要';

ALTER TABLE transaction_merchant_snapshot_202604
    ADD COLUMN payment_admission_snapshot_json JSON NULL COMMENT '首次受理时的规范方式、授权费率及执行引用的白名单快照';

ALTER TABLE transaction_merchant_api_interaction_log_202604
    MODIFY COLUMN transaction_id VARCHAR(64) NULL COMMENT '真实系统订单标识，建单前拒绝时为空',
    ADD COLUMN admission_context_json JSON NULL COMMENT '建单前后的规范方式、稳定尝试及受理证据摘要';

ALTER TABLE transaction_merchant_snapshot_202701
    ADD COLUMN payment_admission_snapshot_json JSON NULL COMMENT '首次受理时的规范方式、授权费率及执行引用的白名单快照';

ALTER TABLE transaction_merchant_api_interaction_log_202701
    MODIFY COLUMN transaction_id VARCHAR(64) NULL COMMENT '真实系统订单标识，建单前拒绝时为空',
    ADD COLUMN admission_context_json JSON NULL COMMENT '建单前后的规范方式、稳定尝试及受理证据摘要';
