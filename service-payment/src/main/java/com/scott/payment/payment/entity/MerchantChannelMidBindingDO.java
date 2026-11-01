package com.scott.payment.payment.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 商户渠道 MID 绑定只读实体，位于 service-payment 数据访问层，用于交易路由按商户选择可用渠道 MID。
 */
@Data
@TableName("merchant_channel_mid_binding")
public class MerchantChannelMidBindingDO {


    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 商户号，用于限定商户配置、交易数据、风控规则和权限归属。
     */
    private String merchantId;


    private Long channelId;

    /**
     * 渠道编码，用于定位 MPGS、WorldPay 等渠道适配实现和路由配置。
     */
    private String channelCode;


    private Long midConfigId;


    private String channelMid;


    private Integer bindingStatus;

    /**
     * 业务配置或汇率开始生效的具体时刻。
     */
    private LocalDateTime effectiveTime;

    /**
     * 业务配置、令牌或缓存条目的失效时刻。
     */
    private LocalDateTime expireTime;

    /**
     * 逻辑删除标识；0 表示有效，1 表示已删除，查询必须沿用统一软删除口径。
     */
    private Long deleted;
}
