package com.scott.payment.payment.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ChannelInfoDO
 * @date : 2026-07-12 00:00
 * @email : scott_x@163.com
 * @description : 收单渠道基础信息只读实体，位于 service-payment 数据访问层，用于交易路由读取渠道启停、请求地址和超时配置。
 * @status : create
 */
@Data
@TableName("channel_info")
public class ChannelInfoDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String channelCode;

    private Integer channelStatus;

    private Integer supportAcquiring;

    private String defaultRequestUrl;

    private Integer connectTimeoutSeconds;

    private Integer readTimeoutSeconds;

    private LocalDateTime updateTime;

    private Long deleted;
}
