package com.scott.payment.channel.payment.enums;

/**
 * 渠道回调认证姿态枚举；当前仅 REQUIRED 可进入解析流程，其余模式在安全链路完成前均失败关闭。
 */
public enum ChannelCallbackAuthentication {
    /**
     * 渠道协议必须完成签名或等价认证。
     */
    REQUIRED,
    /**
     * 渠道无签名但要求主动查询确认；当前尚未实现，注册器拒绝处理。
     */
    NONE_WITH_ACTIVE_QUERY,
    /**
     * 渠道认证协议未完成。
     */
    UNSUPPORTED
}
