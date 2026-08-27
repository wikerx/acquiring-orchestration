package com.scott.payment.clearing.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 清分修订级结算候选；只保存路由和结算维度，不复制费用金额或汇率。 */
@Data
@TableName("settlement_candidate")
public class ClearingSettlementCandidateDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String candidateNo;
    private String sourceType;
    private String sourceBusinessId;
    private Integer sourceRevision;
    private String sourceTransactionId;
    private LocalDateTime sourceTransactionDateTime;
    private String merchantId;
    private Long settlementProfileId;
    private String targetCurrency;
    private Integer targetCurrencyExponent;
    private LocalDate settlementEligibleDate;
    private String candidateStatus;
    private Integer shadowMode;
    private String settlementBatchNo;
    private LocalDateTime claimedTime;
    private LocalDateTime postedTime;
    private Long version;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
