-- 支付路由阶段1A交易维度精简：结构回滚。
-- 仅当每条规则至多配置一个交易类型时可回滚，避免多选数据被静默截断。

DROP PROCEDURE IF EXISTS rollback_payment_route_transaction_dimension;
DELIMITER $$
CREATE PROCEDURE rollback_payment_route_transaction_dimension()
BEGIN
    DECLARE multi_value_rule_count BIGINT DEFAULT 0;

    IF EXISTS (
        SELECT 1
        FROM information_schema.tables
        WHERE table_schema = DATABASE()
          AND table_name = 'payment_route_transaction_type'
    ) THEN
        SELECT COUNT(*)
        INTO multi_value_rule_count
        FROM (
            SELECT rule_id
            FROM payment_route_transaction_type
            WHERE deleted = 0
            GROUP BY rule_id
            HAVING COUNT(*) > 1
        ) multi_value_rule;

        IF multi_value_rule_count > 0 THEN
            SIGNAL SQLSTATE '45000'
                SET MESSAGE_TEXT = 'payment route transaction type rollback refused: multi-value rules exist';
        END IF;

        UPDATE payment_route_rule rule_record
        LEFT JOIN payment_route_transaction_type transaction_record
          ON transaction_record.rule_id = rule_record.id
         AND transaction_record.deleted = 0
        SET rule_record.transaction_type = transaction_record.transaction_type
        WHERE rule_record.deleted = 0;

        DROP TABLE payment_route_transaction_type;
    END IF;
END$$
DELIMITER ;

CALL rollback_payment_route_transaction_dimension();
DROP PROCEDURE rollback_payment_route_transaction_dimension;
