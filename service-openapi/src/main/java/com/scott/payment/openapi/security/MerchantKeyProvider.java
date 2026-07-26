package com.scott.payment.openapi.security;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantKeyProvider
 * @date : 2026-05-28 11:42
 * @email : scott_x@163.com
 * @description : 商户密钥获取接口
 * @status : create
 */
public interface MerchantKeyProvider {

    /**
     * 完成 get Merchant Key 的本地校验、字段转换或结果组装，供当前调用链继续使用。
     * 接口契约要求实现类保持参数校验、状态变化、异常边界和返回结构一致。
     * <p>
     * 层级边界：商户开放接口服务层；输入来源、输出结构和异常语义由 MerchantKeyProvider 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param merchantId 商户号，用于限定数据归属、幂等范围和权限边界
     * @return 方法签名声明的返回值，具体结构由返回类型定义
     */
    String getMerchantKey(String merchantId);
}
