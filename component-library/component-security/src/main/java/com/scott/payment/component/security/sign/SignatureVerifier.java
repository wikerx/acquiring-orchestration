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
     * 校验verify输入，发现缺失、越权或格式错误时中断当前流程。
     * <p>
     * 前置条件：调用方传入需要在 公共组件库 内校验的参数、状态或安全材料。
     * 该方法只执行校验和规则判断，不主动写入业务状态；校验通过后由后续步骤继续处理。
     * 异常边界：缺失、越权、重复、防重放失败或格式错误时抛出当前模块约定异常。
     * </p>
     * @param Map Map 输入值，参与 map 的查询、校验、转换、写入或日志摘要
     * @param parameters parameters 输入值，参与 parameters 的查询、校验、转换、写入或日志摘要
     * @param signature signature 输入值，参与 signature 的查询、校验、转换、写入或日志摘要
     * @param secret secret 输入值，参与 secret 的查询、校验、转换、写入或日志摘要
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    boolean verify(Map<String, String> parameters, String signature, String secret);
}

