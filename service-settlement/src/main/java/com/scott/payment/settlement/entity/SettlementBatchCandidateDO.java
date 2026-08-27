package com.scott.payment.settlement.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SettlementBatchCandidateDO
 * @date : 2026-08-26 20:00
 * @email : scott_x@163.com
 * @description : 批次认领候选的不可删除审计关系；释放后保留历史，防止跨批重复归属无法追踪。
 * @status : create
 */
@Data
@TableName("settlement_batch_candidate")
public class SettlementBatchCandidateDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 稳定批次候选关系号。 */
    private String batchCandidateNo;
    /** 所属结算批次号。 */
    private String settlementBatchNo;
    /** 清分修订级候选主键。 */
    private Long candidateId;
    /** 候选来源类型快照。 */
    private String sourceType;
    /** 候选来源业务 ID 快照。 */
    private String sourceBusinessId;
    /** 候选来源修订号快照。 */
    private Integer sourceRevision;
    /** CLAIMED、RELEASED、POSTED 或 MANUAL_REVIEW。 */
    private String relationStatus;
    /** 当前批次认领时间。 */
    private LocalDateTime claimedTime;
    /** 入账前释放时间。 */
    private LocalDateTime releasedTime;
    /** 随批次入账时间。 */
    private LocalDateTime postedTime;
    /** 关系状态 CAS 版本。 */
    private Long version;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
