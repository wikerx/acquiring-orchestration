package com.scott.payment.payment.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantChannelMidBindingDO
 * @date : 2026-07-12 00:00
 * @email : scott_x@163.com
 * @description : 商户渠道 MID 绑定只读实体，位于 service-payment 数据访问层，用于交易路由按商户选择可用渠道 MID。
 * @status : create
 */
@Data
@TableName("merchant_channel_mid_binding")
public class MerchantChannelMidBindingDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String merchantId;

    private Long channelId;

    private String channelCode;

    private Long midConfigId;

    private String channelMid;

    private Integer bindingStatus;

    private LocalDateTime effectiveTime;

    private LocalDateTime expireTime;

    private Long deleted;
}
