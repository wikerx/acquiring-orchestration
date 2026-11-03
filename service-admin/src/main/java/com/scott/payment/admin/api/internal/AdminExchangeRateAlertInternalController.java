package com.scott.payment.admin.api.internal;

import com.scott.payment.admin.application.exchange.AdminExchangeRateVolatilityApplicationService;
import com.scott.payment.admin.dto.exchange.ExchangeRateVolatilityDTOs.AlertEvaluationRequest;
import com.scott.payment.admin.dto.exchange.ExchangeRateVolatilityDTOs.AlertEvaluationResponse;
import com.scott.payment.component.core.model.CommonResult;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.scott.payment.component.core.model.CommonResult.success;

/** service-job 调用的汇率波动幂等评估内部接口，受统一 HMAC 和防重放拦截器保护。 */
@RestController
@RequestMapping("/internal/admin/exchange/alerts")
public class AdminExchangeRateAlertInternalController {

    private final AdminExchangeRateVolatilityApplicationService applicationService;

    /** 创建仅供内部鉴权链路调用的评估命令入口。 */
    public AdminExchangeRateAlertInternalController(AdminExchangeRateVolatilityApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    /** 执行一轮由 requestKey 保护的幂等汇率波动评估。 */
    @PostMapping("/evaluate")
    public CommonResult<AlertEvaluationResponse> evaluate(@Valid @RequestBody AlertEvaluationRequest request) {
        return success(applicationService.evaluate(request));
    }
}
