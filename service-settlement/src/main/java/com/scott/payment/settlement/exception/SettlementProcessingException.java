package com.scott.payment.settlement.exception;

import com.scott.payment.settlement.domain.model.SettlementFailureStage;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SettlementProcessingException
 * @date : 2026-08-26 23:10
 * @email : scott_x@163.com
 * @description : 结算批次受控失败，携带稳定错误码、失败阶段和是否允许数据库补偿重试。
 * @status : create
 */
public class SettlementProcessingException extends RuntimeException {

    /**
     * 结算失败阶段，用于确定补偿入口并防止跨阶段重复执行。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：集合元素必须沿用所属模型的主键、币种、状态和数据范围口径。
     * </p>
     */
    private final SettlementFailureStage stage;
    /**
     * 处理失败码，用于补偿策略、告警聚合和后台排障，不直接暴露底层异常。
     * <p>
     * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    private final String failureCode;
    /**
     * 失败是否允许重试；仅瞬时依赖故障可重试，业务校验和状态冲突不可重试。
     * <p>
     * 单位：无；格式：布尔值或 0/1 开关；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：仅允许平台约定的启停取值；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    private final boolean retryable;

    /**
     * 创建受控结算异常。
     *
     * @param stage 失败阶段
     * @param failureCode 稳定失败码
     * @param retryable 是否允许自动重试
     * @param message 非敏感失败摘要
     */
    public SettlementProcessingException(SettlementFailureStage stage,
                                         String failureCode,
                                         boolean retryable,
                                         String message) {
        super(message);
        this.stage = stage;
        this.failureCode = failureCode;
        this.retryable = retryable;
    }

    /** @return 失败阶段 */
    public SettlementFailureStage getStage() {
        return stage;
    }

    /** @return 稳定失败码 */
    public String getFailureCode() {
        return failureCode;
    }

    /** @return 是否允许自动重试 */
    public boolean isRetryable() {
        return retryable;
    }
}
