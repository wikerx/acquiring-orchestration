package com.scott.payment.channel.payment.worldpay;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Worldpay provider 开关配置；渠道连接参数仍由路由后的 MID 快照提供。 */
@ConfigurationProperties(prefix = "payment.channel.worldpay")
public class WorldPayChannelProperties {

    private boolean enabled = true;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
