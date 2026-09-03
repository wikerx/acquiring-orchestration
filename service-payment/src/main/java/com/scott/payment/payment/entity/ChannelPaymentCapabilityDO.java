package com.scott.payment.payment.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ChannelPaymentCapabilityDO
 * @date : 2026-07-15 00:00
 * @email : scott_x@163.com
 * @description : 渠道支付能力只读实体，位于 service-payment 数据访问层，用于交易路由校验渠道支付方式、交易类型和币种能力。
 * @status : create
 */
@Data
@TableName("channel_payment_capability")
public class ChannelPaymentCapabilityDO {

    /**
     * 渠道支付能力主键。
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 渠道信息 ID，关联 channel_info.id。
     */
    private Long channelId;

    /**
     * 渠道编码，如 MPGS。
     */
    private String channelCode;

    /**
     * 业务类型，如 ACQUIRING。
     */
    private String businessType;

    /**
     * 支付方式，如 BANK_CARD、APPLE_PAY。
     */
    private String paymentMethod;

    /**
     * 支持的交易类型范围，逗号分隔时按单项匹配。
     */
    private String transactionType;

    /** 渠道不直接支持请求币种时使用的默认交易币种。 */
    @TableField("default_transaction_currency")
    private String defaultTransactionCurrency;

    /** 是否支持 3DS：0 不支持，1 支持。 */
    @TableField("support_3ds")
    private Integer support3ds;

    /** 是否支持增量授权：0 不支持，1 支持。 */
    @TableField("support_incremental_authorization")
    private Integer supportIncrementalAuthorization;

    /**
     * 能力状态，1 表示启用。
     */
    private Integer capabilityStatus;

    /**
     * 路由排序值，数值越小优先级越高。
     */
    private Integer sortOrder;

    /**
     * 软删除标识，0 表示未删除。
     */
    private Long deleted;
}
