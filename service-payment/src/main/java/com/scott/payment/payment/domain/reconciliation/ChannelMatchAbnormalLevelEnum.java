package com.scott.payment.payment.domain.reconciliation;

import lombok.Getter;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ChannelMatchAbnormalLevelEnum
 * @date : 2026-08-06 00:00
 * @description : 渠道勾兑异常级别枚举，用于管理端分流案件，不参与交易状态推导。
 * @status : create
 */
@Getter
public enum ChannelMatchAbnormalLevelEnum {

    WARNING("WARNING"),
    HIGH("HIGH"),
    CRITICAL("CRITICAL");

    private final String code;

    ChannelMatchAbnormalLevelEnum(String code) {
        this.code = code;
    }
}
