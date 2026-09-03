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
    /** 数据库自增主键，不参与阶梯累计业务唯一性。 */
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 平台商户号。 */
    private String merchantId;
    /** 动作已冻结的不可变费用方案版本 ID。 */
    private Long feePlanVersionId;
    /** 阶梯费用规则 ID。 */
    private Long feeRuleId;
    /** 月累计期间键，格式 yyyy-MM。 */
    private String periodKey;
    /** 期间内已清分动作数量。 */
    private Long accumulatedCount;
    /** 期间内已冻结的 USD 归一累计金额，十进制主单位。 */
    private BigDecimal accumulatedAmountUsd;
    /** 最近一次成功推进累计的动作交易号。 */
    private String lastTransactionId;
    /** 最近一次成功推进累计的清分修订。 */
    private Integer lastClearingRevision;
    /** 最近一次成功推进累计的动作分片时间。 */
    private LocalDateTime lastTransactionDateTime;
    /** 乐观锁版本，累计更新必须在行锁基础上再做 CAS。 */
    private Long version;
}
