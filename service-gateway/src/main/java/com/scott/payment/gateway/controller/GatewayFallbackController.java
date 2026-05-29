package com.scott.payment.gateway.controller;

import com.scott.payment.component.core.enums.ApiCoResultEnum;
import com.scott.payment.component.core.model.CommonResult;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : GatewayFallbackController
 * @date : 2026-05-29 23:35
 * @email : scott_x@163.com
 * @description : 网关兜底响应控制器，用于统一处理未命中路由的非法请求
 * @status : create
 */
@RestController
public class GatewayFallbackController {

    /**
     * 返回未命中网关白名单路由的统一 JSON 响应。
     * <p>
     * gateway 是系统最外层入口，非法路径不继续转发到业务服务，直接返回稳定的对外错误码。
     *
     * @param exchange WebFlux 请求上下文，后续可从这里补充 traceId、客户端 IP 等审计信息
     * @return 对外 API 统一错误响应
     */
    @RequestMapping("/gateway/fallback/not-found")
    public CommonResult<Void> notFound(ServerWebExchange exchange) {
        return CommonResult.error(ApiCoResultEnum.CO_NOT_FOUND);
    }
}
