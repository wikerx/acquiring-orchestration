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
 * @classname : SettlementBatchDO
 * @date : 2026-08-26 20:00
 * @email : scott_x@163.com
 * @description : 商户单账户单目标币种结算批次实体；状态变化必须经过当前状态与 version CAS。
 * @status : create
 */
@Data
@TableName("settlement_batch")
public class SettlementBatchDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    /** SByyyyMMdd-NNNNNNNN 全局业务批次号。 */
    private String settlementBatchNo;
    /** 调度或人工创建请求全局幂等键。 */
    private String createRequestKey;
    /** 结算业务日期。 */
    private LocalDate businessDate;
    /** 结算日历 IANA 时区。 */
    private String businessTimeZone;
    /** 数据库分配的当日序号。 */
    private Integer dailySequence;
    /** 平台商户号。 */
    private String merchantId;
    /** 冻结的商户结算配置 ID。 */
    private Long settlementProfileId;
    /** 目标结算资金账户 ID。 */
    private Long settlementAccountId;
    /** 目标结算 ISO 币种。 */
    private String targetCurrency;
    /** 目标币种 ISO 小数位。 */
    private Integer targetCurrencyExponent;
    /** REGULAR、RESERVE_RELEASE、REVERSAL 或 ADJUSTMENT。 */
    private String batchType;
    /** 冲正或调整引用的原批次号。 */
    private String originalBatchNo;
    /** 候选窗口闭区间起点。 */
    private LocalDateTime cutoffBeginTime;
    /** 候选窗口开区间终点。 */
    private LocalDateTime cutoffEndTime;
    /** 批次权威状态。 */
    private String batchStatus;
    /** 当前批次已独占认领候选数。 */
    private Integer candidateCount;
    /** 阶段失败重试次数。 */
    private Integer retryCount;
    /** 当前处理租约所有者。 */
    private String processingOwner;
    /** 当前处理租约截止时间。 */
    private LocalDateTime processingDeadline;
    /** 最近失败阶段。 */
    private String lastFailureStage;
    /** 最近稳定失败码。 */
    private String lastFailureCode;
    /** 最近失败摘要，不得保存敏感正文。 */
    private String lastFailureMessage;
    /** 汇率矩阵完整锁定时间。 */
    private LocalDateTime rateLockedTime;
    /** 结算结果和汇总完成时间；余额入账以 postedTime 和 POSTED 状态为准。 */
    private LocalDateTime calculatedTime;
    /** 资金和结果提交时间。 */
    private LocalDateTime postedTime;
    /** 入账前取消时间。 */
    private LocalDateTime cancelledTime;
    /** 批次状态 CAS 版本。 */
    private Long version;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
