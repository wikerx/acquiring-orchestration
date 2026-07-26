package com.scott.payment.payment.service.impl;

import com.scott.payment.payment.api.internal.dto.PaymentCreateCommandDTO;
import com.scott.payment.payment.client.risk.RiskInternalClient;
import com.scott.payment.payment.client.risk.dto.RiskPaymentEvaluateClientRequestDTO;
import com.scott.payment.payment.client.risk.dto.RiskPaymentEvaluateClientResponseDTO;
import com.scott.payment.payment.domain.state.PaymentRiskDecisionEnum;
import com.scott.payment.payment.service.PaymentRiskInvokeService;
import com.scott.payment.payment.service.dto.PaymentRiskDecisionDTO;
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
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
        long startNanos = System.nanoTime();
        log.info("event=PAYMENT_RISK_REQUEST_START merchantId: {} merchantOrderNo: {} transactionType: {} paymentMethod: {} currency: {} amount: {}",
                commandDTO.getMerchantId(),
                commandDTO.getMerchantOrderNo(),
                commandDTO.getTransactionType(),
                commandDTO.getPaymentMethod(),
                commandDTO.getCurrency(),
                commandDTO.getAmount());
        RiskPaymentEvaluateClientResponseDTO responseDTO = riskInternalClient.evaluatePayment(buildRequest(commandDTO));
        PaymentRiskDecisionEnum decisionEnum = PaymentRiskDecisionEnum.of(responseDTO.getDecision());
        PaymentRiskDecisionDTO decisionDTO = new PaymentRiskDecisionDTO();
        decisionDTO.setPassed(decisionEnum.isAllowProceed());
        decisionDTO.setDecision(decisionEnum.getCode());
        decisionDTO.setRiskRecordNo(responseDTO.getRiskRecordNo());
        decisionDTO.setRiskCode(responseDTO.getReasonCode());
        decisionDTO.setRiskMessage(responseDTO.getReasonMessage());
        log.info("event=PAYMENT_RISK_REQUEST_END merchantId: {} merchantOrderNo: {} transactionType: {} decision: {} passed: {} riskRecordNo: {} reasonCode: {} durationMs: {}",
                commandDTO.getMerchantId(),
                commandDTO.getMerchantOrderNo(),
                commandDTO.getTransactionType(),
                decisionDTO.getDecision(),
                decisionDTO.isPassed(),
                decisionDTO.getRiskRecordNo(),
                decisionDTO.getRiskCode(),
                elapsedMillis(startNanos));
        return decisionDTO;
    }

    private long elapsedMillis(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000L;
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

    /**
     * 执行 fill Sub Merchant Info 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * <p>
     * 层级边界：支付核心服务层；输入来源、输出结构和异常语义由 RiskPaymentRiskInvokeService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param commandDTO command DTO 输入值，含义由调用方法名称和所属业务对象限定
     * @param requestDTO 内部客户端请求 DTO，携带跨服务调用所需的交易、金额和商户维度字段
     */
    private void fillSubMerchantInfo(PaymentCreateCommandDTO commandDTO, RiskPaymentEvaluateClientRequestDTO requestDTO) {
        PaymentCreateCommandDTO.SubMerchantInfoDTO subMerchantInfoDTO = commandDTO.getSubMerchantInfo();
        if (subMerchantInfoDTO == null) {
            return;
        }
        requestDTO.setSubMerchantId(subMerchantInfoDTO.getSubId());
        requestDTO.setMerchantCategory(subMerchantInfoDTO.getMerchantCategory());
        requestDTO.setSubMerchantCountryCode(subMerchantInfoDTO.getSubCountryCode());
    }

    /**
     * 执行 fill Billing Info 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * <p>
     * 层级边界：支付核心服务层；输入来源、输出结构和异常语义由 RiskPaymentRiskInvokeService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param commandDTO command DTO 输入值，含义由调用方法名称和所属业务对象限定
     * @param requestDTO 内部客户端请求 DTO，携带跨服务调用所需的交易、金额和商户维度字段
     */
    private void fillBillingInfo(PaymentCreateCommandDTO commandDTO, RiskPaymentEvaluateClientRequestDTO requestDTO) {
        PaymentCreateCommandDTO.BillingCardHolderInfoDTO billingInfoDTO = commandDTO.getBillingCardHolderInfo();
        if (billingInfoDTO == null) {
            return;
        }
        requestDTO.setBillingCountry(billingInfoDTO.getCountry());
        requestDTO.setBillingEmail(billingInfoDTO.getEmail());
    }

    /**
     * 执行 fill Card Info 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * <p>
     * 层级边界：支付核心服务层；输入来源、输出结构和异常语义由 RiskPaymentRiskInvokeService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param commandDTO command DTO 输入值，含义由调用方法名称和所属业务对象限定
     * @param requestDTO 内部客户端请求 DTO，携带跨服务调用所需的交易、金额和商户维度字段
     */
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

    /**
     * 执行 fill Three Ds Info 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * <p>
     * 层级边界：支付核心服务层；输入来源、输出结构和异常语义由 RiskPaymentRiskInvokeService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param commandDTO command DTO 输入值，含义由调用方法名称和所属业务对象限定
     * @param requestDTO 内部客户端请求 DTO，携带跨服务调用所需的交易、金额和商户维度字段
     */
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
