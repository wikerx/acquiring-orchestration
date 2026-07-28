package com.scott.payment.openapi.application.checkout;

import com.scott.payment.openapi.dto.body.HostedCheckoutBrowserRequestDTOs;
import com.scott.payment.openapi.dto.body.HostedCheckoutSessionCreateRequestDTO;
import com.scott.payment.openapi.service.HostedCheckoutService;
import com.scott.payment.openapi.vo.checkout.HostedCheckoutPaymentResultVO;
import com.scott.payment.openapi.vo.checkout.HostedCheckoutSessionCreateVO;
import com.scott.payment.openapi.vo.checkout.HostedCheckoutSessionVO;
import org.springframework.stereotype.Service;

/**
 * Hosted Checkout 应用服务。
 */
@Service
public class OpenApiHostedCheckoutApplicationService {

    private final HostedCheckoutService hostedCheckoutService;

    public OpenApiHostedCheckoutApplicationService(HostedCheckoutService hostedCheckoutService) {
        this.hostedCheckoutService = hostedCheckoutService;
    }

    public HostedCheckoutSessionCreateVO createSession(String encryptedData,
                                                       HostedCheckoutSessionCreateRequestDTO requestDTO) {
        return hostedCheckoutService.createSession(encryptedData, requestDTO);
    }

    public HostedCheckoutSessionVO querySession(HostedCheckoutBrowserRequestDTOs.SessionQueryRequest requestDTO) {
        return hostedCheckoutService.querySession(requestDTO);
    }

    public HostedCheckoutPaymentResultVO submitPayment(
            HostedCheckoutBrowserRequestDTOs.PaymentSubmitRequest requestDTO) {
        return hostedCheckoutService.submitPayment(requestDTO);
    }

    public HostedCheckoutPaymentResultVO queryPaymentStatus(
            HostedCheckoutBrowserRequestDTOs.PaymentStatusRequest requestDTO) {
        return hostedCheckoutService.queryPaymentStatus(requestDTO);
    }

    public HostedCheckoutPaymentResultVO handleThreeDsReturn(
            HostedCheckoutBrowserRequestDTOs.ThreeDsReturnRequest requestDTO) {
        return hostedCheckoutService.handleThreeDsReturn(requestDTO);
    }
}
