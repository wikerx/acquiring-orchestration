package com.scott.payment.merchant.controller;

import com.scott.payment.component.core.model.ApiResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantHealthController
 * @date : 2026-05-28 10:28
 * @email : scott_x@163.com
 * @description : 商户服务健康检查控制器
 * @status : create
 */
@RestController
public class MerchantHealthController {

    @GetMapping("/merchant/health")
    public ApiResult<String> health() {
        return ApiResult.success("service-merchant");
    }
}

