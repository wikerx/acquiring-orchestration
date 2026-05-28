package com.global.payment.admin.controller;

import com.global.payment.component.core.model.ApiResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AdminHealthController {

    @GetMapping("/admin/health")
    public ApiResult<String> health() {
        return ApiResult.success("service-admin");
    }
}

