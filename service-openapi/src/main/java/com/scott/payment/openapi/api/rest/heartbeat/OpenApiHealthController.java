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
 * @description : OpenApiHealthController HTTP 接口控制器，用于接收请求、调用应用服务并返回统一响应，位于 商户开放接口服务层，输入输出边界由所在包和公开方法契约限定。
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
