package com.scott.payment.payment.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 收单渠道基础信息只读实体，位于 service-payment 数据访问层，用于交易路由读取渠道启停、请求地址和超时配置。
 */
@Data
@TableName("channel_info")
public class ChannelInfoDO {


    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 渠道编码，用于定位 MPGS、WorldPay 等渠道适配实现和路由配置。
     */
    private String channelCode;


    private Integer channelStatus;


    private Integer supportAcquiring;


    private String defaultRequestUrl;

    /**
     * 持久化的{@code connectTimeoutSeconds}，用于还原当前记录的业务事实。
     */
    private Integer connectTimeoutSeconds;

    /**
     * 持久化的读取超时秒数，用于还原当前记录的业务事实。
     */
    private Integer readTimeoutSeconds;

    /**
     * 记录最后更新时间，持久化精度为毫秒。
     */
    private LocalDateTime updateTime;

    /**
     * 逻辑删除标识；0 表示有效，1 表示已删除，查询必须沿用统一软删除口径。
     */
    private Long deleted;
}
