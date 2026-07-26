package com.scott.payment.component.security.replay;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : NonceValidator
 * @date : 2026-05-28 10:28
 * @email : scott_x@163.com
 * @description : 请求 Nonce 防重放校验接口
 * @status : create
 */
public interface NonceValidator {

    /**
     * 校验 validate 相关输入，发现不满足业务约束时抛出明确异常。
     * 接口契约要求实现类保持参数校验、状态变化、异常边界和返回结构一致。
     * <p>
     * 层级边界：公共组件层；输入来源、输出结构和异常语义由 NonceValidator 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param nonce nonce 输入值，含义由调用方法名称和所属业务对象限定
     * @param timestamp 时间值，使用系统约定时区或调用方传入的业务时区解释
     * @return 方法签名声明的返回值，具体结构由返回类型定义
     */
    boolean validate(String nonce, long timestamp);
}

