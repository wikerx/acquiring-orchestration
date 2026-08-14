package com.scott.payment.risk.api.internal;

import com.scott.payment.component.core.model.CommonResult;
import com.scott.payment.risk.api.internal.dto.MerchantLimitReservationCommandDTO;
import com.scott.payment.risk.api.internal.dto.MerchantLimitReservationCommandResultDTO;
import com.scott.payment.risk.api.internal.dto.RiskPaymentEvaluateRequestDTO;
import com.scott.payment.risk.api.internal.dto.RiskPaymentEvaluateResultDTO;
import com.scott.payment.risk.api.internal.dto.RiskThreeDsPolicyRequestDTO;
import com.scott.payment.risk.api.internal.dto.RiskThreeDsPolicyResultDTO;
import com.scott.payment.risk.application.MerchantLimitReservationApplicationService;
import com.scott.payment.risk.application.RiskEvaluationApplicationService;
import com.scott.payment.risk.application.RiskThreeDsPolicyApplicationService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.scott.payment.component.core.model.CommonResult.success;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : RiskInternalController
 * @date : 2026-07-12 00:00
 * @email : scott_x@163.com
 * @description : 风控内部服务接口，位于 service-risk 接口层，为 service-payment 提供路由前实时风控评估。
 * @status : create
 */
@RestController
@RequestMapping("/internal/risk")
public class RiskInternalController {

    /**
     * 风控评估应用服务。
     */
    private final RiskEvaluationApplicationService riskEvaluationApplicationService;

    /** 累计限额预占确认、取消和对账应用服务。 */
    private final MerchantLimitReservationApplicationService reservationApplicationService;

    /** 路由后 3DS 策略只读应用服务。 */
    private final RiskThreeDsPolicyApplicationService threeDsPolicyApplicationService;

    /**
     * 创建风控内部服务接口。
     *
     * @param riskEvaluationApplicationService 风控评估应用服务
     */
    public RiskInternalController(
            RiskEvaluationApplicationService riskEvaluationApplicationService,
            MerchantLimitReservationApplicationService reservationApplicationService,
            RiskThreeDsPolicyApplicationService threeDsPolicyApplicationService) {
        this.riskEvaluationApplicationService = riskEvaluationApplicationService;
        this.reservationApplicationService = reservationApplicationService;
        this.threeDsPolicyApplicationService = threeDsPolicyApplicationService;
    }

    /**
     * 执行收单支付路由前风控评估。
     *
     * @param requestDTO 支付风控评估请求
     * @return 风控决策结果
     */
    @PostMapping("/evaluate/payment")
    public CommonResult<RiskPaymentEvaluateResultDTO> evaluatePayment(@Valid @RequestBody RiskPaymentEvaluateRequestDTO requestDTO) {
        return success(riskEvaluationApplicationService.evaluatePayment(requestDTO));
    }

    /**
     * 在渠道路由完成后只读评估 3DS 策略，不执行累计限额或频控预占。
     *
     * @param requestDTO 已路由交易维度
     * @return 3DS 强制、跳过或未配置结果
     */
    @PostMapping("/three-ds/policy/evaluate")
    public CommonResult<RiskThreeDsPolicyResultDTO> evaluateThreeDsPolicy(
            @Valid @RequestBody RiskThreeDsPolicyRequestDTO requestDTO) {
        return success(threeDsPolicyApplicationService.evaluate(requestDTO));
    }

    /**
     * 支付本地事务失败时撤销已经创建的商户累计限额预占。
     *
     * @param commandDTO 只包含稳定交易标识和补偿原因，不包含 Redis Key
     * @return 幂等迁移统计
     */
    @PostMapping("/merchant-limit/reservations/cancel")
    public CommonResult<MerchantLimitReservationCommandResultDTO> cancelMerchantLimitReservation(
            @Valid @RequestBody MerchantLimitReservationCommandDTO commandDTO) {
        return success(reservationApplicationService.cancel(commandDTO));
    }
}
