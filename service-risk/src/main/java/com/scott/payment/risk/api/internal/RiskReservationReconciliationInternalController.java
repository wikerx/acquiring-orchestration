package com.scott.payment.risk.api.internal;

import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.core.model.CommonResult;
import com.scott.payment.component.web.internal.InternalServiceSignature;
import com.scott.payment.risk.domain.MerchantLimitReservationReconciliationSummary;
import com.scott.payment.risk.service.MerchantLimitReservationReconciliationService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

/**
 * 接收 Job 的有界预占复核命令，状态判断和写入仍由 Risk 所有。
 */
@RestController
@RequestMapping("/internal/risk/merchant-limit-reservations")
public class RiskReservationReconciliationInternalController {

    private final MerchantLimitReservationReconciliationService service;
    private final boolean legacyScheduler;
    private final int batchSize;

    public RiskReservationReconciliationInternalController(MerchantLimitReservationReconciliationService service,
            @Value("${risk.evaluation.reservation-reconcile-enabled:true}") boolean legacyScheduler,
            @Value("${risk.evaluation.reservation-reconcile-batch-size:100}") int batchSize) {
        this.service = service;
        this.legacyScheduler = legacyScheduler;
        this.batchSize = Math.max(1, Math.min(batchSize, 500));
    }

    /**
     * Job 与服务内调度互斥，已保护的预占按真实支付状态进行对账。
     */
    @PostMapping("/reconcile")
    public CommonResult<MerchantLimitReservationReconciliationSummary> reconcile(HttpServletRequest request) {
        if (!"service-job".equals(request.getHeader(InternalServiceSignature.HEADER_CALLER))) {
            throw new ServiceException(ApiResultEnum.FORBIDDEN);
        }
        if (legacyScheduler) {
            throw new ServiceException(ApiResultEnum.PAYMENT_CORE_CONFIG_UNAVAILABLE);
        }
        return CommonResult.success(service.reconcile(LocalDateTime.now(), batchSize));
    }
}
