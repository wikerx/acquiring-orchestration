package com.scott.payment.merchant.controller;

import com.scott.payment.component.core.model.ApiResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.scott.payment.component.core.model.ApiResult.success;

/**
 * 商户服务健康检查控制器
 */
@RestController
public class MerchantHealthController {

    /**
     * 商户服务健康检查入口。
     *
     * @return 当前服务名称
     */
    @GetMapping("/merchant/health")
    public ApiResult<String> health() {
        return success("service-merchant");
    }
}
