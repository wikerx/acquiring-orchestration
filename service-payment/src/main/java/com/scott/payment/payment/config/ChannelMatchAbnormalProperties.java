package com.scott.payment.payment.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ChannelMatchAbnormalProperties
 * @date : 2026-08-06 00:00
 * @description : 渠道勾兑异常自动升级配置；默认关闭，发布前需完成数据库和菜单权限门禁。
 * @status : create
 */
@Data
@Component
@ConfigurationProperties(prefix = "payment.channel-match.abnormal")
public class ChannelMatchAbnormalProperties {

    /** 自动升级和建案开关，默认关闭。 */
    private boolean enabled;

    /** 连续未确认达到该次数后升级为 REVIEW_REQUIRED。 */
    private int reviewRequiredThreshold = 12;

    /** 自动建案默认级别。 */
    private String defaultLevel = "HIGH";
}
