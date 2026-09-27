-- ScottChannelA EUR 直连币种修复。
--
-- 现象：EUR 请求不在能力/MID 币种交集内时，支付路由会按默认币种 USD 进行 EDC，
-- 随后因缺少 EUR->USD 交易汇率在本地以 EXCHANGE_RATE_NOT_FOUND 失败，渠道不会收到请求。
-- ScottChannelA 文档允许请求 EUR，因此将 EUR 加入当前 MID 的能力和币种范围。

SET NAMES utf8mb4;
START TRANSACTION;

SET @scott_channel_a_id = (
    SELECT id
    FROM channel_info
    WHERE channel_code = 'SCOTT_CHANNEL_A'
      AND deleted = 0
    ORDER BY id DESC
    LIMIT 1
);

INSERT INTO channel_capability_currency (
    capability_id, channel_id, channel_code, currency_code,
    currency_status, create_by, update_by, deleted
)
SELECT capability.id, @scott_channel_a_id, 'SCOTT_CHANNEL_A', 'EUR',
       1, 'migration', 'migration', 0
FROM channel_payment_capability capability
WHERE @scott_channel_a_id IS NOT NULL
  AND capability.channel_id = @scott_channel_a_id
  AND capability.channel_code = 'SCOTT_CHANNEL_A'
  AND capability.deleted = 0
  AND NOT EXISTS (
      SELECT 1
      FROM channel_capability_currency currency
      WHERE currency.capability_id = capability.id
        AND currency.currency_code = 'EUR'
        AND currency.deleted = 0
  );

-- 该 MID 当前使用显式币种范围；ALL 表示不限制，不需要改写。
UPDATE channel_mid_config
SET currency_scope = CASE
    WHEN currency_scope IS NULL OR TRIM(currency_scope) = ''
         OR UPPER(TRIM(currency_scope)) = 'ALL' THEN currency_scope
    WHEN FIND_IN_SET('EUR', REPLACE(UPPER(currency_scope), ' ', '')) = 0
        THEN CONCAT(TRIM(currency_scope), ',EUR')
    ELSE currency_scope
END,
    update_by = 'migration'
WHERE channel_code = 'SCOTT_CHANNEL_A'
  AND channel_mid = 'MID202609121510536AB662'
  AND deleted = 0;

COMMIT;
