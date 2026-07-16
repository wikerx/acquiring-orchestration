package com.scott.payment.payment.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : TransactionOperationDO
 * @date : 2026-07-14 17:35
 * @email : scott_x@163.com
 * @description : 交易动作单实体，位于 service-payment 持久化层，保存授权、请款、退款、撤销等每一次交易动作的状态和渠道摘要。
 * @status : create
 */
@Data
@TableName("transaction_operation")
public class TransactionOperationDO implements Serializable {

    /**
     * 序列化版本号，用于本地缓存、测试和后续补偿场景的对象兼容。
     */
    private static final long serialVersionUID = 1L;

    /**
     * 物理表主键 ID。
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 平台内部生命周期关联标识，同一原始交易生命周期内保持不变，不返回商户。
     */
    private String operationId;

    /**
     * 平台当前交易唯一标识，每一笔授权、请款、退款、撤销都不同。
     */
    private String transactionId;

    /**
     * 源平台交易 ID，退款、请款、撤销等后续动作关联原交易时使用。
     */
    private String sourceTransactionId;

    /**
     * 平台商户号。
     */
    private String merchantId;

    /**
     * 商户订单号。
     */
    private String merchantOrderNo;

    /**
     * 商户本次 API 请求唯一标识，来自 orderInfo.orderId。
     */
    private String merchantOrderId;

    /**
     * 生命周期内动作序号，从 1 递增。
     */
    private Integer operationSequence;

    /**
     * 交易类型，对齐字典 transaction_type。
     */
    private String transactionType;

    /**
     * 当前动作交易状态，对齐字典 transaction_status。
     */
    private String transactionStatus;

    /**
     * 当前内部处理阶段。
     */
    private String processStage;

    /**
     * 挂起原因码，仅 PENDING 状态使用。
     */
    private String pendingReasonCode;

    /**
     * 后台可见失败原因码，仅 FAILED 状态使用。
     */
    private String failReasonCode;

    /**
     * 后台可见失败原因描述，优先保存渠道真实响应摘要。
     */
    private String failReasonMessage;

    /**
     * 商户上送或页面展示的原始交易币种。
     */
    private String labelCurrency;

    /**
     * 商户上送或页面展示的原始交易金额，主币种单位。
     */
    private BigDecimal labelAmount;

    /**
     * 系统交易币种。
     */
    private String transactionCurrency;

    /**
     * 系统交易金额，主币种单位。
     */
    private BigDecimal transactionAmount;

    /**
     * 渠道批准或最终成功币种。
     */
    private String approvedCurrency;

    /**
     * 渠道批准或最终成功金额，主币种单位。
     */
    private BigDecimal approvedAmount;

    /**
     * 上送渠道币种。
     */
    private String channelRequestCurrency;

    /**
     * 上送渠道金额，主币种单位。
     */
    private BigDecimal channelRequestAmount;

    /**
     * 动作预计或最终结算币种。
     */
    private String settlementCurrency;

    /**
     * 动作预计或最终结算金额。
     */
    private BigDecimal settlementAmount;

    /**
     * 交易币种默认小数位精度。
     */
    private Integer currencyExponent;

    /**
     * 当前动作是否启用 DCC，0 否、1 是。
     */
    private Integer dccEnabled;

    /**
     * 当前动作是否启用 EDC，0 否、1 是。
     */
    private Integer edcEnabled;

    /**
     * 标签金额转交易金额使用的汇率；未换汇时为空。
     */
    private BigDecimal transactionRate;

    /**
     * 渠道编码。
     */
    private String channelCode;

    /**
     * 渠道信息 ID。
     */
    private Long channelId;

    /**
     * 渠道 MID 配置 ID。
     */
    private Long channelMidConfigId;

    /**
     * 渠道终端号或子 MID；当前从路由结果中暂不填充。
     */
    private String channelTerminalId;

    /**
     * 渠道订单号。
     */
    private String channelOrderNo;

    /**
     * 渠道交易 ID；渠道统一响应暂未提供时可为空。
     */
    private String channelTransactionId;

    /**
     * 渠道原始交易状态。
     */
    private String channelStatus;

    /**
     * 渠道响应码，如 MPGS response.acquirerCode。
     */
    private String channelResponseCode;

    /**
     * 渠道响应摘要。
     */
    private String channelResponseMessage;

    /**
     * 授权码。
     */
    private String authCode;

    /**
     * 检索参考号或渠道参考号。
     */
    private String rrn;

    /**
     * 收单机构参考号，用于对账和争议。
     */
    private String acquirerReferenceNo;

    /**
     * 当前动作结算状态。
     */
    private String settlementStatus;

    /**
     * 当前动作对账状态。
     */
    private String reconciliationStatus;

    /**
     * 当前动作入账状态。
     */
    private String accountingStatus;

    /**
     * 当前动作渠道结果勾兑/查询确认状态。
     */
    private String channelMatchStatus;

    /**
     * 最近一次渠道查询确认结果。
     */
    private String channelMatchResult;

    /**
     * 当前动作渠道查询确认次数。
     */
    private Integer channelMatchCount;

    /**
     * 最近一次渠道查询确认请求 ID。
     */
    private String lastChannelMatchRequestId;

    /**
     * 最近一次渠道查询确认时间。
     */
    private LocalDateTime lastChannelMatchTime;

    /**
     * 下一次渠道查询确认计划时间。
     */
    private LocalDateTime nextChannelMatchTime;

    /**
     * 最近一次渠道查询确认失败原因。
     */
    private String channelMatchFailReason;

    /**
     * 交易业务时间，所有交易分表统一字段。
     */
    private LocalDateTime transactionDateTime;

    /**
     * 交易业务时间对应 UTC 时间。
     */
    private LocalDateTime transactionUtcTime;

    /**
     * 交易业务时间所属 IANA 时区。
     */
    private String transactionTimeZone;

    /**
     * 动作受理时间。
     */
    private LocalDateTime operationTime;

    /**
     * 动作完成时间，仅终态交易填写。
     */
    private LocalDateTime completeTime;

    /**
     * 乐观锁版本号。
     */
    private Integer version;

    /**
     * 软删除标识，0 表示未删除。
     */
    private Integer deleted;

    /**
     * 记录创建时间。
     */
    private LocalDateTime createTime;

    /**
     * 记录最后更新时间。
     */
    private LocalDateTime updateTime;

}
