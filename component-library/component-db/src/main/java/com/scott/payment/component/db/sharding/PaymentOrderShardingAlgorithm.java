package com.scott.payment.component.db.sharding;

import com.scott.payment.component.core.enums.ApiCoResultEnum;
import com.scott.payment.component.core.exception.ServiceException;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : PaymentOrderShardingAlgorithm
 * @date : 2026-05-28 10:28
 * @email : scott_x@163.com
 * @description : 支付订单季度分表算法
 * @status : create
 */
public class PaymentOrderShardingAlgorithm {

    /**
     * 数据库统一时区，交易分表字段 transaction_date_time 按 UTC+8 计算季度。
     */
    private static final ZoneId DATABASE_ZONE_ID = ZoneId.of("Asia/Shanghai");

    /**
     * 根据交易时间计算季度分表表名。
     * <p>
     * 所有需要分表的业务表必须传入 transaction_date_time，禁止使用订单号或商户号猜测路由。
     *
     * @param logicalTableName    逻辑表名，例如 transaction、payment_order、payout_order
     * @param transactionDateTime 交易时间，数据库统一按 UTC+8 保存和路由
     * @return 物理表名，例如 transaction_2026_q2
     */
    public String tableName(String logicalTableName, LocalDateTime transactionDateTime) {
        if (logicalTableName == null || logicalTableName.trim().isEmpty()) {
            throw new ServiceException(ApiCoResultEnum.CO_REQUIRED_PARAMETER_MISSING.getCode(), "logicalTableName is required");
        }
        if (transactionDateTime == null) {
            throw new ServiceException(ApiCoResultEnum.CO_REQUIRED_PARAMETER_MISSING.getCode(), "transaction_date_time is required");
        }
        return logicalTableName + "_" + transactionDateTime.getYear() + "_q" + quarter(transactionDateTime);
    }

    /**
     * 根据 Date 类型交易时间计算季度分表表名。
     *
     * @param logicalTableName    逻辑表名
     * @param transactionDateTime 交易时间
     * @return 物理表名
     */
    public String tableName(String logicalTableName, Date transactionDateTime) {
        if (transactionDateTime == null) {
            throw new ServiceException(ApiCoResultEnum.CO_REQUIRED_PARAMETER_MISSING.getCode(), "transaction_date_time is required");
        }
        Instant instant = transactionDateTime.toInstant();
        return tableName(logicalTableName, LocalDateTime.ofInstant(instant, DATABASE_ZONE_ID));
    }

    private int quarter(LocalDateTime transactionDateTime) {
        return (transactionDateTime.getMonthValue() - 1) / 3 + 1;
    }
}
