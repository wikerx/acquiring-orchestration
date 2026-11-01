package com.scott.payment.clearing.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 结算候选之间不可变的资金处理先后依赖。
 *
 * <p>当前候选只有在源候选已经入账，或由结算模块确认可在同批按拓扑顺序处理时才能被认领。</p>
 */
@Data
@TableName("settlement_candidate_dependency")
public class ClearingSettlementCandidateDependencyDO {

    /**
     * 数据库自增主键。
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 受依赖约束的当前候选主键。
     */
    private Long candidateId;

    /**
     * 必须先完成资金处理的源候选主键。
     */
    private Long dependsOnCandidateId;

    /**
     * 依赖类型，取值来自 {@code SettlementCandidateDependencyType}。
     */
    private String dependencyType;

    /**
     * 依赖创建 UTC 时间，数据库精度为毫秒。
     */
    private LocalDateTime createTime;
}
