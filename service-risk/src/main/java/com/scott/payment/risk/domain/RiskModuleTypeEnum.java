package com.scott.payment.risk.domain;

import lombok.Getter;

/**
 * 运行时风控模块类型。
 */
@Getter
public enum RiskModuleTypeEnum {

    /**
     * 白名单模块，命中后按配置降低或豁免后续风险动作。
     */
    WHITE("WHITE"),

    /**
     * 黑名单模块，匹配已确认的禁止或高风险元素。
     */
    BLACK("BLACK"),

    /**
     * 反洗钱模块，承载 AML 名单和规则命中。
     */
    AML("AML"),

    /**
     * 通用规则模块，承载频率、金额等可配置策略。
     */
    RULE("RULE"),

    /**
     * 系统兜底模块，用于 Redis/数据库异常等不可绕过的安全决策。
     */
    SYSTEM("SYSTEM");

    /**
     * 持久化和接口使用的稳定模块编码。
     */
    private final String code;

    RiskModuleTypeEnum(String code) {
        this.code = code;
    }
}
