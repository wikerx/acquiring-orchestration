SET NAMES utf8mb4;
SET @schema_name = DATABASE();

SET @sql = (
    SELECT IF(COUNT(*) = 0,
              'ALTER TABLE base_mcc_level1 ADD COLUMN deleted BIGINT NOT NULL DEFAULT 0 COMMENT ''删除标识：0未删除，大于0为删除记录ID'' AFTER update_time',
              'DO 0')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'base_mcc_level1' AND COLUMN_NAME = 'deleted'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(COUNT(*) = 0,
              'ALTER TABLE base_mcc_level2 ADD COLUMN deleted BIGINT NOT NULL DEFAULT 0 COMMENT ''删除标识：0未删除，大于0为删除记录ID'' AFTER update_time',
              'DO 0')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'base_mcc_level2' AND COLUMN_NAME = 'deleted'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(COUNT(*) = 0,
              'ALTER TABLE base_mcc_code ADD COLUMN deleted BIGINT NOT NULL DEFAULT 0 COMMENT ''删除标识：0未删除，大于0为删除记录ID'' AFTER update_time',
              'DO 0')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'base_mcc_code' AND COLUMN_NAME = 'deleted'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(COUNT(*) = 0,
              'ALTER TABLE base_mcc_risk_policy ADD COLUMN channel_scope VARCHAR(16) NOT NULL DEFAULT ''ALL'' COMMENT ''渠道适用范围：ALL全部，SPECIFIC指定'' AFTER card_scheme',
              'DO 0')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'base_mcc_risk_policy' AND COLUMN_NAME = 'channel_scope'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(COUNT(*) = 0,
              'ALTER TABLE base_mcc_risk_policy ADD COLUMN country_scope VARCHAR(16) NOT NULL DEFAULT ''ALL'' COMMENT ''国家地区适用范围：ALL全部，SPECIFIC指定'' AFTER channel_code',
              'DO 0')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'base_mcc_risk_policy' AND COLUMN_NAME = 'country_scope'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(COUNT(*) = 0,
              'ALTER TABLE base_mcc_risk_policy ADD COLUMN status TINYINT NOT NULL DEFAULT 1 COMMENT ''状态：0停用，1启用'' AFTER require_enhanced_review',
              'DO 0')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'base_mcc_risk_policy' AND COLUMN_NAME = 'status'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(COUNT(*) = 0,
              'ALTER TABLE base_mcc_risk_policy ADD COLUMN policy_status TINYINT NOT NULL DEFAULT 1 COMMENT ''策略状态：0停用，1启用'' AFTER status',
              'DO 0')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'base_mcc_risk_policy' AND COLUMN_NAME = 'policy_status'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(COUNT(*) = 0,
              'ALTER TABLE base_mcc_risk_policy ADD COLUMN deleted BIGINT NOT NULL DEFAULT 0 COMMENT ''删除标识：0未删除，大于0为删除记录ID'' AFTER update_time',
              'DO 0')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'base_mcc_risk_policy' AND COLUMN_NAME = 'deleted'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

UPDATE base_mcc_risk_policy
SET channel_scope = CASE WHEN channel_code IS NULL OR channel_code = '' OR channel_code = 'ALL' THEN 'ALL' ELSE 'SPECIFIC' END,
    country_scope = CASE WHEN country_code IS NULL OR country_code = '' OR country_code = 'ALL' THEN 'ALL' ELSE 'SPECIFIC' END,
    status = COALESCE(status, policy_status, 1),
    policy_status = COALESCE(policy_status, status, 1)
WHERE deleted = 0;

UPDATE base_mcc_code
SET mcc_type = CASE
    WHEN mcc_type IN ('通用MCC', '通用 MCC', 'Common MCC') THEN 'COMMON'
    WHEN mcc_type IN ('品牌专属（酒店）', '品牌专属（航空公司）', '品牌专属（租车公司）', 'Special MCC') THEN 'SPECIAL'
    WHEN mcc_type IN ('限制MCC', '限制 MCC', 'Restricted MCC') THEN 'RESTRICTED'
    WHEN mcc_type IN ('禁入MCC', '禁入 MCC', 'Prohibited MCC') THEN 'PROHIBITED'
    ELSE mcc_type
END
WHERE deleted = 0;

SET @sql = (
    SELECT IF(
        (SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'base_mcc_level3') > 0
        AND
        (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'base_mcc_code' AND COLUMN_NAME = 'level3_id') > 0,
        'UPDATE base_mcc_code c
         JOIN base_mcc_level3 l3 ON c.level3_id = l3.id
         SET c.level1_id = l3.level1_id,
             c.level2_id = l3.level2_id
         WHERE c.deleted = 0
           AND (c.level2_id IS NULL OR c.level2_id = 0
                OR NOT EXISTS (SELECT 1 FROM base_mcc_level2 l2 WHERE l2.id = c.level2_id AND l2.deleted = 0))',
        'DO 0'
    )
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SELECT COUNT(*) AS mcc_code_without_level2_before_drop
FROM base_mcc_code c
LEFT JOIN base_mcc_level2 l2
  ON c.level2_id = l2.id
 AND l2.deleted = 0
WHERE c.deleted = 0
  AND l2.id IS NULL;

INSERT INTO base_mcc_risk_policy (
    mcc_code, card_scheme, channel_scope, channel_code, country_scope, country_code,
    risk_level, allow_onboarding, allow_acquiring, require_enhanced_review, status,
    policy_status, priority, effective_time, expire_time, remark, create_time, update_time, deleted
)
SELECT old_policy.mcc_code,
       scheme.dict_value,
       COALESCE(NULLIF(old_policy.channel_scope, ''), 'ALL'),
       CASE WHEN COALESCE(NULLIF(old_policy.channel_scope, ''), 'ALL') = 'ALL' THEN '' ELSE old_policy.channel_code END,
       COALESCE(NULLIF(old_policy.country_scope, ''), 'ALL'),
       CASE WHEN COALESCE(NULLIF(old_policy.country_scope, ''), 'ALL') = 'ALL' THEN '' ELSE old_policy.country_code END,
       old_policy.risk_level,
       old_policy.allow_onboarding,
       old_policy.allow_acquiring,
       old_policy.require_enhanced_review,
       old_policy.status,
       old_policy.policy_status,
       old_policy.priority,
       old_policy.effective_time,
       old_policy.expire_time,
       CONCAT(COALESCE(old_policy.remark, ''), ' migrated from card_scheme=ALL'),
       CURRENT_TIMESTAMP(3),
       CURRENT_TIMESTAMP(3),
       0
FROM base_mcc_risk_policy old_policy
JOIN sys_dict_data scheme
  ON scheme.dict_type = 'card_scheme'
 AND scheme.status = 1
 AND scheme.deleted = 0
 AND scheme.dict_value <> 'ALL'
WHERE old_policy.deleted = 0
  AND old_policy.card_scheme = 'ALL'
  AND NOT EXISTS (
      SELECT 1
      FROM base_mcc_risk_policy exists_policy
      WHERE exists_policy.deleted = 0
        AND exists_policy.mcc_code = old_policy.mcc_code
        AND exists_policy.card_scheme = scheme.dict_value
        AND exists_policy.channel_scope = COALESCE(NULLIF(old_policy.channel_scope, ''), 'ALL')
        AND exists_policy.channel_code = CASE WHEN COALESCE(NULLIF(old_policy.channel_scope, ''), 'ALL') = 'ALL' THEN '' ELSE old_policy.channel_code END
        AND exists_policy.country_scope = COALESCE(NULLIF(old_policy.country_scope, ''), 'ALL')
        AND exists_policy.country_code = CASE WHEN COALESCE(NULLIF(old_policy.country_scope, ''), 'ALL') = 'ALL' THEN '' ELSE old_policy.country_code END
  );

UPDATE base_mcc_risk_policy
SET status = 0,
    policy_status = 0,
    deleted = id,
    update_time = CURRENT_TIMESTAMP(3)
WHERE deleted = 0
  AND card_scheme = 'ALL';

UPDATE base_mcc_risk_policy
SET channel_code = ''
WHERE deleted = 0
  AND channel_scope = 'ALL'
  AND channel_code = 'ALL';

UPDATE base_mcc_risk_policy
SET country_code = ''
WHERE deleted = 0
  AND country_scope = 'ALL'
  AND country_code = 'ALL';

SET @sql = (
    SELECT IF(COUNT(*) = 0,
              'DO 0',
              CONCAT('ALTER TABLE base_mcc_code ', GROUP_CONCAT(CONCAT('DROP INDEX ', index_name) SEPARATOR ', ')))
    FROM (
        SELECT DISTINCT index_name
        FROM information_schema.STATISTICS
        WHERE table_schema = @schema_name
          AND table_name = 'base_mcc_code'
          AND column_name = 'level3_id'
          AND index_name <> 'PRIMARY'
    ) indexes_to_drop
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(COUNT(*) = 0,
              'ALTER TABLE base_mcc_code ADD INDEX idx_base_mcc_code_l1_l2 (level1_id, level2_id)',
              'DO 0')
    FROM information_schema.STATISTICS
    WHERE table_schema = @schema_name
      AND table_name = 'base_mcc_code'
      AND index_name = 'idx_base_mcc_code_l1_l2'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(COUNT(*) = 0,
              'DO 0',
              'ALTER TABLE base_mcc_code DROP COLUMN level3_id')
    FROM information_schema.COLUMNS
    WHERE table_schema = @schema_name
      AND table_name = 'base_mcc_code'
      AND column_name = 'level3_id'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(COUNT(*) = 0,
              'DO 0',
              'ALTER TABLE base_mcc_risk_policy DROP INDEX uk_mcc_risk_scope')
    FROM information_schema.STATISTICS
    WHERE table_schema = @schema_name
      AND table_name = 'base_mcc_risk_policy'
      AND index_name = 'uk_mcc_risk_scope'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(COUNT(*) = 0,
              'ALTER TABLE base_mcc_risk_policy ADD UNIQUE KEY uk_base_mcc_risk_scope_deleted (mcc_code, card_scheme, channel_scope, channel_code, country_scope, country_code, deleted)',
              'DO 0')
    FROM information_schema.STATISTICS
    WHERE table_schema = @schema_name
      AND table_name = 'base_mcc_risk_policy'
      AND index_name = 'uk_base_mcc_risk_scope_deleted'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

ALTER TABLE base_mcc_risk_policy
    MODIFY card_scheme VARCHAR(32) NOT NULL DEFAULT '' COMMENT '真实卡品牌编码，不允许ALL',
    MODIFY channel_scope VARCHAR(16) NOT NULL DEFAULT 'ALL' COMMENT '渠道适用范围：ALL全部，SPECIFIC指定',
    MODIFY channel_code VARCHAR(64) NOT NULL DEFAULT '' COMMENT '渠道编码，范围为SPECIFIC时必填，否则为空字符串',
    MODIFY country_scope VARCHAR(16) NOT NULL DEFAULT 'ALL' COMMENT '国家地区适用范围：ALL全部，SPECIFIC指定',
    MODIFY country_code VARCHAR(8) NOT NULL DEFAULT '' COMMENT 'ISO国家地区编码，范围为SPECIFIC时必填，否则为空字符串';

DROP TABLE IF EXISTS base_mcc_level3;

SELECT COUNT(*) AS base_mcc_level3_exists
FROM information_schema.tables
WHERE table_schema = DATABASE()
  AND table_name = 'base_mcc_level3';

SELECT COUNT(*) AS base_mcc_code_level3_id_exists
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND table_name = 'base_mcc_code'
  AND column_name = 'level3_id';

SELECT COUNT(*) AS level1_count FROM base_mcc_level1 WHERE deleted = 0;
SELECT COUNT(*) AS level2_count FROM base_mcc_level2 WHERE deleted = 0;
SELECT COUNT(*) AS mcc_code_count FROM base_mcc_code WHERE deleted = 0;

SELECT COUNT(*) AS mcc_code_without_level2
FROM base_mcc_code c
LEFT JOIN base_mcc_level2 l2
  ON c.level2_id = l2.id
 AND l2.deleted = 0
WHERE c.deleted = 0
  AND l2.id IS NULL;

SELECT COUNT(*) AS active_invalid_card_scheme_all_count
FROM base_mcc_risk_policy
WHERE deleted = 0
  AND card_scheme = 'ALL';

SELECT COUNT(*) AS active_invalid_channel_code_all_count
FROM base_mcc_risk_policy
WHERE deleted = 0
  AND channel_code = 'ALL';

SELECT COUNT(*) AS active_invalid_country_code_all_count
FROM base_mcc_risk_policy
WHERE deleted = 0
  AND country_code = 'ALL';

SELECT COUNT(*) AS invalid_card_scheme_not_in_dict
FROM base_mcc_risk_policy p
LEFT JOIN sys_dict_data d
  ON d.dict_type = 'card_scheme'
 AND d.dict_value = p.card_scheme
 AND d.status = 1
 AND d.deleted = 0
WHERE p.deleted = 0
  AND p.card_scheme <> ''
  AND d.id IS NULL;
