package com.scott.payment.clearing.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ClearingTransactionOrderDO
 * @date : 2026-08-26 10:30
 * @email : scott_x@163.com
 * @description : 清分服务对生命周期主单的最小投影，用于退款累计事实和清分聚合状态 CAS，不修改交易状态或金额。
 * @status : create
 */
@Data
@TableName("transaction_order")
public class ClearingTransactionOrderDO {
    /** 生命周期主单自增主键。 */
    private Long id;
    /** 根操作号。 */
    private String operationId;
    /** 平台商户号。 */
    private String merchantId;
    /** 主单交易 ISO 币种。 */
    private String transactionCurrency;
    /** 主单交易金额，十进制主单位。 */
    private BigDecimal transactionAmount;
    /** 已成功退款累计金额，与 transactionCurrency 同币种。 */
    private BigDecimal refundedAmount;
    /** 生命周期清分聚合查询状态，不替代动作级财务状态。 */
    private String clearingStatus;
    /** 根主单季度分片时间。 */
    private LocalDateTime transactionDateTime;
    /** 生命周期查询投影 CAS 版本。 */
    private Integer version;
}
