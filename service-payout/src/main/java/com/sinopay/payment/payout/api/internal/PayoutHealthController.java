package com.sinopay.payment.payout.api.internal;

import com.sinopay.payment.component.core.model.ApiResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : PayoutHealthController
 * @date : 2026-05-28 10:28
 * @email : scott_x@163.com
 * @description : 代付服务健康检查控制器
 * @status : create
 */
@RestController
public class PayoutHealthController {

    @GetMapping("/payout/health")
    public ApiResult<String> health() {
        return ApiResult.success("service-payout");
    }
}
