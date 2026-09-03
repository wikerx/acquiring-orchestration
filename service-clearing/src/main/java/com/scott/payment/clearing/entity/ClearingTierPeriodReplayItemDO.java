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

    /** 数据库自增主键。 */
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 所属阶梯期间重放业务号。 */
    private String replayNo;
    /** 重放内从 1 开始的稳定顺序号。 */
    private Integer sequenceNo;
    /** 动作财务状态业务号。 */
    private String financeStateId;
    /** 动作交易号。 */
    private String transactionId;
    /** 动作季度分片时间。 */
    private LocalDateTime transactionDateTime;
    /** 冻结时预期清分修订。 */
    private Integer expectedClearingRevision;
    /** 冻结时预期财务状态 CAS 版本。 */
    private Integer expectedFinanceStateVersion;
    /** 原清分完成 UTC 时间，用于全期间稳定排序。 */
    private LocalDateTime clearingCompleteTime;
    /** 动作项状态，前序未完成时不得跳序执行。 */
    private String itemStatus;
    /** 当前动作项已执行次数。 */
    private Integer attemptCount;
    /** 下一次允许重试 UTC 时间；无需重试时为空。 */
    private LocalDateTime nextRetryTime;
    /** 最近一次稳定错误码；无错误时为空。 */
    private String lastErrorCode;
    /** 最近一次非敏感错误摘要；无错误时为空。 */
    private String lastErrorMessage;
    /** 成功重放后生成的新清分修订；未完成时为空。 */
    private Integer processedRevision;
    /** 成功处理 UTC 时间；未完成时为空。 */
    private LocalDateTime processedTime;
    /** 动作项 CAS 版本。 */
    private Long version;
    /** 创建 UTC 时间。 */
    private LocalDateTime createTime;
    /** 最后更新 UTC 时间。 */
    private LocalDateTime updateTime;
}
