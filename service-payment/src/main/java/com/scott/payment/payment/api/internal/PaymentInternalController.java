package com.scott.payment.payment.api.internal;

import com.scott.payment.component.core.model.CommonResult;
import com.scott.payment.payment.application.PaymentAuthorizationApplicationService;
import com.scott.payment.payment.api.internal.dto.PaymentCreateCommandDTO;
import com.scott.payment.payment.api.internal.dto.PaymentCreateResultDTO;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.scott.payment.component.core.model.CommonResult.success;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : PaymentInternalController
 * @date : 2026-05-31 21:04
 * @email : scott_x@163.com
 * @description : service-payment 内部交易接口控制器
 * @status : create
 */
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : PaymentInternalController
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 收单支付Payment Internal 管理接口，位于 service-payment 的接口层，用于承载该模块对应的业务职责和数据流转边界。
 * @status : create
 */
@RestController
@RequestMapping("/internal/payment")
public class PaymentInternalController {

    /**
     * 收单支付交易服务。
     */
    private final PaymentAuthorizationApplicationService paymentAuthorizationApplicationService;

    /**
     * 创建内部交易接口控制器。
     *
     * @param paymentAuthorizationApplicationService 收单授权交易应用服务
     */
    public PaymentInternalController(PaymentAuthorizationApplicationService paymentAuthorizationApplicationService) {
        this.paymentAuthorizationApplicationService = paymentAuthorizationApplicationService;
    }

    /**
     * 创建收单授权交易。
     *
     * @param commandDTO 创建交易命令
     * @return 创建交易结果
     */
    /**
     * 创建或保存收单支付数据，保持请求校验、默认值和审计字段一致。
     * @param commandDTO 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    @PostMapping("/authorization")
    public CommonResult<PaymentCreateResultDTO> createAuthorization(@RequestBody PaymentCreateCommandDTO commandDTO) {
        return success(paymentAuthorizationApplicationService.createAuthorization(commandDTO));
    }
}
