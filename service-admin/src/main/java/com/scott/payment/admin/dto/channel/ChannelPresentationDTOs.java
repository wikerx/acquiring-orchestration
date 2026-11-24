package com.scott.payment.admin.dto.channel;

import java.util.List;

/** 仅用于已授权页面的渠道身份展示，不包含配置、MID 或存储地址。 */
public final class ChannelPresentationDTOs {
    private ChannelPresentationDTOs() { }

    public enum Scope {
        /** 渠道基础资料、支付能力及限额页面使用各自权限前缀。 */
        INFO("channel:info"), CAPABILITY("channel:capability"), LIMIT("channel:limit"),
        /** 渠道 MID、商户绑定及路由页面使用各自权限前缀。 */
        MID("channel:mid"), MID_BINDING("channel:mid-binding"), ROUTE("channel:route"),
        /** 渠道预警规则及事件页面使用各自权限前缀。 */
        ALERT_RULE("channel:alert-rule"), ALERT_EVENT("channel:alert-event"),
        /** 交易订单及操作页面使用各自权限前缀，不复用渠道编辑权限。 */
        ORDER("transaction:order"), OPERATION("transaction:operation"),
        CHANNEL_CALLBACK("transaction:channel-callback");

        private final String permission;
        Scope(String permission) { this.permission = permission; }
        /** 将当前页面范围映射到列表权限，供轻量渠道身份投影使用。 */
        public String listPermission() { return permission + ":list"; }
        /** 将当前页面范围映射到详情权限，读取展示身份不等于读取底层配置。 */
        public String detailPermission() { return permission + ":detail"; }
    }

    public record LookupRequest(List<Long> ids, List<String> codes) { }
    public record Presentation(Long id, String channelCode, String channelCnName,
                               String channelEnName, String logoVersion) { }
}
