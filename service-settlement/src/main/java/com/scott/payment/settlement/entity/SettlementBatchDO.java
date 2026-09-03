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
    /** 结算批次数据库主键，插入前允许为空。 */
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
    /** 人工预审审批通过后关联的唯一预审单号。 */
    private String reviewOrderNo;
    /** AUTO 或 MANUAL_REVIEW。 */
    private String createMode;
    /** 候选窗口闭区间起点。 */
    private LocalDateTime cutoffBeginTime;
    /** 候选窗口开区间终点。 */
    private LocalDateTime cutoffEndTime;
    /** 批次权威状态。 */
    private String batchStatus;
    /** 当前批次已独占认领候选数。 */
    private Integer candidateCount;
    /** 冻结的真实交易投影候选数。 */
    private Integer projectableCandidateCount;
    /** 人工预审冻结的结果指纹；正式计算必须一致。 */
    private String resultFingerprint;
    /** 人工预审 Maker 账户 ID；自动批次允许为空。 */
    private Long makerAccountId;
    /** Maker 账户展示名；自动批次允许为空。 */
    private String makerAccountName;
    /** Maker 提交时角色权限快照；自动批次允许为空。 */
    private String makerRoleSnapshot;
    /** Maker 客户端 IP 审计值；自动批次允许为空。 */
    private String makerClientIp;
    /** Maker 客户端 User-Agent 审计值；自动批次允许为空。 */
    private String makerUserAgent;
    /** Maker 提交原因；自动批次使用系统原因或为空。 */
    private String makerReason;
    /** Maker 实际提交时间；自动批次允许为空。 */
    private LocalDateTime makerTime;
    /** Checker 账户 ID；非预审或待复核时允许为空。 */
    private Long checkerAccountId;
    /** Checker 账户展示名；非预审或待复核时允许为空。 */
    private String checkerAccountName;
    /** Checker 决策时角色权限快照；非预审时允许为空。 */
    private String checkerRoleSnapshot;
    /** Checker 客户端 IP 审计值；非预审时允许为空。 */
    private String checkerClientIp;
    /** Checker 客户端 User-Agent 审计值；非预审时允许为空。 */
    private String checkerUserAgent;
    /** Checker 批准意见；非预审时允许为空。 */
    private String checkerComment;
    /** Checker 实际决策时间；非预审时允许为空。 */
    private LocalDateTime checkerTime;
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
    /** 批次创建时间，数据库精度为毫秒。 */
    private LocalDateTime createTime;
    /** 批次最近更新时间，数据库精度为毫秒。 */
    private LocalDateTime updateTime;
}
