package com.sinopay.payment.openapi.api.rest.v1.heartbeat;

import com.sinopay.payment.component.core.model.ApiResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class OpenApiHealthController {

    @GetMapping("/openapi/health")
    public ApiResult<String> health() {
        return ApiResult.success("service-openapi");
    }
}
