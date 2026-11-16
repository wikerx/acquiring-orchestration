-- 支付路由配置中心回滚脚本。
-- 为避免丢失已维护配置，四张表任意一张存在数据时会终止；请先导出并清空后再执行。

DROP PROCEDURE IF EXISTS rollback_payment_route_admin;
DELIMITER $$
CREATE PROCEDURE rollback_payment_route_admin()
BEGIN
    DECLARE config_count BIGINT DEFAULT 0;

    -- 每张表独立判断，保证迁移只执行一部分时回滚脚本仍可安全运行。
    IF EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_schema = DATABASE() AND table_name = 'payment_route_rule'
    ) THEN
        SELECT config_count + COUNT(*) INTO config_count FROM payment_route_rule;
    END IF;
    IF EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_schema = DATABASE() AND table_name = 'payment_route_method'
    ) THEN
        SELECT config_count + COUNT(*) INTO config_count FROM payment_route_method;
    END IF;
    IF EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_schema = DATABASE() AND table_name = 'payment_route_transaction_type'
    ) THEN
        SELECT config_count + COUNT(*) INTO config_count FROM payment_route_transaction_type;
    END IF;
    IF EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_schema = DATABASE() AND table_name = 'payment_route_strategy'
    ) THEN
        SELECT config_count + COUNT(*) INTO config_count FROM payment_route_strategy;
    END IF;
    IF EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_schema = DATABASE() AND table_name = 'payment_route_candidate'
    ) THEN
        SELECT config_count + COUNT(*) INTO config_count FROM payment_route_candidate;
    END IF;
    IF EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_schema = DATABASE() AND table_name = 'payment_route_change_log'
    ) THEN
        SELECT config_count + COUNT(*) INTO config_count FROM payment_route_change_log;
    END IF;

    IF config_count > 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'payment route configuration exists; export and clear it before rollback';
    END IF;

    DELETE grant_record
    FROM sys_role_permission grant_record
    JOIN sys_permission permission ON permission.id = grant_record.permission_id
    WHERE permission.app_id = 1 AND permission.permission_code LIKE 'channel:route:%';

    DELETE grant_record
    FROM sys_role_menu grant_record
    JOIN sys_menu menu ON menu.id = grant_record.menu_id
    WHERE menu.app_id = 1
      AND (menu.menu_code = 'admin_payment_route_v1' OR menu.menu_code LIKE 'admin_payment_route_%_v1');

    DELETE FROM sys_permission
    WHERE app_id = 1 AND permission_code LIKE 'channel:route:%';

    DELETE FROM sys_menu
    WHERE app_id = 1
      AND (menu_code = 'admin_payment_route_v1' OR menu_code LIKE 'admin_payment_route_%_v1');

    UPDATE sys_menu
    SET sort_no = CASE menu_code
        WHEN 'admin_channel_alert_rule_v1' THEN 46
        WHEN 'admin_channel_alert_event_v1' THEN 47
    END
    WHERE app_id = 1
      AND menu_code IN ('admin_channel_alert_rule_v1', 'admin_channel_alert_event_v1')
      AND deleted = 0;

    DROP TABLE IF EXISTS payment_route_change_log;
    DROP TABLE IF EXISTS payment_route_candidate;
    DROP TABLE IF EXISTS payment_route_strategy;
    DROP TABLE IF EXISTS payment_route_transaction_type;
    DROP TABLE IF EXISTS payment_route_method;
    DROP TABLE IF EXISTS payment_route_rule;
END$$
DELIMITER ;

CALL rollback_payment_route_admin();
DROP PROCEDURE rollback_payment_route_admin;
