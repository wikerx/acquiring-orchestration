package com.scott.payment.payment.domain.reconciliation;

import lombok.Getter;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ChannelMatchDetectSourceEnum
 * @date : 2026-08-06 00:00
 * @description : 勾兑异常发现来源枚举，区分自动查询、回调、状态流转和人工复查。
 * @status : create
 */
@Getter
public enum ChannelMatchDetectSourceEnum {

    AUTO_QUERY("AUTO_QUERY"),
    CALLBACK("CALLBACK"),
    STATUS_TRANSITION("STATUS_TRANSITION"),
    MANUAL("MANUAL");

    private final String code;

    ChannelMatchDetectSourceEnum(String code) {
        this.code = code;
    }
}
