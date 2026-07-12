package com.scott.payment.channel.payment.mpgs;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MpgsChannelConfig
 * @date : 2026-07-12 00:00
 * @email : scott_x@163.com
 * @description : MPGS 渠道 Spring 配置，位于 payment-channel-library 配置层，用于启用渠道属性绑定。
 * @status : create
 */
@Configuration
@EnableConfigurationProperties(MpgsChannelProperties.class)
public class MpgsChannelConfig {
}
