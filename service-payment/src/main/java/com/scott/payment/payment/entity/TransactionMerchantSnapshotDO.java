package com.scott.payment.payment.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : TransactionMerchantSnapshotDO
 * @date : 2026-08-14 16:00
 * @email : scott_x@163.com
 * @description : 交易商户快照实体，按交易动作保存子商户 JSON 以及渠道、费用版本和路由等冻结配置。
 * @status : create
 */
@Data
@TableName("transaction_merchant_snapshot")
public class TransactionMerchantSnapshotDO implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;
    private String snapshotId;
    private String transactionId;
    private String operationId;
    private String merchantId;
    private String subMerchantInfoJson;
    private String merchantName;
    private String merchantCountry;
    private String merchantCategoryCode;
    private String merchantStatus;
    private Long channelId;
    private String channelCode;
    private Long channelMidConfigId;
    private String channelMerchantId;
    private String terminalId;
    private String channelMidMetadataJson;
    private String settlementConfigSnapshotJson;
    private String feeConfigSnapshotJson;
    private String internalRiskConfigSnapshotJson;
    private String routeConfigSnapshotJson;
    private Long feePlanId;
    private Long feePlanVersionId;
    private Integer feePlanVersionNo;
    private String feeSnapshotHash;
    private LocalDateTime feeSnapshotTime;
    private LocalDateTime transactionDateTime;
    private LocalDateTime transactionUtcTime;
    private String transactionTimeZone;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
