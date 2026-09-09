package com.scott.payment.openapi.application.checkout;

import com.scott.payment.openapi.dto.body.HostedCheckoutBrowserRequestDTOs;
import com.scott.payment.openapi.dto.body.HostedCheckoutSessionCreateRequestDTO;
import com.scott.payment.openapi.service.HostedCheckoutService;
import com.scott.payment.openapi.vo.checkout.HostedCheckoutPaymentResultVO;
import com.scott.payment.openapi.vo.checkout.HostedCheckoutSessionCreateVO;
import com.scott.payment.openapi.vo.checkout.HostedCheckoutSessionVO;
import com.scott.payment.openapi.vo.checkout.HostedCheckoutCardBinVO;
import org.springframework.stereotype.Service;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : OpenApiHostedCheckoutApplicationService
 * @date : 2026-09-02 08:03
 * @email : scott_x@163.com
 * @description : Hosted Checkout 应用服务。
 * @status : create
 */
@Service
public class OpenApiHostedCheckoutApplicationService {

    /**
     * Hosted Checkout 领域服务入口。
     */
    private final HostedCheckoutService hostedCheckoutService;

    /**
     * 创建 Hosted Checkout 应用编排服务。
     *
     * @param hostedCheckoutService Hosted Checkout 领域服务
     */
    public OpenApiHostedCheckoutApplicationService(HostedCheckoutService hostedCheckoutService) {
        this.hostedCheckoutService = hostedCheckoutService;
    }

    /**
     * 编排商户创建 Hosted Checkout 会话。
     *
     * @param encryptedData 商户原始密文，仅用于请求指纹
     * @param requestDTO    解密并校验后的会话创建请求
     * @return 会话创建结果
     */
    public HostedCheckoutSessionCreateVO createSession(String encryptedData,
                                                       HostedCheckoutSessionCreateRequestDTO requestDTO) {
        return hostedCheckoutService.createSession(encryptedData, requestDTO);
    }

    /**
     * 编排付款人查询会话展示快照。
     *
     * @param requestDTO 会话查询请求
     * @return 可公开展示的会话快照
     */
    public HostedCheckoutSessionVO querySession(HostedCheckoutBrowserRequestDTOs.SessionQueryRequest requestDTO) {
        return hostedCheckoutService.querySession(requestDTO);
    }

    /**
     * 编排付款人提交支付，敏感卡数据仅向支付核心透传。
     *
     * @param requestDTO 支付提交请求
     * @return 当前支付结果或后续动作
     */
    public HostedCheckoutPaymentResultVO submitPayment(
            HostedCheckoutBrowserRequestDTOs.PaymentSubmitRequest requestDTO) {
        return hostedCheckoutService.submitPayment(requestDTO);
    }

    /**
     * 编排付款人查询支付尝试状态。
     *
     * @param requestDTO 支付状态查询请求
     * @return 支付核心当前状态
     */
    public HostedCheckoutPaymentResultVO queryPaymentStatus(
            HostedCheckoutBrowserRequestDTOs.PaymentStatusRequest requestDTO) {
        return hostedCheckoutService.queryPaymentStatus(requestDTO);
    }

    /**
     * 编排 3DS 回跳处理，不在应用层直接认定交易终态。
     *
     * @param requestDTO 3DS 回跳请求
     * @return 支付核心处理后的当前状态
     */
    public HostedCheckoutPaymentResultVO handleThreeDsReturn(
            HostedCheckoutBrowserRequestDTOs.ThreeDsReturnRequest requestDTO) {
        return hostedCheckoutService.handleThreeDsReturn(requestDTO);
    }

    /**
     * 解析卡号段（BIN）
     *
     * @param requestDTO 卡Bin信息
     * @return 卡品牌信息
     */
    public HostedCheckoutCardBinVO resolveCardBin(HostedCheckoutBrowserRequestDTOs.CardBinRequest requestDTO) {
        return hostedCheckoutService.resolveCardBin(requestDTO);
    }
}
