package com.scott.payment.payment.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 退款审批工作队列实体，位于支付持久化层；该普通表只保存审批事实和三个真实分片时间，不作为退款金额事实源。
 */
@Data
@TableName("transaction_refund_approval")
public class TransactionRefundApprovalDO implements Serializable {

    private static final long serialVersionUID = 1L;


    @TableId(type = IdType.AUTO)
    private Long id;

    private String approvalId;
    /**
     * 平台交易号，由支付核心生成，用于串联主单、动作单、渠道请求、回调和通知。
     */
    private String refundTransactionId;
    /**
     * 持久化的退款交易日期时间，用于还原当前记录的业务事实。
     */
    private LocalDateTime refundTransactionDateTime;
    /**
     * 原平台交易号，用于将请款、退款、撤销、增量授权等后续动作关联到原始交易。
     */
    private String sourceTransactionId;
    /**
     * 持久化的来源交易日期时间，用于还原当前记录的业务事实。
     */
    private LocalDateTime sourceTransactionDateTime;
    /**
     * 持久化的{@code rootTransactionDateTime}，用于还原当前记录的业务事实。
     */
    private LocalDateTime rootTransactionDateTime;
    /**
     * 商户号，用于限定商户配置、交易数据、风控规则和权限归属。
     */
    private String merchantId;

    private String approvalStatus;

    private String approvalPolicyCode;
    /**
     * 持久化的审批策略快照，用于还原当前记录的业务事实。
     */
    private String approvalPolicySnapshot;
    /**
     * 持久化的当前审批等级，用于还原当前记录的业务事实。
     */
    private Integer currentApprovalLevel;

    private Integer totalApprovalLevels;
    /**
     * {@code applicantType}，用于区分 {@code TransactionRefundApprovalDO} 记录的处理类别、配置维度或外部协议枚举。
     */
    private String applicantType;

    private String applicantId;

    private String applicantName;

    private String approvalOperatorId;

    private String approvalOperatorName;
    /**
     * 持久化的审批时间，用于还原当前记录的业务事实。
     */
    private LocalDateTime approvalTime;
    /**
     * 持久化的审批原因，用于还原当前记录的业务事实。
     */
    private String approvalReason;
    /**
     * 业务配置、令牌或缓存条目的失效时刻。
     */
    private LocalDateTime expireTime;

    private String decisionRequestId;

    private String executionEventId;

    private Integer version;
    /**
     * 记录创建时刻，持久化精度为毫秒。
     */
    private LocalDateTime createTime;
    /**
     * 记录最后更新时间，持久化精度为毫秒。
     */
    private LocalDateTime updateTime;
}
