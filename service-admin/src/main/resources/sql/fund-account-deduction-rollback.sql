-- 仅回滚尚未产生业务数据的账户扣减功能；已存在扣减申请时必须先完成财务核对。
SET NAMES utf8mb4;

DROP PROCEDURE IF EXISTS `rollback_empty_fund_deduction`;
DELIMITER $$
CREATE PROCEDURE `rollback_empty_fund_deduction`()
BEGIN
    DECLARE deduction_count BIGINT DEFAULT 0;
    IF EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_schema = DATABASE() AND table_name = 'merchant_fund_deduction'
    ) THEN
        SELECT COUNT(1) INTO deduction_count FROM merchant_fund_deduction;
        IF deduction_count > 0 THEN
            SIGNAL SQLSTATE '45000'
                SET MESSAGE_TEXT = 'merchant_fund_deduction is not empty; reconcile before rollback';
        END IF;
    END IF;
END$$
DELIMITER ;

CALL `rollback_empty_fund_deduction`();
DROP PROCEDURE `rollback_empty_fund_deduction`;

START TRANSACTION;

DELETE role_permission
FROM sys_role_permission role_permission
JOIN sys_permission permission ON permission.id = role_permission.permission_id
WHERE permission.permission_code LIKE 'fund:deduction:%';

DELETE role_menu
FROM sys_role_menu role_menu
JOIN sys_menu menu ON menu.id = role_menu.menu_id
WHERE menu.menu_code LIKE 'admin_fund_deduction_%';

DELETE FROM sys_permission WHERE permission_code LIKE 'fund:deduction:%';
DELETE FROM sys_menu WHERE menu_code LIKE 'admin_fund_deduction_%';
DELETE FROM sys_dict_data
WHERE dict_type = 'fund_deduction_category'
   OR (dict_type = 'fund_ledger_business_type' AND dict_value = 'BALANCE_DEDUCTION');
DELETE FROM sys_dict_type WHERE dict_type = 'fund_deduction_category';

UPDATE sys_menu menu
JOIN sys_app app ON app.id = menu.app_id AND app.app_code = 'ADMIN' AND app.deleted = 0
SET menu.sort_no = 44
WHERE menu.menu_code = 'admin_fund_ledger_all_v1' AND menu.deleted = 0;

COMMIT;

DROP TABLE IF EXISTS merchant_fund_deduction;
