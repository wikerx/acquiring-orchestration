package com.scott.payment.settlement.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 一个逻辑预审单的有界候选处理分段及不可变来源/结果指纹。 */
@Data
@TableName("settlement_review_segment")
public class SettlementReviewSegmentDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String segmentNo;
    private String reviewOrderNo;
    private Integer sequenceNo;
    private Long firstCandidateId;
    private Long lastCandidateId;
    private Integer candidateCount;
    private Integer projectableCandidateCount;
    private String sourceFingerprint;
    private String resultFingerprint;
    private BigDecimal netSignedAmount;
    private String settlementBatchNo;
    private String segmentStatus;
    private LocalDateTime processedTime;
    private Long version;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
