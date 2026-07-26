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
     * 完成 get Merchant Key 分支的校验或转换，返回值供当前调用链继续组装结果。
     * 接口契约要求实现类保持参数校验、状态变化、异常边界和返回结构一致。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param merchantId 商户号，用于限定数据归属、幂等范围和权限边界
     * @return 当前方法计算或转换后的业务结果
     */
    String getMerchantKey(String merchantId);
}
