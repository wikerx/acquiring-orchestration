-- 仅用于回滚尚未被业务依赖的试算明细快照结构，执行会删除新产生的明细数据。
DROP TABLE IF EXISTS fee_simulation_record_detail;

SET @drop_fee_simulation_net_formula := (
    SELECT IF(COUNT(*) > 0,
        'ALTER TABLE fee_simulation_record DROP COLUMN net_settlement_formula_snapshot',
        'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'fee_simulation_record'
      AND column_name = 'net_settlement_formula_snapshot'
);
PREPARE stmt FROM @drop_fee_simulation_net_formula;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @drop_fee_simulation_reserve_rate := (
    SELECT IF(COUNT(*) > 0,
        'ALTER TABLE fee_simulation_record DROP COLUMN reserve_rate',
        'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'fee_simulation_record'
      AND column_name = 'reserve_rate'
);
PREPARE stmt FROM @drop_fee_simulation_reserve_rate;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @drop_fee_simulation_label_amount_usd := (
    SELECT IF(COUNT(*) > 0,
        'ALTER TABLE fee_simulation_record DROP COLUMN label_amount_usd',
        'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'fee_simulation_record'
      AND column_name = 'label_amount_usd'
);
PREPARE stmt FROM @drop_fee_simulation_label_amount_usd;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
