package com.scott.payment.clearing.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 清分服务对非分表 transaction_locator 的只读投影，用于恢复当前、源动作和根主单的真实分片时间。
 */
@Data
@TableName("transaction_locator")
public class ClearingTransactionLocatorDO {
    /**
     * Locator 自增主键。
     */
    private Long id;
    /**
     * 当前动作交易号，全局唯一。
     */
    private String transactionId;
    /**
     * 当前动作操作号。
     */
    private String operationId;
    /**
     * 生命周期根交易号。
     */
    private String rootTransactionId;
    /**
     * 平台商户号。
     */
    private String merchantId;
    /**
     * 商户订单号，仅用于运营定位。
     */
    private String merchantOrderNo;
    /**
     * 平台统一交易类型。
     */
    private String transactionType;
    /**
     * 当前动作季度分片时间。
     */
    private LocalDateTime transactionDateTime;
    /**
     * 生命周期根主单季度分片时间。
     */
    private LocalDateTime rootTransactionDateTime;
}
