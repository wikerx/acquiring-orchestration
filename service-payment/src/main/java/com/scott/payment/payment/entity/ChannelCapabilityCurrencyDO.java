package com.scott.payment.payment.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ChannelCapabilityCurrencyDO
 * @date : 2026-07-15 00:00
 * @email : scott_x@163.com
 * @description : 渠道能力币种只读实体，位于 service-payment 数据访问层，用于判断商户标签币种是否可直连渠道或需要 EDC。
 * @status : create
 */
@Data
@TableName("channel_capability_currency")
public class ChannelCapabilityCurrencyDO {

    /**
     * 渠道能力币种主键。
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 渠道支付能力 ID，关联 channel_payment_capability.id。
     */
    private Long capabilityId;

    /**
     * 渠道信息 ID。
     */
    private Long channelId;

    /**
     * 渠道编码。
     */
    private String channelCode;

    /**
     * ISO 4217 三位币种代码。
     */
    private String currencyCode;

    /**
     * 币种状态，1 表示启用。
     */
    private Integer currencyStatus;

    /**
     * 软删除标识，0 表示未删除。
     */
    private Long deleted;
}
