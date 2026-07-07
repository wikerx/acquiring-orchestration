-- AML、黑名单、白名单名单类功能移除 rule_name 字段迁移草案。
-- 执行前请确认管理端代码已发布到不再读写名单表 rule_name 字段，并完成数据库备份。
-- 内风控规则表 risk_rule_* 仍保留 rule_name，不在本脚本处理范围内。

SET @risk_list_rule_name_tables = 'risk_aml_card,risk_aml_card_bin,risk_aml_ip,risk_aml_country,risk_aml_email,risk_aml_phone,risk_aml_cardholder_name,risk_aml_source_url,risk_black_card_no,risk_black_card_fingerprint,risk_black_card_bin,risk_black_cardholder_name,risk_black_phone,risk_black_ip,risk_black_email,risk_black_email_username,risk_black_email_domain,risk_black_billing_address,risk_black_billing_zip,risk_black_billing_country,risk_black_shipping_address,risk_black_shipping_zip,risk_black_shipping_country,risk_black_issuer_country,risk_black_device_fingerprint,risk_black_region,risk_white_merchant,risk_white_card_no,risk_white_card_fingerprint,risk_white_card_bin,risk_white_ip,risk_white_trade_country,risk_white_issuer_country,risk_white_email,risk_white_email_domain,risk_white_phone,risk_white_customer_id,risk_white_device_fingerprint';

DROP PROCEDURE IF EXISTS migrate_risk_list_drop_rule_name;
DELIMITER $$
CREATE PROCEDURE migrate_risk_list_drop_rule_name()
BEGIN
    DECLARE done INT DEFAULT 0;
    DECLARE current_table VARCHAR(128);
    DECLARE table_cursor CURSOR FOR
        SELECT table_name
        FROM information_schema.tables
        WHERE table_schema = DATABASE()
          AND FIND_IN_SET(table_name, @risk_list_rule_name_tables);
    DECLARE CONTINUE HANDLER FOR NOT FOUND SET done = 1;

    OPEN table_cursor;
    table_loop: LOOP
        FETCH table_cursor INTO current_table;
        IF done = 1 THEN
            LEAVE table_loop;
        END IF;

        IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = current_table AND column_name = 'rule_name') THEN
            SET @sql = CONCAT('ALTER TABLE ', current_table, ' DROP COLUMN rule_name');
            PREPARE stmt FROM @sql;
            EXECUTE stmt;
            DEALLOCATE PREPARE stmt;
        END IF;
    END LOOP;
    CLOSE table_cursor;
END$$
DELIMITER ;

-- CALL migrate_risk_list_drop_rule_name();
-- DROP PROCEDURE IF EXISTS migrate_risk_list_drop_rule_name;
