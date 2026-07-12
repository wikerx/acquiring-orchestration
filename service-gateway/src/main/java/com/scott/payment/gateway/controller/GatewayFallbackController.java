package com.scott.payment.gateway.controller;

import com.scott.payment.component.core.enums.ApiResultEnum;
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
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : GatewayFallbackController
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 收单支付Gateway Fallback 管理接口，位于 service-gateway 的接口层，用于承载该模块对应的业务职责和数据流转边界。
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
    /**
     * 执行收单支付相关处理，保持当前层级的职责边界和返回语义。
     * @param exchange 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    @RequestMapping("/gateway/fallback/not-found")
    public CommonResult<Void> notFound(ServerWebExchange exchange) {
        return CommonResult.error(ApiResultEnum.NOT_FOUND);
    }
}
