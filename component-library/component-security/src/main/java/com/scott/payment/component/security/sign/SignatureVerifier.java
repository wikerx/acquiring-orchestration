package com.scott.payment.component.security.sign;

import java.util.Map;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SignatureVerifier
 * @date : 2026-05-28 10:28
 * @email : scott_x@163.com
 * @description : 商户请求签名验签接口
 * @status : create
 */
public interface SignatureVerifier {

    /**
     * 完成 verify 的本地校验、字段转换或结果组装，供当前调用链继续使用。
     * 接口契约要求实现类保持参数校验、状态变化、异常边界和返回结构一致。
     * <p>
     * 层级边界：公共组件层；输入来源、输出结构和异常语义由 SignatureVerifier 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param Map Map 输入值，含义由调用方法名称和所属业务对象限定
     * @param parameters parameters 输入值，含义由调用方法名称和所属业务对象限定
     * @param signature signature 输入值，含义由调用方法名称和所属业务对象限定
     * @param secret secret 输入值，含义由调用方法名称和所属业务对象限定
     * @return 方法签名声明的返回值，具体结构由返回类型定义
     */
    boolean verify(Map<String, String> parameters, String signature, String secret);
}

