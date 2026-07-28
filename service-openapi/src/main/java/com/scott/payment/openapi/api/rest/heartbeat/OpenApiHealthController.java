package com.scott.payment.openapi.api.rest.heartbeat;

import com.scott.payment.component.core.model.ApiResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.scott.payment.component.core.model.ApiResult.success;

@RestController
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : OpenApiHealthController
 * @date : 2026-05-28 09:28
 * @email : scott_x@163.com
 * @description : Open API Health Controller 控制器，位于 商户开放接口服务，接收 HTTP 请求、提取路径和查询条件、委托应用服务处理，并返回统一响应。
 * @status : create
 */
public class OpenApiHealthController {

    /**
     * 查询 OpenAPI 服务健康状态。
     *
     * @return 服务健康标识
     */
    @GetMapping("/openapi/health")
    public ApiResult<String> health() {
        return success("service-openapi");
    }
}
