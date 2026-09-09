package com.scott.payment.settlement.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SettlementReviewCandidateDO
 * @date : 2026-09-01 00:00
 * @email : scott_x@163.com
 * @description : 预审单与候选的不可删除关系快照；冻结候选版本和清分指纹，批准时原子消费，拒绝、取消或过期时原子释放。
 * @status : create
 */
@Data
@TableName("settlement_review_candidate")
public class SettlementReviewCandidateDO {
    /** 关系数据库主键，插入前允许为空。 */
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 稳定预审候选关系号，数据库必须唯一。 */
    private String reviewCandidateNo;
    /** 所属结算预审单号。 */
    private String reviewOrderNo;
    /** 被独占锁定的结算候选主键。 */
    private Long candidateId;
    /** 提交时冻结的候选业务号。 */
    private String candidateNo;
    /** CLEARING_REVISION、RESERVE_RELEASE 或 ADJUSTMENT。 */
    private String sourceType;
    /** 来源清分/保证金业务标识。 */
    private String sourceBusinessId;
    /** 来源清分修订号；非交易候选允许为空。 */
    private Integer sourceRevision;
    /** 来源交易号；纯保证金候选允许为空。 */
    private String sourceTransactionId;
    /** 来源交易分片时间；纯保证金候选允许为空。 */
    private LocalDateTime sourceTransactionDateTime;
    /** 锁定成功后的候选版本，审批时必须一致。 */
    private Long lockedCandidateVersion;
    /** 提交时清分事实 SHA-256 指纹。 */
    private String clearingFingerprint;
    /** LOCKED、CONSUMED 或 RELEASED。 */
    private String relationStatus;
    /** 候选被预审独占锁定时间。 */
    private LocalDateTime lockedTime;
    /** 批准后候选转入正式批次时间；其他状态为空。 */
    private LocalDateTime consumedTime;
    /** 拒绝、取消或过期后释放时间；其他状态为空。 */
    private LocalDateTime releasedTime;
    /** 关系状态 CAS 版本。 */
    private Long version;
    /** 关系创建时间，数据库精度为毫秒。 */
    private LocalDateTime createTime;
    /** 关系最近更新时间，数据库精度为毫秒。 */
    private LocalDateTime updateTime;
}
