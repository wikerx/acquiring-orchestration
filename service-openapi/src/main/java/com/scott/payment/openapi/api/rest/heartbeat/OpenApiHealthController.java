package com.scott.payment.openapi.api.rest.heartbeat;

import com.scott.payment.component.core.model.ApiResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.scott.payment.component.core.model.ApiResult.success;

/**
 * OpenAPI 进程存活检查入口，不访问支付状态或商户安全材料。
 */
@RestController
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
