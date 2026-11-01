package com.scott.payment.openapi.application;

/**
 * 开放接口请求处理门面
 */
public class OpenApiRequestFacade {

    /**
     * 开放接口请求处理门面预留入口。
     * <p>
     * 后续如果请求认证、商户基础资料校验、幂等、风控预检需要在应用层统一编排，可在这里汇总调用，
     * 控制器只保留业务语义。
     */
    public void validateMerchantRequest() {
        // 预留应用层编排点，当前由参数解析器和业务服务分别完成认证、解密和业务处理。
    }
}
