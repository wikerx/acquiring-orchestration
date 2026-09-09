package com.scott.payment.payment.domain.reconciliation;

import lombok.Getter;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ChannelMatchResolutionTypeEnum
 * @date : 2026-08-06 00:00
 * @email : scott_x@163.com
 * @description : 勾兑异常处置类型枚举；当前只允许自动恢复、确认无需修改和忽略，不提供人工终态修正。
 * @status : create
 */
@Getter
public enum ChannelMatchResolutionTypeEnum {

    /**
     * AUTO RECOVERED 枚举值，表示当前枚举定义中的一个受控业务取值。
     * <p>
     * 单位：无；格式：枚举常量；非敏感字段；不允许在业务状态流转中使用未声明取值。
     * </p>
     */
    AUTO_RECOVERED("AUTO_RECOVERED"),
    /**
     * NO CHANGE REQUIRED 枚举值，表示当前枚举定义中的一个受控业务取值。
     * <p>
     * 单位：无；格式：枚举常量；非敏感字段；不允许在业务状态流转中使用未声明取值。
     * </p>
     */
    NO_CHANGE_REQUIRED("NO_CHANGE_REQUIRED"),
    /**
     * IGNORED 枚举值，表示当前枚举定义中的一个受控业务取值。
     * <p>
     * 单位：无；格式：枚举常量；非敏感字段；不允许在业务状态流转中使用未声明取值。
     * </p>
     */
    IGNORED("IGNORED");

    private final String code;

    ChannelMatchResolutionTypeEnum(String code) {
        this.code = code;
    }

    /** @return 是否为当前版本允许的人工处置类型 */
    public boolean manuallyAllowed() {
        return this == NO_CHANGE_REQUIRED || this == IGNORED;
    }

    /** @return 按编码解析，无法识别时返回 null */
    public static ChannelMatchResolutionTypeEnum fromCode(String code) {
        for (ChannelMatchResolutionTypeEnum value : values()) {
            if (value.code.equals(code)) {
                return value;
            }
        }
        return null;
    }
}
