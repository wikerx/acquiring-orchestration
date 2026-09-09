package com.scott.payment.settlement.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SettlementRateQuoteDO
 * @date : 2026-08-26 22:40
 * @email : scott_x@163.com
 * @description : 有效业务汇率与启用来源配置的批量只读投影，保留来源排序和报价方向判定所需事实。
 * @status : create
 */
@Data
public class SettlementRateQuoteDO {
    /** exchange_business_rate 主键，用作批次 quote_id。 */
    private Long id;
    /** 启用汇率来源编码。 */
    private String sourceCode;
    /** 数据库报价基准币种。 */
    private String baseCurrency;
    /** 数据库报价目标币种。 */
    private String quoteCurrency;
    /** 当前有效最终业务汇率。 */
    private BigDecimal finalRate;
    /** 报价生效时间。 */
    private LocalDateTime effectiveTime;
    /** 1 表示默认来源。 */
    private Integer defaultSource;
    /** 来源优先级，数值越小越优先。 */
    private Integer sourcePriority;
}
