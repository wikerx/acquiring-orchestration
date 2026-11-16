-- 仅回滚新版操作审计；历史 risk_trade_black_record 和真实名单表均保留。
DROP TABLE IF EXISTS risk_trade_black_batch_item;
DROP TABLE IF EXISTS risk_trade_black_batch;
