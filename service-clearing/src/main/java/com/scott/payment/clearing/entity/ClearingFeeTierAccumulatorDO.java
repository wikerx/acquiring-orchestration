package com.scott.payment.clearing.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ClearingFeeTierAccumulatorDO
 * @date : 2026-08-26 10:35
 * @email : scott_x@163.com
 * @description : 商户费用版本规则月累计事实实体；数据库行锁和唯一键是阶梯并发权威，Redis只能保存镜像。
 * @status : create
 */
@Data
@TableName("fee_tier_accumulator")
public class ClearingFeeTierAccumulatorDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String merchantId;
    private Long feePlanVersionId;
    private Long feeRuleId;
    private String periodKey;
    private Long accumulatedCount;
    private BigDecimal accumulatedAmountUsd;
    private String lastTransactionId;
    private Integer lastClearingRevision;
    private LocalDateTime lastTransactionDateTime;
    private Long version;
}
