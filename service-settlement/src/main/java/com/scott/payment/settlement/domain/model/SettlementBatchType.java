package com.scott.payment.settlement.domain.model;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SettlementBatchType
 * @date : 2026-08-26 20:00
 * @email : scott_x@163.com
 * @description : 结算批次业务类型；只有冲正引用原批次，保证金调整引用原 HOLD 清分事实。
 * @status : create
 */
public enum SettlementBatchType {
    /**
     * REGULAR 枚举值，表示当前枚举定义中的一个受控业务取值。
     * <p>
     * 单位：无；格式：枚举常量；非敏感字段；不允许在业务状态流转中使用未声明取值。
     * </p>
     */
    REGULAR(false),
    /**
     * RESERVE RELEASE 枚举值，表示当前枚举定义中的一个受控业务取值。
     * <p>
     * 单位：无；格式：枚举常量；非敏感字段；不允许在业务状态流转中使用未声明取值。
     * </p>
     */
    RESERVE_RELEASE(false),
    /**
     * REVERSAL 枚举值，表示当前枚举定义中的一个受控业务取值。
     * <p>
     * 单位：无；格式：枚举常量；非敏感字段；不允许在业务状态流转中使用未声明取值。
     * </p>
     */
    REVERSAL(true),
    /**
     * ADJUSTMENT 枚举值，表示当前枚举定义中的一个受控业务取值。
     * <p>
     * 单位：无；格式：枚举常量；非敏感字段；不允许在业务状态流转中使用未声明取值。
     * </p>
     */
    ADJUSTMENT(false);

    /**
     * 是否必须关联原结算批次；冲正批次为 true，普通结算批次为 false。
     * <p>
     * 单位：无；格式：布尔值或 0/1 开关；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：仅允许平台约定的启停取值；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    private final boolean originalBatchRequired;

    SettlementBatchType(boolean originalBatchRequired) {
        this.originalBatchRequired = originalBatchRequired;
    }

    /**
     * 判断当前批次类型是否必须引用原批次。
     *
     * @return 冲正批次返回 true
     */
    public boolean isOriginalBatchRequired() {
        return originalBatchRequired;
    }
}
