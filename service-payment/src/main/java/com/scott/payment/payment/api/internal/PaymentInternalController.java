package com.scott.payment.payment.api.internal;

import com.scott.payment.component.core.model.CommonResult;
import com.scott.payment.payment.api.internal.dto.PaymentCreateCommandDTO;
import com.scott.payment.payment.api.internal.dto.PaymentCreateResultDTO;
import com.scott.payment.payment.service.PaymentTransactionService;
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
@RestController
@RequestMapping("/internal/payment")
public class PaymentInternalController {

    /**
     * 收单支付交易服务。
     */
    private final PaymentTransactionService paymentTransactionService;

    /**
     * 创建内部交易接口控制器。
     *
     * @param paymentTransactionService 收单支付交易服务
     */
    public PaymentInternalController(PaymentTransactionService paymentTransactionService) {
        this.paymentTransactionService = paymentTransactionService;
    }

    /**
     * 创建收单授权交易。
     *
     * @param commandDTO 创建交易命令
     * @return 创建交易结果
     */
    @PostMapping("/authorization")
    public CommonResult<PaymentCreateResultDTO> createAuthorization(@RequestBody PaymentCreateCommandDTO commandDTO) {
        return success(paymentTransactionService.createAuthorization(commandDTO));
    }
}
