package com.scott.payment.settlement.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 一个逻辑预审单的有界候选处理分段及不可变来源/结果指纹。
 */
@Data
@TableName("settlement_review_segment")
public class SettlementReviewSegmentDO {

    @TableId(type = IdType.AUTO)
    private Long id;
    /**
     * 持久化的{@code segmentNo}，用于还原当前记录的业务事实。
     */
    private String segmentNo;
    /**
     * 持久化的审核订单编号，用于还原当前记录的业务事实。
     */
    private String reviewOrderNo;
    /**
     * 持久化的{@code sequenceNo}，用于还原当前记录的业务事实。
     */
    private Integer sequenceNo;

    private Long firstCandidateId;

    private Long lastCandidateId;

    private Integer candidateCount;

    private Integer projectableCandidateCount;
    /**
     * 冲正来源事实指纹，用于复核时确认原批次、净结果和资金流水未被替换。
     */
    private String sourceFingerprint;

    private String resultFingerprint;

    private BigDecimal netSignedAmount;
    /**
     * 持久化的结算批次编号，用于还原当前记录的业务事实。
     */
    private String settlementBatchNo;

    private String segmentStatus;
    /**
     * 持久化的{@code processedTime}，用于还原当前记录的业务事实。
     */
    private LocalDateTime processedTime;

    private Long version;
    /**
     * 记录创建时刻，持久化精度为毫秒。
     */
    private LocalDateTime createTime;
    /**
     * 记录最后更新时间，持久化精度为毫秒。
     */
    private LocalDateTime updateTime;
}
