package com.scott.payment.payment.service.impl;

import com.scott.payment.component.core.trace.TraceContext;
import com.scott.payment.payment.api.internal.dto.PaymentCreateCommandDTO;
import com.scott.payment.payment.client.risk.RiskInternalClient;
import com.scott.payment.payment.client.risk.dto.RiskPaymentEvaluateClientRequestDTO;
import com.scott.payment.payment.client.risk.dto.RiskPaymentEvaluateClientResponseDTO;
import com.scott.payment.payment.client.risk.dto.RiskMerchantLimitReservationClientRequestDTO;
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

    /** service-risk 不可用时返回的稳定风险原因码。 */
    private static final String RISK_SERVICE_UNAVAILABLE = "RISK_SERVICE_UNAVAILABLE";

    /** service-risk 不可用时使用的安全失败说明。 */
    private static final String RISK_SERVICE_UNAVAILABLE_MESSAGE = "risk service is unavailable";

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
        PaymentRiskDecisionDTO decisionDTO;
        try {
            RiskPaymentEvaluateClientResponseDTO responseDTO =
                    riskInternalClient.evaluatePayment(buildRequest(commandDTO));
            if (responseDTO == null) {
                throw new IllegalStateException("service-risk returned empty response");
            }
            PaymentRiskDecisionEnum decisionEnum = PaymentRiskDecisionEnum.of(responseDTO.getDecision());
            decisionDTO = new PaymentRiskDecisionDTO();
            decisionDTO.setPassed(decisionEnum.isAllowProceed());
            decisionDTO.setDecision(decisionEnum.getCode());
            decisionDTO.setRiskRecordNo(responseDTO.getRiskRecordNo());
            decisionDTO.setRiskCode(responseDTO.getReasonCode());
            decisionDTO.setRiskMessage(responseDTO.getReasonMessage());
            decisionDTO.setMerchantLimitReserved(responseDTO.isMerchantLimitReserved());
        } catch (RuntimeException exception) {
            decisionDTO = unavailableDecision();
            log.error("event: PAYMENT_RISK_REQUEST_FAILED stage=RISK traceId: {} merchantId: {} merchantOrderNo: {} transactionId: {} transactionType: {} exceptionType: {} durationMs: {}",
                    TraceContext.getTraceId(),
                    commandDTO.getMerchantId(),
                    commandDTO.getMerchantOrderNo(),
                    commandDTO.getTransactionId(),
                    commandDTO.getTransactionType(),
                    exception.getClass().getSimpleName(),
                    elapsedMillis(startNanos));
        }
        commandDTO.setRiskRecordNo(decisionDTO.getRiskRecordNo());
        commandDTO.setRiskCode(decisionDTO.getRiskCode());
        commandDTO.setRiskMessage(decisionDTO.getRiskMessage());
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

    /**
     * 撤销已成功创建但本地支付事务未提交的商户限额预占。
     *
     * <p>只有明确存在预占且交易号完整时才调用补偿接口；补偿由 service-risk 按交易号幂等。</p>
     *
     * @param commandDTO  支付创建命令
     * @param decisionDTO 原风控决策及预占标识
     * @param reason      受控补偿原因
     */
    @Override
    public void cancelMerchantLimitReservation(PaymentCreateCommandDTO commandDTO,
                                               PaymentRiskDecisionDTO decisionDTO,
                                               String reason) {
        if (commandDTO == null || decisionDTO == null || !decisionDTO.isMerchantLimitReserved()
                || !StringUtils.hasText(commandDTO.getTransactionId())) {
            return;
        }
        RiskMerchantLimitReservationClientRequestDTO requestDTO =
                new RiskMerchantLimitReservationClientRequestDTO();
        requestDTO.setTransactionId(commandDTO.getTransactionId());
        requestDTO.setRiskRecordNo(decisionDTO.getRiskRecordNo());
        requestDTO.setReason(reason);
        riskInternalClient.cancelMerchantLimitReservation(requestDTO);
        log.info("event: PAYMENT_RISK_RESERVATION_CANCELLED stage=RISK_COMPENSATION traceId: {} transactionId: {} riskRecordNo: {} reason: {}",
                TraceContext.getTraceId(),
                commandDTO.getTransactionId(),
                decisionDTO.getRiskRecordNo(),
                reason);
    }

    /**
     * 构造 service-risk 不可用时的 Fail Closed 决策。
     *
     * @return 不允许继续支付的 UNKNOWN 决策
     */
    private PaymentRiskDecisionDTO unavailableDecision() {
        PaymentRiskDecisionDTO decisionDTO = new PaymentRiskDecisionDTO();
        decisionDTO.setPassed(false);
        decisionDTO.setDecision(PaymentRiskDecisionEnum.UNKNOWN.getCode());
        decisionDTO.setRiskCode(RISK_SERVICE_UNAVAILABLE);
        decisionDTO.setRiskMessage(RISK_SERVICE_UNAVAILABLE_MESSAGE);
        return decisionDTO;
    }

    /**
     * 计算远程风控调用耗时。
     *
     * @param startNanos 调用开始的单调时钟纳秒值
     * @return 已耗时毫秒数
     */
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

    private RiskPaymentEvaluateClientRequestDTO buildRequest(PaymentCreateCommandDTO commandDTO) {
        RiskPaymentEvaluateClientRequestDTO requestDTO = new RiskPaymentEvaluateClientRequestDTO();
        requestDTO.setMerchantId(commandDTO.getMerchantId());
        requestDTO.setMerchantOrderNo(commandDTO.getMerchantOrderNo());
        requestDTO.setTransactionId(commandDTO.getTransactionId());
        requestDTO.setTransactionType(commandDTO.getTransactionType());
        requestDTO.setPaymentMethod(commandDTO.getPaymentMethod());
        requestDTO.setRequestId(commandDTO.getRequestId());
        requestDTO.setRequestSource(commandDTO.getRequestSource());
        requestDTO.setAmount(commandDTO.getAmount());
        requestDTO.setCurrency(commandDTO.getCurrency());
        requestDTO.setTransactionDateTime(commandDTO.getTransactionDateTime());
        requestDTO.setRequestFingerprint(commandDTO.getRequestFingerprint());
        requestDTO.setSourceUrl(commandDTO.getSourceUrl());
        requestDTO.setPayerIp(commandDTO.getPayerIp());
        requestDTO.setUserAgent(commandDTO.getUserAgent());
        fillSubMerchantInfo(commandDTO, requestDTO);
        fillBillingInfo(commandDTO, requestDTO);
        fillPayerInfo(commandDTO, requestDTO);
        fillShippingInfo(commandDTO, requestDTO);
        fillRiskContext(commandDTO, requestDTO);
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
        requestDTO.setLegalPerson(subMerchantInfoDTO.getSubName());
        requestDTO.setEnterprise(subMerchantInfoDTO.getSubCompanyName());
        requestDTO.setMerchantBillingAddress(subMerchantInfoDTO.getSubStreet());
    }

    private void fillBillingInfo(PaymentCreateCommandDTO commandDTO, RiskPaymentEvaluateClientRequestDTO requestDTO) {
        PaymentCreateCommandDTO.BillingCardHolderInfoDTO billingInfoDTO = commandDTO.getBillingCardHolderInfo();
        if (billingInfoDTO == null) {
            return;
        }
        requestDTO.setBillingCountry(billingInfoDTO.getCountry());
        requestDTO.setBillingEmail(billingInfoDTO.getEmail());
        requestDTO.setBillingPhone(billingInfoDTO.getPhone());
        requestDTO.setCardholderName(joinName(billingInfoDTO.getFirstName(), billingInfoDTO.getLastName()));
        requestDTO.setBillingAddress(billingInfoDTO.getStreet());
        requestDTO.setBillingZip(billingInfoDTO.getPostal());
        requestDTO.setBillingRegion(billingInfoDTO.getState());
        requestDTO.setBillingCity(billingInfoDTO.getCity());
    }

    /** Copy payer identity and address independently from cardholder billing data. */
    private void fillPayerInfo(PaymentCreateCommandDTO commandDTO,
                               RiskPaymentEvaluateClientRequestDTO requestDTO) {
        PaymentCreateCommandDTO.PayerInfoDTO payerInfo = commandDTO.getPayerInfo();
        if (payerInfo == null) {
            return;
        }
        requestDTO.setPayerId(payerInfo.getPayerId());
        requestDTO.setPayerName(joinName(payerInfo.getFirstName(), payerInfo.getLastName()));
        requestDTO.setPayerEmail(payerInfo.getEmail());
        requestDTO.setPayerPhone(payerInfo.getPhone());
        requestDTO.setPayerCountry(payerInfo.getCountry());
        requestDTO.setPayerAddress(payerInfo.getStreet());
        requestDTO.setPayerZip(payerInfo.getPostal());
        requestDTO.setPayerRegion(payerInfo.getState());
        requestDTO.setPayerCity(payerInfo.getCity());
        requestDTO.setPayerSessionId(payerInfo.getSessionId());
        requestDTO.setPayerIp(payerInfo.getIpAddress());
        if (StringUtils.hasText(payerInfo.getUserAgent())) {
            requestDTO.setUserAgent(payerInfo.getUserAgent());
        }
    }

    /** Copy shipping identity and address independently from optional legacy riskInfo. */
    private void fillShippingInfo(PaymentCreateCommandDTO commandDTO,
                                  RiskPaymentEvaluateClientRequestDTO requestDTO) {
        PaymentCreateCommandDTO.ShippingInfoDTO shippingInfo = commandDTO.getShippingInfo();
        if (shippingInfo == null) {
            return;
        }
        requestDTO.setShippingName(joinName(shippingInfo.getFirstName(), shippingInfo.getLastName()));
        requestDTO.setShippingEmail(shippingInfo.getEmail());
        requestDTO.setShippingPhone(shippingInfo.getPhone());
        requestDTO.setShippingCountry(shippingInfo.getCountry());
        requestDTO.setShippingRegion(shippingInfo.getState());
        requestDTO.setShippingCity(shippingInfo.getCity());
        requestDTO.setShippingAddress(shippingInfo.getStreet());
        requestDTO.setShippingZip(shippingInfo.getPostal());
    }

    /**
     * 复制商户可选风控上下文。
     *
     * @param commandDTO 支付创建命令
     * @param requestDTO 风控评估请求
     */
    private void fillRiskContext(PaymentCreateCommandDTO commandDTO,
                                 RiskPaymentEvaluateClientRequestDTO requestDTO) {
        PaymentCreateCommandDTO.RiskContextDTO riskContextDTO = commandDTO.getRiskContext();
        if (riskContextDTO == null) {
            return;
        }
        requestDTO.setCustomerId(riskContextDTO.getCustomerId());
        requestDTO.setDeviceFingerprint(riskContextDTO.getDeviceFingerprint());
        if (commandDTO.getShippingInfo() == null) {
            requestDTO.setShippingAddress(riskContextDTO.getShippingAddress());
            requestDTO.setShippingZip(riskContextDTO.getShippingPostalCode());
            requestDTO.setShippingCountry(riskContextDTO.getShippingCountry());
        }
    }

    /**
     * 拼接持卡人姓名供风控匹配；空白部分被忽略，完整姓名属于敏感信息，禁止直接写日志。
     *
     * @param firstName 名
     * @param lastName  姓
     * @return 规范化姓名，两个部分均为空时返回 null
     */
    private String joinName(String firstName, String lastName) {
        String first = StringUtils.hasText(firstName) ? firstName.trim() : "";
        String last = StringUtils.hasText(lastName) ? lastName.trim() : "";
        String fullName = (first + " " + last).trim();
        return StringUtils.hasText(fullName) ? fullName : null;
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
        requestDTO.setCardNo(normalizedCardNo);
        if (normalizedCardNo.length() >= 6) {
            requestDTO.setCardBin(normalizedCardNo.substring(0, Math.min(normalizedCardNo.length(), 11)));
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
