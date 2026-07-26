package com.scott.payment.payment.service.impl;

import com.scott.payment.component.core.trace.TraceContext;
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
        log.info("event: PAYMENT_RISK_REQUEST_START stage=RISK traceId: {} merchantId: {} merchantOrderNo: {} transactionId: {} transactionType: {} paymentMethod: {} currency: {} amount: {} payerIp: {} sourceUrl: {} requestFingerprint: {}",
                TraceContext.getTraceId(),
                commandDTO.getMerchantId(),
                commandDTO.getMerchantOrderNo(),
                commandDTO.getTransactionId(),
                commandDTO.getTransactionType(),
                commandDTO.getPaymentMethod(),
                commandDTO.getCurrency(),
                commandDTO.getAmount(),
                commandDTO.getPayerIp(),
                maskUrl(commandDTO.getSourceUrl()),
                commandDTO.getRequestFingerprint());
        RiskPaymentEvaluateClientResponseDTO responseDTO = riskInternalClient.evaluatePayment(buildRequest(commandDTO));
        PaymentRiskDecisionEnum decisionEnum = PaymentRiskDecisionEnum.of(responseDTO.getDecision());
        PaymentRiskDecisionDTO decisionDTO = new PaymentRiskDecisionDTO();
        decisionDTO.setPassed(decisionEnum.isAllowProceed());
        decisionDTO.setDecision(decisionEnum.getCode());
        decisionDTO.setRiskRecordNo(responseDTO.getRiskRecordNo());
        decisionDTO.setRiskCode(responseDTO.getReasonCode());
        decisionDTO.setRiskMessage(responseDTO.getReasonMessage());
        log.info("event: PAYMENT_RISK_REQUEST_END stage=RISK traceId: {} merchantId: {} merchantOrderNo: {} transactionId: {} transactionType: {} currency: {} amount: {} decision: {} passed: {} riskRecordNo: {} reasonCode: {} durationMs: {}",
                TraceContext.getTraceId(),
                commandDTO.getMerchantId(),
                commandDTO.getMerchantOrderNo(),
                commandDTO.getTransactionId(),
                commandDTO.getTransactionType(),
                commandDTO.getCurrency(),
                commandDTO.getAmount(),
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

    /**
     * 脱敏商户来源页面 URL。
     *
     * @param url 商户来源页面地址
     * @return 去除 query 值后的 URL
     */
    private String maskUrl(String url) {
        if (!StringUtils.hasText(url)) {
            return null;
        }
        int queryIndex = url.indexOf('?');
        if (queryIndex < 0) {
            return url;
        }
        return url.substring(0, queryIndex) + "?...";
    }

    /**
     * 构造支付风控评估请求。
     * <p>
     * 前置条件：支付创建命令已经生成平台交易号并完成基础参数校验。
     * 该方法把商户号、交易号、金额币种、请求指纹、来源页面、付款人 IP、账单信息、卡摘要和 3DS 信息复制到
     * service-risk 内部请求；卡号和安全码不得在日志中明文输出。
     * </p>
     * @param commandDTO 支付创建命令，提供风控评估所需的交易、商户、金额、来源和支付工具字段
     * @return service-risk 支付评估请求 DTO
     */
    private RiskPaymentEvaluateClientRequestDTO buildRequest(PaymentCreateCommandDTO commandDTO) {
        RiskPaymentEvaluateClientRequestDTO requestDTO = new RiskPaymentEvaluateClientRequestDTO();
        requestDTO.setMerchantId(commandDTO.getMerchantId());
        requestDTO.setMerchantOrderNo(commandDTO.getMerchantOrderNo());
        requestDTO.setTransactionId(commandDTO.getTransactionId());
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
     * 构造sub商户info对象，完成字段复制、格式标准化和敏感数据处理。
     * <p>
     * 前置条件：调用方已准备 支付核心服务 所需的源对象、配置或协议字段。
     * 该方法主要完成字段映射、格式标准化、金额币种整理或响应组装，不承担远程调用职责。
     * 异常边界：必要字段缺失或格式非法时抛出当前模块约定异常；敏感字段只保留脱敏、摘要或最小必要值。
     * </p>
     * @param commandDTO command DTO，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
     * @param requestDTO request DTO，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
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
     * 构造billinginfo对象，完成字段复制、格式标准化和敏感数据处理。
     * <p>
     * 前置条件：调用方已准备 支付核心服务 所需的源对象、配置或协议字段。
     * 该方法主要完成字段映射、格式标准化、金额币种整理或响应组装，不承担远程调用职责。
     * 异常边界：必要字段缺失或格式非法时抛出当前模块约定异常；敏感字段只保留脱敏、摘要或最小必要值。
     * </p>
     * @param commandDTO command DTO，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
     * @param requestDTO request DTO，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
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
     * 构造cardinfo对象，完成字段复制、格式标准化和敏感数据处理。
     * <p>
     * 前置条件：调用方已准备 支付核心服务 所需的源对象、配置或协议字段。
     * 该方法主要完成字段映射、格式标准化、金额币种整理或响应组装，不承担远程调用职责。
     * 异常边界：必要字段缺失或格式非法时抛出当前模块约定异常；敏感字段只保留脱敏、摘要或最小必要值。
     * </p>
     * @param commandDTO command DTO，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
     * @param requestDTO request DTO，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
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
     * 构造threedsinfo对象，完成字段复制、格式标准化和敏感数据处理。
     * <p>
     * 前置条件：调用方已准备 支付核心服务 所需的源对象、配置或协议字段。
     * 该方法主要完成字段映射、格式标准化、金额币种整理或响应组装，不承担远程调用职责。
     * 异常边界：必要字段缺失或格式非法时抛出当前模块约定异常；敏感字段只保留脱敏、摘要或最小必要值。
     * </p>
     * @param commandDTO command DTO，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
     * @param requestDTO request DTO，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
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
