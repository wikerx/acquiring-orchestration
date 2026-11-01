package com.scott.payment.payment.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 交易商户快照实体，按交易动作保存子商户 JSON 以及渠道、费用版本和路由等冻结配置。
 */
@Data
@TableName("transaction_merchant_snapshot")
public class TransactionMerchantSnapshotDO implements Serializable {

    private static final long serialVersionUID = 1L;


    @TableId(type = IdType.AUTO)
    private Long id;

    private String snapshotId;
    /**
     * 平台交易号，由支付核心生成，用于串联主单、动作单、渠道请求、回调和通知。
     */
    private String transactionId;
    /**
     * 平台操作号，由支付核心生成，用于定位一次授权、请款、退款、撤销或回调处理动作。
     */
    private String operationId;
    /**
     * 商户号，用于限定商户配置、交易数据、风控规则和权限归属。
     */
    private String merchantId;
    /**
     * 持久化的{@code subMerchantInfoJson}，用于还原当前记录的业务事实。
     */
    private String subMerchantInfoJson;

    private String merchantName;

    private String merchantCountry;

    private String merchantCategoryCode;

    private String merchantStatus;

    private Long channelId;
    /**
     * 渠道编码，用于定位 MPGS、WorldPay 等渠道适配实现和路由配置。
     */
    private String channelCode;

    private Long channelMidConfigId;
    /**
     * 商户号，用于限定商户配置、交易数据、风控规则和权限归属。
     */
    private String channelMerchantId;

    private String terminalId;

    private String channelMidMetadataJson;
    /**
     * 持久化的{@code settlementConfigSnapshotJson}，用于还原当前记录的业务事实。
     */
    private String settlementConfigSnapshotJson;
    /**
     * 持久化的{@code feeConfigSnapshotJson}，用于还原当前记录的业务事实。
     */
    private String feeConfigSnapshotJson;
    /**
     * 持久化的{@code internalRiskConfigSnapshotJson}，用于还原当前记录的业务事实。
     */
    private String internalRiskConfigSnapshotJson;
    /**
     * 持久化的{@code routeConfigSnapshotJson}，用于还原当前记录的业务事实。
     */
    private String routeConfigSnapshotJson;

    private Long feePlanId;

    private Long feePlanVersionId;

    private Integer feePlanVersionNo;

    private String feeSnapshotHash;
    /**
     * 持久化的费用快照时间，用于还原当前记录的业务事实。
     */
    private LocalDateTime feeSnapshotTime;
    /**
     * 交易受理时刻，按交易业务时区解释并保留毫秒精度。
     */
    private LocalDateTime transactionDateTime;
    /**
     * 交易受理时刻对应的 UTC 时间，用于跨时区排序和对账。
     */
    private LocalDateTime transactionUtcTime;
    /**
     * 交易业务时区，使用 IANA 时区标识解释本地交易时间。
     */
    private String transactionTimeZone;
    /**
     * 记录创建时刻，持久化精度为毫秒。
     */
    private LocalDateTime createTime;
    /**
     * 记录最后更新时间，持久化精度为毫秒。
     */
    private LocalDateTime updateTime;
}
