package com.scott.payment.clearing.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ClearingTierPeriodReplayDO
 * @date : 2026-08-26 19:30
 * @email : scott_x@163.com
 * @description : 商户不可变费用版本月度阶梯重放控制实体；保存双人复核、稳定游标和可恢复进度，不保存汇率或余额。
 * @status : create
 */
@Data
@TableName("clearing_tier_period_replay")
public class ClearingTierPeriodReplayDO {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String replayNo;
    private String requestKey;
    private String merchantId;
    private Long feePlanId;
    private Long feePlanVersionId;
    private Long triggerFeeRuleId;
    private String periodKey;
    private LocalDateTime periodStart;
    private LocalDateTime periodEnd;
    private String reason;
    private String submitOperator;
    private String reviewOperator;
    private String reviewComment;
    private String replayStatus;
    private Integer itemCount;
    private Integer completedCount;
    private LocalDateTime lastClearingCompleteTime;
    private String lastTransactionId;
    private String lastErrorCode;
    private String lastErrorMessage;
    private Long version;
    private LocalDateTime reviewTime;
    private LocalDateTime completedTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
