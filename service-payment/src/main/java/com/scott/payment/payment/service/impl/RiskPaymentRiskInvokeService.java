package com.scott.payment.payment.service.impl;

import com.scott.payment.payment.api.internal.dto.PaymentCreateCommandDTO;
import com.scott.payment.payment.client.risk.RiskInternalClient;
import com.scott.payment.payment.client.risk.dto.RiskPaymentEvaluateClientRequestDTO;
import com.scott.payment.payment.client.risk.dto.RiskPaymentEvaluateClientResponseDTO;
import com.scott.payment.payment.domain.state.PaymentRiskDecisionEnum;
import com.scott.payment.payment.service.PaymentRiskInvokeService;
import com.scott.payment.payment.service.dto.PaymentRiskDecisionDTO;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : RiskPaymentRiskInvokeService
 * @date : 2026-07-12 00:00
 * @email : scott_x@163.com
 * @description : service-risk 风控调用实现，位于 service-payment 服务实现层，负责把支付创建上下文转换为风控请求并映射风控决策。
 * @status : create
 */
@Service
@ConditionalOnProperty(prefix = "payment.risk-client", name = "remote-enabled", havingValue = "true")
public class RiskPaymentRiskInvokeService implements PaymentRiskInvokeService {

    /**
     * service-risk 内部客户端。
     */
    private final RiskInternalClient riskInternalClient;

    /**
     * 创建远程风控调用服务。
     *
     * @param riskInternalClient service-risk 内部客户端
     */
    public RiskPaymentRiskInvokeService(RiskInternalClient riskInternalClient) {
        this.riskInternalClient = riskInternalClient;
    }

    /**
     * 执行路由前风控检查。
     *
     * @param commandDTO 创建交易命令
     * @return 风控决策
     */
    @Override
    public PaymentRiskDecisionDTO checkPreRoute(PaymentCreateCommandDTO commandDTO) {
        RiskPaymentEvaluateClientResponseDTO responseDTO = riskInternalClient.evaluatePayment(buildRequest(commandDTO));
        PaymentRiskDecisionEnum decisionEnum = PaymentRiskDecisionEnum.of(responseDTO.getDecision());
        PaymentRiskDecisionDTO decisionDTO = new PaymentRiskDecisionDTO();
        decisionDTO.setPassed(decisionEnum.isAllowProceed());
        decisionDTO.setDecision(decisionEnum.getCode());
        decisionDTO.setRiskRecordNo(responseDTO.getRiskRecordNo());
        decisionDTO.setRiskCode(responseDTO.getReasonCode());
        decisionDTO.setRiskMessage(responseDTO.getReasonMessage());
        return decisionDTO;
    }

    private RiskPaymentEvaluateClientRequestDTO buildRequest(PaymentCreateCommandDTO commandDTO) {
        RiskPaymentEvaluateClientRequestDTO requestDTO = new RiskPaymentEvaluateClientRequestDTO();
        requestDTO.setMerchantId(commandDTO.getMerchantId());
        requestDTO.setMerchantOrderNo(commandDTO.getMerchantOrderNo());
        requestDTO.setTransactionType(commandDTO.getTransactionType());
        requestDTO.setPaymentMethod(commandDTO.getPaymentMethod());
        requestDTO.setRequestId(commandDTO.getRequestId());
        requestDTO.setAmount(commandDTO.getAmount());
        requestDTO.setCurrency(commandDTO.getCurrency());
        requestDTO.setTransactionDateTime(commandDTO.getTransactionDateTime());
        requestDTO.setRequestFingerprint(commandDTO.getRequestFingerprint());
        requestDTO.setSourceUrl(commandDTO.getSourceUrl());
        requestDTO.setPayerIp(commandDTO.getPayerIp());
        requestDTO.setUserAgent(commandDTO.getUserAgent());
        fillSubMerchantInfo(commandDTO, requestDTO);
        fillBillingInfo(commandDTO, requestDTO);
        fillCardInfo(commandDTO, requestDTO);
        fillThreeDsInfo(commandDTO, requestDTO);
        return requestDTO;
    }

    private void fillSubMerchantInfo(PaymentCreateCommandDTO commandDTO, RiskPaymentEvaluateClientRequestDTO requestDTO) {
        PaymentCreateCommandDTO.SubMerchantInfoDTO subMerchantInfoDTO = commandDTO.getSubMerchantInfo();
        if (subMerchantInfoDTO == null) {
            return;
        }
        requestDTO.setSubMerchantId(subMerchantInfoDTO.getSubId());
        requestDTO.setMerchantCategory(subMerchantInfoDTO.getMerchantCategory());
        requestDTO.setSubMerchantCountryCode(subMerchantInfoDTO.getSubCountryCode());
    }

    private void fillBillingInfo(PaymentCreateCommandDTO commandDTO, RiskPaymentEvaluateClientRequestDTO requestDTO) {
        PaymentCreateCommandDTO.BillingCardHolderInfoDTO billingInfoDTO = commandDTO.getBillingCardHolderInfo();
        if (billingInfoDTO == null) {
            return;
        }
        requestDTO.setBillingCountry(billingInfoDTO.getCountry());
        requestDTO.setBillingEmail(billingInfoDTO.getEmail());
    }

    private void fillCardInfo(PaymentCreateCommandDTO commandDTO, RiskPaymentEvaluateClientRequestDTO requestDTO) {
        PaymentCreateCommandDTO.TransactionInfoDTO transactionInfoDTO = commandDTO.getTransactionInfo();
        if (transactionInfoDTO != null) {
            requestDTO.setCardBrand(transactionInfoDTO.getCardBrand());
        }
        PaymentCreateCommandDTO.CardInfoDTO cardInfoDTO = commandDTO.getCardInfo();
        if (cardInfoDTO == null || !StringUtils.hasText(cardInfoDTO.getCardNo())) {
            return;
        }
        String normalizedCardNo = cardInfoDTO.getCardNo().replaceAll("\\D", "");
        if (normalizedCardNo.length() >= 6) {
            requestDTO.setCardBin(normalizedCardNo.substring(0, 6));
        }
        if (normalizedCardNo.length() >= 4) {
            requestDTO.setCardLast4(normalizedCardNo.substring(normalizedCardNo.length() - 4));
        }
    }

    private void fillThreeDsInfo(PaymentCreateCommandDTO commandDTO, RiskPaymentEvaluateClientRequestDTO requestDTO) {
        PaymentCreateCommandDTO.ThreeDsInfoDTO threeDsInfoDTO = commandDTO.getThreeDsInfo();
        if (threeDsInfoDTO == null) {
            return;
        }
        requestDTO.setThreeDsEci(threeDsInfoDTO.getEci());
        requestDTO.setThreeDsVersion(threeDsInfoDTO.getThreeDsVersion());
        requestDTO.setThreeDsTransactionId(threeDsInfoDTO.getDsTransactionId());
    }
}
