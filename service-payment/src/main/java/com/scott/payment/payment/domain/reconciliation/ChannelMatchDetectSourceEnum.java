package com.scott.payment.payment.domain.reconciliation;

import lombok.Getter;

/**
 * 勾兑异常发现来源枚举，区分自动查询、回调、状态流转和人工复查。
 */
@Getter
public enum ChannelMatchDetectSourceEnum {

    /**
     * 定时任务或单笔主动查询发现异常。
     */
    AUTO_QUERY("AUTO_QUERY"),
    /**
     * 渠道回调与平台已有事实不一致时发现异常。
     */
    CALLBACK("CALLBACK"),
    /**
     * 平台状态迁移过程中检测到不合法或冲突结果。
     */
    STATUS_TRANSITION("STATUS_TRANSITION"),
    /**
     * 运营人员人工复查时创建异常案件。
     */
    MANUAL("MANUAL");

    private final String code;

    /**
     * 创建勾兑异常发现来源。
     *
     * @param code 持久化和接口使用的稳定来源编码
     */
    ChannelMatchDetectSourceEnum(String code) {
        this.code = code;
    }
}
