package com.scott.payment.openapi.aspect;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AuthorizationAspect
 * @date : 2026-05-28 10:28
 * @email : scott_x@163.com
 * @description : 开放接口认证处理切面预留类
 * @status : create
 */
@Slf4j
@Component
public class AuthorizationAspect {

    /**
     * 开放接口请求认证预留入口。
     * <p>
     * 当前正式链路由 {@code OpenApiRequestArgumentResolver} 完成 JWT、请求体解密、参数校验和防重放处理；
     * 该方法保留给后续 AOP 编排扩展，避免在控制器中重复认证逻辑。
     */
    public void verifyMerchantRequest() {
        log.debug("开始开放接口商户请求认证预处理");
    }
}
