package com.scott.payment.channel.payment.enums;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ChannelCallbackAuthentication
 * @date : 2026-10-17 12:00
 * @email : scott_x@163.com
 * @description : 渠道回调认证姿态枚举；当前仅 REQUIRED 可进入解析流程，其余模式在安全链路完成前均失败关闭。
 * @status : create
 */
public enum ChannelCallbackAuthentication {
    /** 渠道协议必须完成签名或等价认证。 */
    REQUIRED,
    /** 渠道无签名但要求主动查询确认；当前尚未实现，注册器拒绝处理。 */
    NONE_WITH_ACTIVE_QUERY,
    /** 渠道认证协议未完成。 */
    UNSUPPORTED
}
