-- 修复 2026 年第三季度误写入模板表的 3DS 认证摘要。
-- 脚本按 authentication_info_id 防重，可重复执行；确认业务查询正常前不删除模板表原记录。
USE `payment_acquiring`;

START TRANSACTION;

INSERT INTO `transaction_authentication_info_202603`
(
  `authentication_info_id`,
  `transaction_id`,
  `operation_id`,
  `authentication_type`,
  `authentication_status`,
  `authentication_source`,
  `three_ds_version`,
  `three_ds_transaction_id`,
  `three_ds_server_transaction_id`,
  `acs_transaction_id`,
  `ds_transaction_id`,
  `eci`,
  `cavv`,
  `xid`,
  `liability_shift`,
  `challenge_required`,
  `challenge_status`,
  `authentication_redirect_url_hash`,
  `authentication_result_code`,
  `authentication_result_message`,
  `authentication_time`,
  `authentication_extra_json`,
  `transaction_date_time`,
  `transaction_utc_time`,
  `transaction_time_zone`,
  `create_time`,
  `update_time`
)
SELECT
  source.`authentication_info_id`,
  source.`transaction_id`,
  source.`operation_id`,
  source.`authentication_type`,
  source.`authentication_status`,
  source.`authentication_source`,
  source.`three_ds_version`,
  source.`three_ds_transaction_id`,
  source.`three_ds_server_transaction_id`,
  source.`acs_transaction_id`,
  source.`ds_transaction_id`,
  source.`eci`,
  source.`cavv`,
  source.`xid`,
  source.`liability_shift`,
  source.`challenge_required`,
  source.`challenge_status`,
  source.`authentication_redirect_url_hash`,
  source.`authentication_result_code`,
  source.`authentication_result_message`,
  source.`authentication_time`,
  source.`authentication_extra_json`,
  source.`transaction_date_time`,
  source.`transaction_utc_time`,
  source.`transaction_time_zone`,
  source.`create_time`,
  source.`update_time`
FROM `transaction_authentication_info` source
WHERE source.`transaction_date_time` >= '2026-07-01 00:00:00.000'
  AND source.`transaction_date_time` < '2026-10-01 00:00:00.000'
  AND NOT EXISTS (
    SELECT 1
    FROM `transaction_authentication_info_202603` target
    WHERE target.`authentication_info_id` = source.`authentication_info_id`
  );

COMMIT;

SELECT COUNT(*) AS migrated_row_count
FROM `transaction_authentication_info_202603`
WHERE `transaction_date_time` >= '2026-07-01 00:00:00.000'
  AND `transaction_date_time` < '2026-10-01 00:00:00.000';
