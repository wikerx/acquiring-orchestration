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
     * 完成 verify 分支的校验或转换，返回值供当前调用链继续组装结果。
     * 接口契约要求实现类保持参数校验、状态变化、异常边界和返回结构一致。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param Map Map 输入值，含义由调用方法名称和所属业务对象限定
     * @param parameters parameters 输入值，含义由调用方法名称和所属业务对象限定
     * @param signature signature 输入值，含义由调用方法名称和所属业务对象限定
     * @param secret secret 输入值，含义由调用方法名称和所属业务对象限定
     * @return 当前方法计算或转换后的业务结果
     */
    boolean verify(Map<String, String> parameters, String signature, String secret);
}

