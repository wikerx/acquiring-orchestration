package com.scott.payment.payment.application;

import com.scott.payment.payment.api.internal.dto.PaymentCheckoutPaymentResultDTO;
import com.scott.payment.payment.api.internal.dto.PaymentCheckoutPaymentStatusCommandDTO;
import com.scott.payment.payment.api.internal.dto.PaymentCheckoutPaymentSubmitCommandDTO;
import com.scott.payment.payment.api.internal.dto.PaymentCheckoutSessionCreateCommandDTO;
import com.scott.payment.payment.api.internal.dto.PaymentCheckoutSessionCreateResultDTO;
import com.scott.payment.payment.api.internal.dto.PaymentCheckoutSessionQueryCommandDTO;
import com.scott.payment.payment.api.internal.dto.PaymentCheckoutSessionQueryResultDTO;
import com.scott.payment.payment.api.internal.dto.PaymentCheckoutThreeDsReturnCommandDTO;
import com.scott.payment.payment.service.PaymentCheckoutService;
import org.springframework.stereotype.Service;

/**
 * Hosted Checkout 应用编排服务。
 */
@Service
public class PaymentCheckoutApplicationService {

    private final PaymentCheckoutService paymentCheckoutService;

    public PaymentCheckoutApplicationService(PaymentCheckoutService paymentCheckoutService) {
        this.paymentCheckoutService = paymentCheckoutService;
    }

    public PaymentCheckoutSessionCreateResultDTO createSession(PaymentCheckoutSessionCreateCommandDTO commandDTO) {
        return paymentCheckoutService.createSession(commandDTO);
    }

    public PaymentCheckoutSessionQueryResultDTO querySession(PaymentCheckoutSessionQueryCommandDTO commandDTO) {
        return paymentCheckoutService.querySession(commandDTO);
    }

    public PaymentCheckoutPaymentResultDTO submitPayment(PaymentCheckoutPaymentSubmitCommandDTO commandDTO) {
        return paymentCheckoutService.submitPayment(commandDTO);
    }

    public PaymentCheckoutPaymentResultDTO queryPaymentStatus(PaymentCheckoutPaymentStatusCommandDTO commandDTO) {
        return paymentCheckoutService.queryPaymentStatus(commandDTO);
    }

    public PaymentCheckoutPaymentResultDTO handleThreeDsReturn(PaymentCheckoutThreeDsReturnCommandDTO commandDTO) {
        return paymentCheckoutService.handleThreeDsReturn(commandDTO);
    }
}
