package com.scott.payment.gateway.controller;

import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.model.CommonResult;
import com.scott.payment.component.core.openapi.result.OpenApiResultCode;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;

/**
 * 网关兜底响应控制器，用于统一处理未命中路由的非法请求
 */
@RestController
public class GatewayFallbackController {

    /**
     * 返回商户 OpenAPI 未命中已发布路由时的 V1.3 响应。
     *
     * @return HTTP 404 与 A3002；不泄露 Gateway 路由表或服务发现信息
     */
    @RequestMapping("/gateway/fallback/openapi-not-found")
    public ResponseEntity<CommonResult<Void>> openApiNotFound() {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(CommonResult.error(OpenApiResultCode.API_NOT_FOUND));
    }

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
        return CommonResult.error(ApiResultEnum.NOT_FOUND);
    }
}
