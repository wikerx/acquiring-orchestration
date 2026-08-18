package com.scott.payment.payment.application;

import com.scott.payment.payment.api.internal.dto.PaymentCheckoutPaymentResultDTO;
import com.scott.payment.payment.api.internal.dto.PaymentCheckoutPaymentStatusCommandDTO;
import com.scott.payment.payment.api.internal.dto.PaymentCheckoutPaymentSubmitCommandDTO;
import com.scott.payment.payment.api.internal.dto.PaymentCheckoutSessionCreateCommandDTO;
import com.scott.payment.payment.api.internal.dto.PaymentCheckoutSessionCreateResultDTO;
import com.scott.payment.payment.api.internal.dto.PaymentCheckoutSessionQueryCommandDTO;
import com.scott.payment.payment.api.internal.dto.PaymentCheckoutSessionQueryResultDTO;
import com.scott.payment.payment.api.internal.dto.PaymentCheckoutThreeDsReturnCommandDTO;
import com.scott.payment.payment.api.internal.dto.PaymentCheckoutCardBinCommandDTO;
import com.scott.payment.payment.api.internal.dto.PaymentCheckoutCardBinResultDTO;
import com.scott.payment.payment.service.PaymentCheckoutService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * Hosted Checkout 应用编排服务。
 */
@Service
public class PaymentCheckoutApplicationService {

    /** Hosted Checkout 领域服务，负责持久化幂等、状态机和渠道编排。 */
    private final PaymentCheckoutService paymentCheckoutService;

    /**
     * 创建支付核心 Hosted Checkout 应用服务。
     *
     * @param paymentCheckoutService Hosted Checkout 领域服务
     */
    public PaymentCheckoutApplicationService(PaymentCheckoutService paymentCheckoutService) {
        this.paymentCheckoutService = paymentCheckoutService;
    }

    /**
     * 编排创建或幂等返回 Hosted Checkout 会话。
     *
     * @param commandDTO 已通过内部签名校验的会话命令
     * @return 数据库持久化后的会话结果
     */
    public PaymentCheckoutSessionCreateResultDTO createSession(PaymentCheckoutSessionCreateCommandDTO commandDTO) {
        return paymentCheckoutService.createSession(commandDTO);
    }

    /**
     * 编排令牌摘要校验与会话展示查询。
     *
     * @param commandDTO 会话查询命令
     * @return 可公开展示的会话快照
     */
    public PaymentCheckoutSessionQueryResultDTO querySession(PaymentCheckoutSessionQueryCommandDTO commandDTO) {
        return paymentCheckoutService.querySession(commandDTO);
    }

    /**
     * 编排付款尝试提交，敏感卡数据仅在当前调用链内存中过境。
     *
     * @param commandDTO 支付提交命令
     * @return 当前支付结果或 3DS 后续动作
     */
    public PaymentCheckoutPaymentResultDTO submitPayment(PaymentCheckoutPaymentSubmitCommandDTO commandDTO) {
        return paymentCheckoutService.submitPayment(commandDTO);
    }

    /**
     * 编排查询支付核心持久化的尝试状态。
     *
     * @param commandDTO 支付状态查询命令
     * @return 当前支付状态
     */
    public PaymentCheckoutPaymentResultDTO queryPaymentStatus(PaymentCheckoutPaymentStatusCommandDTO commandDTO) {
        return paymentCheckoutService.queryPaymentStatus(commandDTO);
    }

    /**
     * 编排 3DS 回跳处理，不依据浏览器数据直接确认交易成功。
     *
     * @param commandDTO 3DS 回跳命令
     * @return 状态机处理后的当前支付状态
     */
    public PaymentCheckoutPaymentResultDTO handleThreeDsReturn(PaymentCheckoutThreeDsReturnCommandDTO commandDTO) {
        return paymentCheckoutService.handleThreeDsReturn(commandDTO);
    }

    /** 执行收银台付款期限补偿扫描。 */
    public int expireDue(LocalDateTime now, int limit) {
        return paymentCheckoutService.expireDue(now, limit);
    }

    /** 解析收银台卡 BIN 品牌及商户支持状态。 */
    public PaymentCheckoutCardBinResultDTO resolveCardBin(PaymentCheckoutCardBinCommandDTO commandDTO) {
        return paymentCheckoutService.resolveCardBin(commandDTO);
    }
}
