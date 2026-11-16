-- Safe rollback for the route payment scope and MID dual-currency migration.
-- The rollback refuses to discard multi-brand or distinct transaction-currency semantics.

DROP PROCEDURE IF EXISTS rollback_route_mid_dual_currency;
DELIMITER $$
CREATE PROCEDURE rollback_route_mid_dual_currency()
BEGIN
    DECLARE multi_brand_scope_count BIGINT DEFAULT 0;
    DECLARE distinct_currency_semantic_count BIGINT DEFAULT 0;

    SELECT COUNT(*) INTO multi_brand_scope_count
    FROM (
        SELECT rule_id, payment_method, mid_config_id
        FROM payment_route_candidate
        WHERE deleted = 0
          AND payment_method = 'BANK_CARD'
        GROUP BY rule_id, payment_method, mid_config_id
        HAVING COUNT(DISTINCT card_brand) > 1
        UNION ALL
        SELECT rule_id, payment_method, 0 AS mid_config_id
        FROM payment_route_method
        WHERE deleted = 0
          AND payment_method = 'BANK_CARD'
        GROUP BY rule_id, payment_method
        HAVING COUNT(DISTINCT card_brand) > 1
    ) multi_brand_scope;

    IF multi_brand_scope_count > 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'rollback refused: multi-brand route configurations exist';
    END IF;

    SELECT COUNT(*) INTO distinct_currency_semantic_count
    FROM channel_mid_config
    WHERE deleted = 0
      AND default_transaction_currency <> default_settlement_currency;

    IF distinct_currency_semantic_count > 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'rollback refused: MID transaction and settlement currencies differ';
    END IF;

    ALTER TABLE payment_route_method
        DROP INDEX uk_payment_route_method_rule_scope_deleted,
        DROP INDEX idx_payment_route_method_scope_rule;
    ALTER TABLE payment_route_candidate
        DROP INDEX uk_payment_route_candidate_mid_deleted,
        DROP INDEX uk_payment_route_candidate_priority_deleted,
        DROP INDEX idx_payment_route_candidate_scope;

    UPDATE payment_route_method
    SET card_brand = 'NONE'
    WHERE deleted = 0;
    UPDATE payment_route_candidate
    SET card_brand = 'NONE'
    WHERE deleted = 0;

    ALTER TABLE payment_route_method DROP COLUMN card_brand;
    ALTER TABLE payment_route_method
        ADD UNIQUE KEY uk_payment_route_method_rule_method_deleted (rule_id, payment_method, deleted),
        ADD KEY idx_payment_route_method_method_rule (payment_method, rule_id, deleted);

    ALTER TABLE payment_route_candidate DROP COLUMN card_brand;
    ALTER TABLE payment_route_candidate
        ADD UNIQUE KEY uk_payment_route_candidate_mid_deleted (rule_id, payment_method, mid_config_id, deleted),
        ADD UNIQUE KEY uk_payment_route_candidate_priority_deleted (rule_id, payment_method, candidate_priority, deleted),
        ADD KEY idx_payment_route_candidate_method (payment_method, candidate_status, deleted);

    ALTER TABLE channel_mid_config DROP COLUMN default_transaction_currency;
END$$
DELIMITER ;

CALL rollback_route_mid_dual_currency();
DROP PROCEDURE rollback_route_mid_dual_currency;
