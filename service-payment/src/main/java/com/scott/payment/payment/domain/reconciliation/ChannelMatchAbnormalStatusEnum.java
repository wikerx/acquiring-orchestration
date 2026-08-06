package com.scott.payment.payment.domain.reconciliation;

import lombok.Getter;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ChannelMatchAbnormalStatusEnum
 * @date : 2026-08-06 00:00
 * @description : 渠道勾兑异常案件状态枚举，约束案件领取、复查和关闭的可审计流转。
 * @status : create
 */
@Getter
public enum ChannelMatchAbnormalStatusEnum {

    OPEN("OPEN", false),
    PROCESSING("PROCESSING", false),
    RESOLVED("RESOLVED", true),
    IGNORED("IGNORED", true);

    private final String code;
    private final boolean terminal;

    ChannelMatchAbnormalStatusEnum(String code, boolean terminal) {
        this.code = code;
        this.terminal = terminal;
    }
}
