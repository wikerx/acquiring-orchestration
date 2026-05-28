package com.global.payment.openapi.api;

import com.global.payment.component.core.model.ApiResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class OpenApiHealthController {

    @GetMapping("/openapi/health")
    public ApiResult<String> health() {
        return ApiResult.success("service-openapi");
    }
}

