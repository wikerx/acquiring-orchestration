package com.scott.payment.clearing.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ClearingTransactionLocatorDO
 * @date : 2026-08-26 10:30
 * @email : scott_x@163.com
 * @description : 清分服务对非分表 transaction_locator 的只读投影，用于恢复当前、源动作和根主单的真实分片时间。
 * @status : create
 */
@Data
@TableName("transaction_locator")
public class ClearingTransactionLocatorDO {
    /** Locator 自增主键。 */
    private Long id;
    /** 当前动作交易号，全局唯一。 */
    private String transactionId;
    /** 当前动作操作号。 */
    private String operationId;
    /** 生命周期根交易号。 */
    private String rootTransactionId;
    /** 平台商户号。 */
    private String merchantId;
    /** 商户订单号，仅用于运营定位。 */
    private String merchantOrderNo;
    /** 平台统一交易类型。 */
    private String transactionType;
    /** 当前动作季度分片时间。 */
    private LocalDateTime transactionDateTime;
    /** 生命周期根主单季度分片时间。 */
    private LocalDateTime rootTransactionDateTime;
}
