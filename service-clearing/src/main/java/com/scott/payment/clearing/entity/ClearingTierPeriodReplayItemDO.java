package com.scott.payment.clearing.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ClearingTierPeriodReplayItemDO
 * @date : 2026-08-26 19:30
 * @email : scott_x@163.com
 * @description : 阶梯期间重放稳定动作项；冻结原修订和状态版本，并以序号保证失败恢复后不能跳序。
 * @status : create
 */
@Data
@TableName("clearing_tier_period_replay_item")
public class ClearingTierPeriodReplayItemDO {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String replayNo;
    private Integer sequenceNo;
    private String financeStateId;
    private String transactionId;
    private LocalDateTime transactionDateTime;
    private Integer expectedClearingRevision;
    private Integer expectedFinanceStateVersion;
    private LocalDateTime clearingCompleteTime;
    private String itemStatus;
    private Integer attemptCount;
    private LocalDateTime nextRetryTime;
    private String lastErrorCode;
    private String lastErrorMessage;
    private Integer processedRevision;
    private LocalDateTime processedTime;
    private Long version;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
