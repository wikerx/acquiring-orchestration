-- Route payment scope and MID dual-currency forward migration.
-- Scope is configuration only. No transaction, clearing or settlement table is modified.

DROP PROCEDURE IF EXISTS migrate_route_mid_dual_currency;
DELIMITER $$
CREATE PROCEDURE migrate_route_mid_dual_currency()
route_mid_dual_currency: BEGIN
    DECLARE unresolved_mid_count BIGINT DEFAULT 0;
    DECLARE invalid_mid_transaction_currency_count BIGINT DEFAULT 0;
    DECLARE invalid_candidate_scope_count BIGINT DEFAULT 0;
    DECLARE completed_index_count BIGINT DEFAULT 0;
    DECLARE invalid_active_scope_count BIGINT DEFAULT 0;

    IF DATABASE() <> 'payment_acquiring' THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'migration refused: current database must be payment_acquiring';
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'channel_mid_config'
          AND column_name = 'default_transaction_currency'
    ) THEN
        ALTER TABLE channel_mid_config
            ADD COLUMN default_transaction_currency CHAR(3) NULL
                COMMENT 'Default transaction currency for future initial transactions'
                AFTER allowed_country_scope;
    END IF;

    UPDATE channel_mid_config mid_record
    JOIN (
        SELECT mid_source.id AS mid_config_id,
               MAX(capability.default_transaction_currency) AS default_transaction_currency
        FROM channel_mid_config mid_source
        JOIN channel_payment_capability capability
          ON capability.channel_id = mid_source.channel_id
         AND capability.business_type = mid_source.business_type
         AND capability.capability_status = 1
         AND capability.deleted = 0
         AND (
              UPPER(TRIM(mid_source.payment_method_scope)) = 'ALL'
              OR FIND_IN_SET(capability.payment_method, UPPER(REPLACE(mid_source.payment_method_scope, ' ', ''))) > 0
         )
        WHERE mid_source.deleted = 0
        GROUP BY mid_source.id
        HAVING COUNT(DISTINCT capability.default_transaction_currency) = 1
    ) currency_backfill ON currency_backfill.mid_config_id = mid_record.id
    SET mid_record.default_transaction_currency = UPPER(currency_backfill.default_transaction_currency)
    WHERE mid_record.deleted = 0
      AND (mid_record.default_transaction_currency IS NULL OR TRIM(mid_record.default_transaction_currency) = '');

    SELECT COUNT(*) INTO unresolved_mid_count
    FROM channel_mid_config
    WHERE deleted = 0
      AND (default_transaction_currency IS NULL OR TRIM(default_transaction_currency) = '');

    IF unresolved_mid_count > 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'migration refused: MID default transaction currency is not deterministic';
    END IF;

    SELECT COUNT(*) INTO invalid_mid_transaction_currency_count
    FROM channel_mid_config
    WHERE deleted = 0
      AND UPPER(TRIM(currency_scope)) <> 'ALL'
      AND FIND_IN_SET(
          UPPER(TRIM(default_transaction_currency)),
          UPPER(REPLACE(currency_scope, ' ', ''))
      ) = 0;

    IF invalid_mid_transaction_currency_count > 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'migration refused: MID default transaction currency is outside transaction currency scope';
    END IF;

    ALTER TABLE channel_mid_config
        MODIFY COLUMN default_transaction_currency CHAR(3) NOT NULL
            COMMENT 'Default transaction currency for future initial transactions';

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'payment_route_method'
          AND column_name = 'card_brand'
    ) THEN
        ALTER TABLE payment_route_method
            ADD COLUMN card_brand VARCHAR(64) NOT NULL DEFAULT 'NONE'
                COMMENT 'Card brand; NONE for non-card payment methods'
                AFTER payment_method;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'payment_route_candidate'
          AND column_name = 'card_brand'
    ) THEN
        ALTER TABLE payment_route_candidate
            ADD COLUMN card_brand VARCHAR(64) NOT NULL DEFAULT 'NONE'
                COMMENT 'Card brand; NONE for non-card payment methods'
                AFTER payment_method;
    END IF;

    SELECT COUNT(DISTINCT CONCAT(table_name, ':', index_name)) INTO completed_index_count
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND (
          table_name = 'payment_route_method'
          AND index_name IN (
              'uk_payment_route_method_rule_scope_deleted',
              'idx_payment_route_method_scope_rule'
          )
          OR table_name = 'payment_route_candidate'
          AND index_name IN (
              'uk_payment_route_candidate_mid_deleted',
              'uk_payment_route_candidate_priority_deleted',
              'idx_payment_route_candidate_scope'
          )
      );

    SELECT (
        SELECT COUNT(*)
        FROM payment_route_method
        WHERE deleted = 0
          AND (
              payment_method = 'ALL'
              OR payment_method = 'BANK_CARD' AND card_brand IN ('ALL', 'NONE')
              OR payment_method <> 'BANK_CARD' AND card_brand <> 'NONE'
          )
    ) + (
        SELECT COUNT(*)
        FROM payment_route_candidate
        WHERE deleted = 0
          AND (
              payment_method = 'ALL'
              OR payment_method = 'BANK_CARD' AND card_brand IN ('ALL', 'NONE')
              OR payment_method <> 'BANK_CARD' AND card_brand <> 'NONE'
          )
    ) INTO invalid_active_scope_count;

    IF completed_index_count = 5 AND invalid_active_scope_count = 0 THEN
        LEAVE route_mid_dual_currency;
    END IF;

    IF EXISTS (
        SELECT 1 FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = 'payment_route_method'
          AND index_name = 'uk_payment_route_method_rule_method_deleted'
    ) THEN
        ALTER TABLE payment_route_method DROP INDEX uk_payment_route_method_rule_method_deleted;
    END IF;
    IF EXISTS (
        SELECT 1 FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = 'payment_route_method'
          AND index_name = 'idx_payment_route_method_method_rule'
    ) THEN
        ALTER TABLE payment_route_method DROP INDEX idx_payment_route_method_method_rule;
    END IF;
    IF EXISTS (
        SELECT 1 FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = 'payment_route_candidate'
          AND index_name = 'uk_payment_route_candidate_mid_deleted'
    ) THEN
        ALTER TABLE payment_route_candidate DROP INDEX uk_payment_route_candidate_mid_deleted;
    END IF;
    IF EXISTS (
        SELECT 1 FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = 'payment_route_candidate'
          AND index_name = 'uk_payment_route_candidate_priority_deleted'
    ) THEN
        ALTER TABLE payment_route_candidate DROP INDEX uk_payment_route_candidate_priority_deleted;
    END IF;
    IF EXISTS (
        SELECT 1 FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = 'payment_route_candidate'
          AND index_name = 'idx_payment_route_candidate_method'
    ) THEN
        ALTER TABLE payment_route_candidate DROP INDEX idx_payment_route_candidate_method;
    END IF;

    DROP TEMPORARY TABLE IF EXISTS tmp_route_candidate_scope;
    CREATE TEMPORARY TABLE tmp_route_candidate_scope (
        source_candidate_id BIGINT NOT NULL,
        rule_id BIGINT NOT NULL,
        payment_method VARCHAR(64) NOT NULL,
        card_brand VARCHAR(64) NOT NULL,
        channel_id BIGINT NOT NULL,
        mid_config_id BIGINT NOT NULL,
        candidate_priority INT NOT NULL,
        candidate_weight DECIMAL(5, 2) NULL,
        candidate_status TINYINT NOT NULL,
        create_by VARCHAR(64) NULL,
        create_time DATETIME(3) NULL,
        update_by VARCHAR(64) NULL,
        update_time DATETIME(3) NULL
    );
    INSERT INTO tmp_route_candidate_scope (
        source_candidate_id, rule_id, payment_method, card_brand,
        channel_id, mid_config_id, candidate_priority, candidate_weight,
        candidate_status, create_by, create_time, update_by, update_time
    )
    SELECT DISTINCT
           source_candidate.id AS source_candidate_id,
           source_candidate.rule_id,
           method_scope.payment_method,
           CASE
               WHEN method_scope.payment_method = 'BANK_CARD' THEN UPPER(TRIM(card_scope.card_brand))
               ELSE 'NONE'
           END AS card_brand,
           source_candidate.channel_id,
           source_candidate.mid_config_id,
           source_candidate.candidate_priority,
           source_candidate.candidate_weight,
           source_candidate.candidate_status,
           source_candidate.create_by,
           source_candidate.create_time,
           source_candidate.update_by,
           source_candidate.update_time
    FROM (
        SELECT candidate.*,
               CASE
                   WHEN UPPER(TRIM(mid_record.payment_method_scope)) = 'ALL' THEN (
                       SELECT GROUP_CONCAT(DISTINCT capability.payment_method ORDER BY capability.payment_method)
                       FROM channel_payment_capability capability
                       WHERE capability.channel_id = mid_record.channel_id
                         AND capability.business_type = mid_record.business_type
                         AND capability.capability_status = 1
                         AND capability.deleted = 0
                   )
                   ELSE mid_record.payment_method_scope
               END AS mid_payment_method_scope,
               CASE
                   WHEN UPPER(TRIM(candidate.payment_method)) = 'BANK_CARD'
                        AND UPPER(TRIM(candidate.card_brand)) NOT IN ('ALL', 'NONE')
                       THEN candidate.card_brand
                   WHEN UPPER(TRIM(mid_record.card_brand_scope)) = 'ALL' THEN (
                       SELECT GROUP_CONCAT(DISTINCT capability_brand.card_brand ORDER BY capability_brand.card_brand)
                       FROM channel_payment_capability capability
                       JOIN channel_capability_card_brand capability_brand
                         ON capability_brand.capability_id = capability.id
                        AND capability_brand.brand_status = 1
                        AND capability_brand.deleted = 0
                       WHERE capability.channel_id = mid_record.channel_id
                         AND capability.business_type = mid_record.business_type
                         AND capability.payment_method = 'BANK_CARD'
                         AND capability.capability_status = 1
                         AND capability.deleted = 0
                   )
                   ELSE mid_record.card_brand_scope
               END AS mid_card_brand_scope
        FROM payment_route_candidate candidate
        JOIN channel_mid_config mid_record
          ON mid_record.id = candidate.mid_config_id
         AND mid_record.deleted = 0
        WHERE candidate.deleted = 0
    ) source_candidate
    JOIN JSON_TABLE(
        CONCAT(
            '["',
            REPLACE(
                CASE
                    WHEN UPPER(TRIM(source_candidate.payment_method)) = 'ALL'
                        THEN UPPER(REPLACE(source_candidate.mid_payment_method_scope, ' ', ''))
                    ELSE UPPER(TRIM(source_candidate.payment_method))
                END,
                ',', '","'
            ),
            '"]'
        ),
        '$[*]' COLUMNS (payment_method VARCHAR(64) PATH '$')
    ) method_scope
    JOIN JSON_TABLE(
        CASE
            WHEN method_scope.payment_method <> 'BANK_CARD' THEN '["NONE"]'
            ELSE CONCAT(
                '["',
                REPLACE(UPPER(REPLACE(source_candidate.mid_card_brand_scope, ' ', '')), ',', '","'),
                '"]'
            )
        END,
        '$[*]' COLUMNS (card_brand VARCHAR(64) PATH '$')
    ) card_scope
    WHERE method_scope.payment_method NOT IN ('', 'ALL')
      AND (
          method_scope.payment_method <> 'BANK_CARD'
          OR card_scope.card_brand NOT IN ('', 'ALL', 'NONE')
      );

    SELECT COUNT(*) INTO invalid_candidate_scope_count
    FROM payment_route_candidate candidate
    WHERE candidate.deleted = 0
      AND NOT EXISTS (
          SELECT 1
          FROM tmp_route_candidate_scope expanded
          WHERE expanded.source_candidate_id = candidate.id
      );

    IF invalid_candidate_scope_count > 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'migration refused: route candidate cannot be expanded to explicit payment scopes';
    END IF;

    DROP TEMPORARY TABLE IF EXISTS tmp_route_method_scope;
    CREATE TEMPORARY TABLE tmp_route_method_scope AS
    SELECT DISTINCT rule_id, payment_method, card_brand
    FROM tmp_route_candidate_scope
    UNION
    SELECT DISTINCT method_record.rule_id,
           method_record.payment_method,
           CASE WHEN method_record.payment_method = 'BANK_CARD' THEN card_dict.dict_value ELSE 'NONE' END
    FROM payment_route_method method_record
    LEFT JOIN (
        SELECT DISTINCT UPPER(dict_value) AS dict_value
        FROM sys_dict_data
        WHERE dict_type = 'card_brand'
          AND status = 1
          AND deleted = 0
          AND UPPER(dict_value) NOT IN ('ALL', 'NONE')
    ) card_dict
      ON method_record.payment_method = 'BANK_CARD'
     AND UPPER(method_record.card_brand) IN ('ALL', 'NONE')
    WHERE method_record.deleted = 0
      AND UPPER(method_record.payment_method) <> 'ALL'
      AND (
          method_record.payment_method <> 'BANK_CARD'
          OR UPPER(method_record.card_brand) NOT IN ('ALL', 'NONE')
          OR card_dict.dict_value IS NOT NULL
      );

    UPDATE payment_route_candidate candidate
    JOIN (SELECT DISTINCT source_candidate_id FROM tmp_route_candidate_scope) source
      ON source.source_candidate_id = candidate.id
    SET candidate.deleted = candidate.id,
        candidate.update_by = 'route_scope_migration',
        candidate.update_time = CURRENT_TIMESTAMP(3)
    WHERE candidate.deleted = 0;

    INSERT INTO payment_route_candidate (
        rule_id, payment_method, card_brand, channel_id, mid_config_id,
        candidate_priority, candidate_weight, candidate_status,
        create_by, create_time, update_by, update_time, deleted
    )
    SELECT rule_id, payment_method, card_brand, channel_id, mid_config_id,
           candidate_priority, candidate_weight, candidate_status,
           create_by, create_time, 'route_scope_migration', CURRENT_TIMESTAMP(3), 0
    FROM tmp_route_candidate_scope;

    UPDATE payment_route_method
    SET deleted = id,
        update_by = 'route_scope_migration',
        update_time = CURRENT_TIMESTAMP(3)
    WHERE deleted = 0;

    INSERT INTO payment_route_method (
        rule_id, payment_method, card_brand,
        create_by, create_time, update_by, update_time, deleted
    )
    SELECT rule_id, payment_method, card_brand,
           'route_scope_migration', CURRENT_TIMESTAMP(3),
           'route_scope_migration', CURRENT_TIMESTAMP(3), 0
    FROM tmp_route_method_scope;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = 'payment_route_method'
          AND index_name = 'uk_payment_route_method_rule_scope_deleted'
    ) THEN
        ALTER TABLE payment_route_method
            ADD UNIQUE KEY uk_payment_route_method_rule_scope_deleted (rule_id, payment_method, card_brand, deleted);
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = 'payment_route_method'
          AND index_name = 'idx_payment_route_method_scope_rule'
    ) THEN
        ALTER TABLE payment_route_method
            ADD KEY idx_payment_route_method_scope_rule (payment_method, card_brand, rule_id, deleted);
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = 'payment_route_candidate'
          AND index_name = 'uk_payment_route_candidate_mid_deleted'
    ) THEN
        ALTER TABLE payment_route_candidate
            ADD UNIQUE KEY uk_payment_route_candidate_mid_deleted (rule_id, payment_method, card_brand, mid_config_id, deleted);
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = 'payment_route_candidate'
          AND index_name = 'uk_payment_route_candidate_priority_deleted'
    ) THEN
        ALTER TABLE payment_route_candidate
            ADD UNIQUE KEY uk_payment_route_candidate_priority_deleted (rule_id, payment_method, card_brand, candidate_priority, deleted);
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = 'payment_route_candidate'
          AND index_name = 'idx_payment_route_candidate_scope'
    ) THEN
        ALTER TABLE payment_route_candidate
            ADD KEY idx_payment_route_candidate_scope (payment_method, card_brand, candidate_status, deleted);
    END IF;

    DROP TEMPORARY TABLE IF EXISTS tmp_route_method_scope;
    DROP TEMPORARY TABLE IF EXISTS tmp_route_candidate_scope;
END route_mid_dual_currency$$
DELIMITER ;

CALL migrate_route_mid_dual_currency();
DROP PROCEDURE migrate_route_mid_dual_currency;
