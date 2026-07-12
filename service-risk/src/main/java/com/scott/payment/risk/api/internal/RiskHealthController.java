package com.scott.payment.risk.api.internal;

import com.scott.payment.component.core.model.ApiResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.scott.payment.component.core.model.ApiResult.success;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : RiskHealthController
 * @date : 2026-07-12 00:00
 * @email : scott_x@163.com
 * @description : 风控服务健康检查接口，位于 service-risk 接口层，仅用于基础探针，不承载风控业务决策。
 * @status : create
 */
@RestController
public class RiskHealthController {

    /**
     * 查询风控服务健康状态。
     *
     * @return 当前服务名称
     */
    @GetMapping("/risk/health")
    public ApiResult<String> health() {
        return success("service-risk");
    }
}
