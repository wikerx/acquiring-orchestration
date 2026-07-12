package com.scott.payment.payment.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ChannelMidConfigDO
 * @date : 2026-07-12 00:00
 * @email : scott_x@163.com
 * @description : 渠道 MID 配置只读实体，位于 service-payment 数据访问层，用于交易路由读取真实渠道 MID、能力范围和渠道元数据。
 * @status : create
 */
@Data
@TableName("channel_mid_config")
public class ChannelMidConfigDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long channelId;

    private String channelCode;

    private String channelMid;

    private String midName;

    private String terminalId;

    private String businessType;

    private String paymentMethodScope;

    private String transactionTypeScope;

    private String currencyScope;

    private String allowedCountryScope;

    private String defaultSettlementCurrency;

    private String settlementCycle;

    private LocalTime settlementCutoffTime;

    private String settlementTimeZone;

    private String mcc;

    private String statementDescriptor;

    private String metadataValueJson;

    private Integer midStatus;

    private LocalDateTime effectiveTime;

    private LocalDateTime expireTime;

    private Long deleted;
}
