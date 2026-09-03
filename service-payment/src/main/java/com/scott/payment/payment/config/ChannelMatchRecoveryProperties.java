package com.scott.payment.payment.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ChannelMatchRecoveryProperties
 * @date : 2026-08-14 00:00
 * @email : scott_x@163.com
 * @description : 渠道勾兑恢复配置，控制原资金请求在途保护时间，避免重复调用渠道并允许历史 INIT 请求恢复查询。
 * @status : create
 */
@Data
@Component
@ConfigurationProperties(prefix = "payment.channel-match.recovery")
public class ChannelMatchRecoveryProperties {

    /** INIT 原资金请求进入主动查询前的保护秒数，负数按 0 处理。 */
    private long initRequestGraceSeconds = 300L;
}
