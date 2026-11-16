-- 支付路由指定商户多选：结构回滚。
-- 一条规则已经配置多个商户时拒绝回滚，避免把多选事实截断为旧单值字段。

DROP PROCEDURE IF EXISTS rollback_payment_route_multi_merchant;
DELIMITER $$
CREATE PROCEDURE rollback_payment_route_multi_merchant()
BEGIN
    DECLARE multi_merchant_rule_count BIGINT DEFAULT 0;

    IF EXISTS (
        SELECT 1
        FROM information_schema.tables
        WHERE table_schema = DATABASE()
          AND table_name = 'payment_route_rule_merchant'
    ) THEN
        SELECT COUNT(*)
        INTO multi_merchant_rule_count
        FROM (
            SELECT rule_id
            FROM payment_route_rule_merchant
            WHERE deleted = 0
            GROUP BY rule_id
            HAVING COUNT(*) > 1
        ) multi_merchant_rule;

        IF multi_merchant_rule_count > 0 THEN
            SIGNAL SQLSTATE '45000'
                SET MESSAGE_TEXT = 'payment route multi-merchant rollback refused: multi-merchant rules exist';
        END IF;

        UPDATE payment_route_rule rule_record
        LEFT JOIN payment_route_rule_merchant relation_record
          ON relation_record.rule_id = rule_record.id
         AND relation_record.deleted = 0
        SET rule_record.merchant_id = relation_record.merchant_id
        WHERE rule_record.deleted = 0
          AND rule_record.scope_type = 'MERCHANT';

        DROP TABLE payment_route_rule_merchant;
    END IF;
END$$
DELIMITER ;

CALL rollback_payment_route_multi_merchant();
DROP PROCEDURE rollback_payment_route_multi_merchant;
