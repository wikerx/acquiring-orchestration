package com.scott.payment.openapi.api.rest.heartbeat;

import com.scott.payment.component.core.model.ApiResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : OpenApiHealthController
 * @date : 2026-05-28 10:28
 * @email : scott_x@163.com
 * @description : 开放接口健康检查控制器
 * @status : create
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
        return ApiResult.success("service-openapi");
    }
}
