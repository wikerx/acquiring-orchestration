package com.scott.payment.payment.domain.reconciliation;

import lombok.Getter;

/**
 * 勾兑异常处置类型枚举；当前只允许自动恢复、确认无需修改和忽略，不提供人工终态修正。
 */
@Getter
public enum ChannelMatchResolutionTypeEnum {

    /**
     * 自动勾兑已经按渠道权威结果修复平台非终态，无需人工再次修改。
     */
    AUTO_RECOVERED("AUTO_RECOVERED"),

    /**
     * 人工复查确认平台事实正确，只关闭异常案件而不改变交易状态。
     */
    NO_CHANGE_REQUIRED("NO_CHANGE_REQUIRED"),

    /**
     * 经人工判断无需处理，保留原交易事实并记录忽略原因。
     */
    IGNORED("IGNORED");

    private final String code;

    /**
     * 创建勾兑异常处置类型。
     *
     * @param code 持久化和接口使用的稳定处置编码
     */
    ChannelMatchResolutionTypeEnum(String code) {
        this.code = code;
    }

    /**
     * 判断当前处置是否允许由人工接口提交。
     *
     * @return 无需修改或忽略时返回 {@code true}
     */
    public boolean manuallyAllowed() {
        return this == NO_CHANGE_REQUIRED || this == IGNORED;
    }

    /**
     * 按稳定编码解析处置类型。
     *
     * @param code 待解析的处置编码
     * @return 匹配的处置类型；无法识别时返回 {@code null}
     */
    public static ChannelMatchResolutionTypeEnum fromCode(String code) {
        for (ChannelMatchResolutionTypeEnum value : values()) {
            if (value.code.equals(code)) {
                return value;
            }
        }
        return null;
    }
}
