-- 修复渠道支付能力与已有 MID 交易类型范围不一致的问题。
--
-- transaction_type_scope 是由 MID 已选支付方式和渠道启用能力共同派生的字段。
-- 本脚本只修复 ScottChannelA 的现有数据，不改变 MID 的支付方式、卡品牌、币种或国家范围。

USE `payment_acquiring`;

START TRANSACTION;

-- 执行前检查：结果不一致的记录会在本次事务中被修复。
SELECT id, channel_mid, business_type, payment_method_scope, transaction_type_scope
FROM channel_mid_config
WHERE channel_code = 'SCOTT_CHANNEL_A'
  AND deleted = 0
ORDER BY id;

WITH selected_transaction_type AS (
    SELECT mid.id AS mid_id,
           UPPER(TRIM(token.transaction_type)) AS transaction_type,
           capability.sort_order,
           capability.id AS capability_id,
           token.ordinality
    FROM channel_mid_config mid
    JOIN channel_payment_capability capability
      ON capability.channel_id = mid.channel_id
     AND capability.business_type = mid.business_type
     AND capability.capability_status = 1
     AND capability.deleted = 0
     AND (
          UPPER(TRIM(mid.payment_method_scope)) = 'ALL'
          OR FIND_IN_SET(
                 UPPER(TRIM(capability.payment_method)),
                 REPLACE(UPPER(mid.payment_method_scope), ' ', '')
             ) > 0
     )
    JOIN JSON_TABLE(
        CONCAT(
            '["',
            REPLACE(
                CASE
                    WHEN capability.business_type = 'PAYOUT'
                         AND (
                              capability.transaction_type IS NULL
                              OR TRIM(capability.transaction_type) = ''
                              OR FIND_IN_SET(
                                     'NONE',
                                     REPLACE(UPPER(capability.transaction_type), ' ', '')
                                 ) > 0
                         )
                        THEN 'PAYOUT,CANCEL'
                    ELSE capability.transaction_type
                END,
                ',',
                '","'
            ),
            '"]'
        ),
        '$[*]' COLUMNS (
            ordinality FOR ORDINALITY,
            transaction_type VARCHAR(64) PATH '$'
        )
    ) token
    WHERE mid.channel_code = 'SCOTT_CHANNEL_A'
      AND mid.deleted = 0
), distinct_transaction_type AS (
    SELECT mid_id,
           transaction_type,
           MIN(sort_order) AS first_sort_order,
           MIN(capability_id) AS first_capability_id,
           MIN(ordinality) AS first_ordinality
    FROM selected_transaction_type
    WHERE transaction_type <> ''
      AND transaction_type <> 'NONE'
    GROUP BY mid_id, transaction_type
), derived_scope AS (
    SELECT mid_id,
           GROUP_CONCAT(
               transaction_type
               ORDER BY first_sort_order, first_capability_id, first_ordinality
               SEPARATOR ','
           ) AS transaction_type_scope
    FROM distinct_transaction_type
    GROUP BY mid_id
)
UPDATE channel_mid_config mid
LEFT JOIN derived_scope scope ON scope.mid_id = mid.id
SET mid.transaction_type_scope = COALESCE(scope.transaction_type_scope, 'NONE'),
    mid.update_by = 'migration',
    mid.update_time = CURRENT_TIMESTAMP(3)
WHERE mid.channel_code = 'SCOTT_CHANNEL_A'
  AND mid.deleted = 0
  AND mid.transaction_type_scope <> COALESCE(scope.transaction_type_scope, 'NONE');

SELECT ROW_COUNT() AS repaired_mid_count;

-- 执行后检查：收单 MID 应包含 AUTHORIZATION，代付 MID 应为 PAYOUT,CANCEL。
SELECT id, channel_mid, business_type, payment_method_scope, transaction_type_scope
FROM channel_mid_config
WHERE channel_code = 'SCOTT_CHANNEL_A'
  AND deleted = 0
ORDER BY id;

COMMIT;
