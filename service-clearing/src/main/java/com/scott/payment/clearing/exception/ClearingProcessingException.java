package com.scott.payment.clearing.exception;

import com.scott.payment.clearing.domain.state.ClearingFailureCodeEnum;

import java.util.Objects;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ClearingProcessingException
 * @date : 2026-08-26 10:30
 * @email : scott_x@163.com
 * @description : 清分编排的受控失败，携带稳定失败码供状态机决定重试、等待源交易或人工复核。
 * @status : create
 */
public class ClearingProcessingException extends RuntimeException {

    /**
     * 处理失败码，用于补偿策略、告警聚合和后台排障，不直接暴露底层异常。
     * <p>
     * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    private final ClearingFailureCodeEnum failureCode;

    /**
     * 创建不包含敏感上下文的清分失败。
     *
     * @param failureCode 稳定失败分类
     * @param message 可写入失败摘要的非敏感说明
     */
    public ClearingProcessingException(ClearingFailureCodeEnum failureCode, String message) {
        super(message);
        this.failureCode = Objects.requireNonNull(failureCode, "clearing failure code is required");
    }

    /** @return 清分状态机使用的稳定失败码 */
    public ClearingFailureCodeEnum getFailureCode() {
        return failureCode;
    }
}
