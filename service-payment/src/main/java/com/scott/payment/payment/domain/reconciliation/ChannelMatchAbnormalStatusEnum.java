package com.scott.payment.payment.domain.reconciliation;

import lombok.Getter;

/**
 * 渠道勾兑异常案件状态枚举，约束案件领取、复查和关闭的可审计流转。
 */
@Getter
public enum ChannelMatchAbnormalStatusEnum {

    /**
     * 异常案件已创建，尚未被人工领取处理。
     */
    OPEN("OPEN", false),
    /**
     * 异常案件已被领取，等待复查结论。
     */
    PROCESSING("PROCESSING", false),
    /**
     * 异常已按允许的处置类型完成复核，属于不可逆终态。
     */
    RESOLVED("RESOLVED", true),
    /**
     * 异常经人工判断无需处理并关闭，属于不可逆终态。
     */
    IGNORED("IGNORED", true);

    private final String code;
    private final boolean terminal;

    /**
     * 创建勾兑异常案件状态并标记其是否为终态。
     *
     * @param code 持久化和接口使用的稳定状态编码
     * @param terminal 是否禁止案件继续迁移
     */
    ChannelMatchAbnormalStatusEnum(String code, boolean terminal) {
        this.code = code;
        this.terminal = terminal;
    }
}
