package com.scott.payment.channel.payment.worldpay;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : WorldPayChannelProperties
 * @date : 2026-09-02 08:03
 * @email : scott_x@163.com
 * @description : Worldpay provider 开关配置；渠道连接参数仍由路由后的 MID 快照提供。
 * @status : create
 */
@ConfigurationProperties(prefix = "payment.channel.worldpay")
public class WorldPayChannelProperties {

    private boolean enabled = true;

    /**
     * 判断 is enabled 条件是否成立，用于控制 World Pay Channel Properties 的后续分支。
     * <p>
     * 纯判断操作，不修改业务状态。
     * </p>
     * @return 条件满足时返回 true，否则返回 false
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * 写入启用标识，保持配置属性或测试夹具中的字段值与调用方输入一致。
     * @param enabled 受控开关或审批结论，不得绕过对应权限和状态校验
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
