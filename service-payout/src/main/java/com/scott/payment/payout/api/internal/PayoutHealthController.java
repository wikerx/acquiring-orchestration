package com.scott.payment.payout.api.internal;

import com.scott.payment.component.core.model.ApiResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 暴露代付服务进程级健康检查，不承载交易状态判断。
 */
@RestController
public class PayoutHealthController {

    /**
     * 代付核心服务健康检查入口。
     *
     * @return 当前服务名称
     */
    @GetMapping("/payout/health")
    public ApiResult<String> health() {
        return ApiResult.success("service-payout");
    }
}
