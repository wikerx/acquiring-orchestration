-- 扩展模板和现有分片。字段可空，历史记录无需回填。

ALTER TABLE `transaction_order`
    ADD COLUMN `channel_authorization_id` varchar(128) DEFAULT NULL COMMENT 'Scott 原授权生命周期编号',
    ALGORITHM=INSTANT;

ALTER TABLE `transaction_order_202603`
    ADD COLUMN `channel_authorization_id` varchar(128) DEFAULT NULL COMMENT 'Scott 原授权生命周期编号',
    ALGORITHM=INSTANT;

ALTER TABLE `transaction_order_202604`
    ADD COLUMN `channel_authorization_id` varchar(128) DEFAULT NULL COMMENT 'Scott 原授权生命周期编号',
    ALGORITHM=INSTANT;

ALTER TABLE `transaction_order_202701`
    ADD COLUMN `channel_authorization_id` varchar(128) DEFAULT NULL COMMENT 'Scott 原授权生命周期编号',
    ALGORITHM=INSTANT;

ALTER TABLE `transaction_channel_request`
    ADD COLUMN `channel_merchant_reference` varchar(128) DEFAULT NULL COMMENT 'Scott 实际发出的商户侧动作参考号',
    ADD COLUMN `channel_notify_url` varchar(512) DEFAULT NULL COMMENT 'Scott 首次外呼前冻结的本笔回调地址',
    ALGORITHM=INSTANT;

ALTER TABLE `transaction_channel_request_202603`
    ADD COLUMN `channel_merchant_reference` varchar(128) DEFAULT NULL COMMENT 'Scott 实际发出的商户侧动作参考号',
    ADD COLUMN `channel_notify_url` varchar(512) DEFAULT NULL COMMENT 'Scott 首次外呼前冻结的本笔回调地址',
    ALGORITHM=INSTANT;

ALTER TABLE `transaction_channel_request_202604`
    ADD COLUMN `channel_merchant_reference` varchar(128) DEFAULT NULL COMMENT 'Scott 实际发出的商户侧动作参考号',
    ADD COLUMN `channel_notify_url` varchar(512) DEFAULT NULL COMMENT 'Scott 首次外呼前冻结的本笔回调地址',
    ALGORITHM=INSTANT;

ALTER TABLE `transaction_channel_request_202701`
    ADD COLUMN `channel_merchant_reference` varchar(128) DEFAULT NULL COMMENT 'Scott 实际发出的商户侧动作参考号',
    ADD COLUMN `channel_notify_url` varchar(512) DEFAULT NULL COMMENT 'Scott 首次外呼前冻结的本笔回调地址',
    ALGORITHM=INSTANT;
