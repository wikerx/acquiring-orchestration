package com.scott.payment.payment.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ExchangeBusinessRateDO
 * @date : 2026-07-15 00:00
 * @email : scott_x@163.com
 * @description : 业务汇率只读实体，位于 service-payment 数据访问层，用于交易 EDC 按 TRANSACTION_RATE 查询有效汇率快照。
 * @status : create
 */
@Data
@TableName("exchange_business_rate")
public class ExchangeBusinessRateDO {

    /**
     * 业务汇率主键。
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 汇率类型，如 TRANSACTION_RATE、SETTLEMENT_RATE。
     */
    private String rateType;

    /**
     * 汇率源编码，例如 BOC。
     */
    private String sourceCode;

    /**
     * 原始币种。
     */
    private String baseCurrency;

    /**
     * 目标币种。
     */
    private String quoteCurrency;

    /**
     * 最终业务汇率，必须使用 BigDecimal 保留精度。
     */
    private BigDecimal finalRate;

    /**
     * 业务汇率生效时间，数据库保留 DATETIME(3)。
     */
    private LocalDateTime effectiveTime;

    /**
     * 业务汇率失效时间，空表示长期有效。
     */
    private LocalDateTime expireTime;

    /**
     * 业务汇率状态，ENABLED 表示可用于交易。
     */
    private String rateStatus;

    /**
     * 软删除标识，0 表示未删除。
     */
    private Long deleted;
}
