package com.scott.payment.admin.dto.channel;

import java.util.List;

/** 仅用于已授权页面的渠道身份展示，不包含配置、MID 或存储地址。 */
public final class ChannelPresentationDTOs {
    private ChannelPresentationDTOs() { }

    public enum Scope {
        INFO("channel:info"), CAPABILITY("channel:capability"), LIMIT("channel:limit"),
        MID("channel:mid"), MID_BINDING("channel:mid-binding"), ROUTE("channel:route"),
        ALERT_RULE("channel:alert-rule"), ALERT_EVENT("channel:alert-event"),
        ORDER("transaction:order"), OPERATION("transaction:operation");

        private final String permission;
        Scope(String permission) { this.permission = permission; }
        public String listPermission() { return permission + ":list"; }
        public String detailPermission() { return permission + ":detail"; }
    }

    public record LookupRequest(List<Long> ids, List<String> codes) { }
    public record Presentation(Long id, String channelCode, String channelCnName,
                               String channelEnName, String logoVersion) { }
}
