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
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param nonce nonce 输入值，含义由调用方法名称和所属业务对象限定
     * @param timestamp 时间值，使用系统约定时区或调用方传入的业务时区解释
     * @return 当前方法计算或转换后的业务结果
     */
    boolean validate(String nonce, long timestamp);
}

