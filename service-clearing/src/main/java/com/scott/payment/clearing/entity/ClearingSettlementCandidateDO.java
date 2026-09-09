package com.scott.payment.clearing.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ClearingSettlementCandidateDO
 * @date : 2026-08-27 19:46
 * @email : scott_x@163.com
 * @description : 清分修订级结算候选；只保存路由和结算维度，不复制费用金额或汇率。
 * @status : update
 */
@Data
@TableName("settlement_candidate")
public class ClearingSettlementCandidateDO {
    /** 数据库自增主键，仅用于存储和游标，不作为业务幂等键。 */
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 候选业务号，全局唯一且不可为空。 */
    private String candidateNo;
    /** 来源类型：CLEARING_REVISION、RESERVE_RELEASE 或 ADJUSTMENT。 */
    private String sourceType;
    /** 来源业务号；按来源类型分别对应财务状态号、保证金状态号或调整申请号。 */
    private String sourceBusinessId;
    /** 来源事实修订号，从 1 开始。 */
    private Integer sourceRevision;
    /** 来源动作号；保证金释放和调整为独立财务动作号，不代表真实交易。 */
    private String sourceTransactionId;
    /** 来源动作季度分片时间，格式为数据库 DATETIME(3)。 */
    private LocalDateTime sourceTransactionDateTime;
    /** 平台商户号，不包含商户名称或敏感资料。 */
    private String merchantId;
    /** 结算档案主键；清分创建候选时允许为空，由结算认领校验活动档案。 */
    private Long settlementProfileId;
    /** 目标结算 ISO 4217 币种。 */
    private String targetCurrency;
    /** 目标结算币种 ISO exponent。 */
    private Integer targetCurrencyExponent;
    /** 最早可结算业务日，不包含时区时间。 */
    private LocalDate settlementEligibleDate;
    /** 候选状态；只有 READY 可被正式结算批次认领。 */
    private String candidateStatus;
    /** 影子标识，1 表示不得进入真实结算入账。 */
    private Integer shadowMode;
    /** 认领该候选的结算批次号；未认领时为空。 */
    private String settlementBatchNo;
    /** 批次认领 UTC 时间；未认领时为空。 */
    private LocalDateTime claimedTime;
    /** 资金入账 UTC 时间；未入账时为空。 */
    private LocalDateTime postedTime;
    /** 乐观锁版本，所有状态变更必须使用 CAS。 */
    private Long version;
    /** 创建 UTC 时间，数据库精度为毫秒。 */
    private LocalDateTime createTime;
    /** 最后更新 UTC 时间，数据库精度为毫秒。 */
    private LocalDateTime updateTime;
}
