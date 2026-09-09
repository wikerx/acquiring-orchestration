package com.scott.payment.clearing.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ClearingTransactionOperationDO
 * @date : 2026-08-26 09:12
 * @email : scott_x@163.com
 * @description : 清分服务对 transaction_operation 的只读持久化投影；所有访问必须携带 transaction_date_time。
 * @status : create
 */
@Data
@TableName("transaction_operation")
public class ClearingTransactionOperationDO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 动作表自增主键。 */
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 动作交易号。 */
    private String transactionId;
    /** 动作操作号。 */
    private String operationId;
    /** 关联源交易号；无源动作时为空。 */
    private String sourceTransactionId;
    /** 平台商户号。 */
    private String merchantId;
    /** 商户订单号，仅用于运营定位。 */
    private String merchantOrderNo;
    /** 平台统一交易类型。 */
    private String transactionType;
    /** 数据库权威动作状态。 */
    private String transactionStatus;
    /** 商户标签 ISO 币种，百分比费用和保证金使用该币种。 */
    private String labelCurrency;
    /** 商户标签金额，十进制主单位。 */
    private BigDecimal labelAmount;
    /** 渠道批准 ISO 币种；未批准时允许为空。 */
    private String approvedCurrency;
    /** 渠道批准金额，十进制主单位；未批准时允许为空。 */
    private BigDecimal approvedAmount;
    /** 实际交易 ISO 币种。 */
    private String transactionCurrency;
    /** 实际交易金额，十进制主单位。 */
    private BigDecimal transactionAmount;
    /** 标签币种 ISO exponent。 */
    private Integer currencyExponent;
    /** 动作季度分片时间。 */
    private LocalDateTime transactionDateTime;
    /** 动作 UTC 业务时间。 */
    private LocalDateTime transactionUtcTime;
    /** 动作业务 IANA 时区。 */
    private String transactionTimeZone;
    /** 动作查询投影 CAS 版本。 */
    private Integer version;
}
