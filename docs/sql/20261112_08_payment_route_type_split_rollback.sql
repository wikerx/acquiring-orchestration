-- 支付路由阶段1A类型拆分结构回滚。
-- WEIGHT或HISTORY_AFFINITY已产生配置时拒绝回滚，避免丢失类型专属语义。

DROP PROCEDURE IF EXISTS rollback_payment_route_type_split;
DELIMITER $$
CREATE PROCEDURE rollback_payment_route_type_split()
BEGIN
    IF EXISTS (
        SELECT 1 FROM payment_route_rule
        WHERE deleted = 0 AND rule_type IN ('WEIGHT', 'HISTORY_AFFINITY')
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'weight or history affinity route exists; export or forward-fix before rollback';
    END IF;

    UPDATE payment_route_rule
    SET rule_type = 'STANDARD'
    WHERE rule_type = 'PRIORITY';

    UPDATE payment_route_strategy strategy
    JOIN payment_route_rule rule_record ON rule_record.id = strategy.rule_id
    SET strategy.strategy_type = 'PRIORITY', strategy.strategy_priority = 100
    WHERE strategy.deleted = 0 AND rule_record.deleted = 0;

    ALTER TABLE payment_route_candidate DROP COLUMN candidate_weight;
    ALTER TABLE payment_route_strategy DROP COLUMN history_lookback_months;
    DROP TABLE payment_route_method;
END$$
DELIMITER ;

CALL rollback_payment_route_type_split();
DROP PROCEDURE rollback_payment_route_type_split;
