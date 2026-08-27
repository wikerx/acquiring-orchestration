package com.scott.payment.settlement.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SettlementCandidateDO
 * @date : 2026-08-26 20:00
 * @email : scott_x@163.com
 * @description : 清分修订级结算候选实体；只保存路由及结算归属，不复制清分金额或汇率事实。
 * @status : create
 */
@Data
@TableName("settlement_candidate")
public class SettlementCandidateDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 候选稳定业务号。 */
    private String candidateNo;
    /** CLEARING_REVISION、RESERVE_RELEASE 或 ADJUSTMENT。 */
    private String sourceType;
    /** 清分或调整来源业务 ID。 */
    private String sourceBusinessId;
    /** 来源清分修订号或版本。 */
    private Integer sourceRevision;
    /** 来源动作交易号，可为空。 */
    private String sourceTransactionId;
    /** 来源动作季度精确路由时间，可为空。 */
    private LocalDateTime sourceTransactionDateTime;
    /** 平台商户号。 */
    private String merchantId;
    /** 冻结结算配置 ID；影子候选为空。 */
    private Long settlementProfileId;
    /** 目标结算 ISO 币种。 */
    private String targetCurrency;
    /** 目标币种 ISO 小数位。 */
    private Integer targetCurrencyExponent;
    /** 最早允许认领业务日期。 */
    private LocalDate settlementEligibleDate;
    /** 候选权威状态。 */
    private String candidateStatus;
    /** 1 为影子候选，0 为经审批真实候选。 */
    private Integer shadowMode;
    /** 当前独占候选的结算批次号。 */
    private String settlementBatchNo;
    /** 最近认领时间。 */
    private LocalDateTime claimedTime;
    /** 资金入账完成时间。 */
    private LocalDateTime postedTime;
    /** 认领状态 CAS 版本。 */
    private Long version;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
