package com.scott.payment.payment.service.impl;

import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.core.trace.TraceContext;
import com.scott.payment.component.redis.support.RedisKeyDigest;
import com.scott.payment.payment.api.internal.dto.PaymentCreateCommandDTO;
import com.scott.payment.payment.client.risk.RiskInternalClient;
import com.scott.payment.payment.client.risk.dto.RiskPaymentEvaluateClientRequestDTO;
import com.scott.payment.payment.client.risk.dto.RiskPaymentEvaluateClientResponseDTO;
import com.scott.payment.payment.client.risk.dto.RiskMerchantLimitReservationClientRequestDTO;
import com.scott.payment.payment.domain.state.PaymentRiskDecisionEnum;
import com.scott.payment.payment.service.PaymentRiskInvokeService;
import com.scott.payment.payment.service.dto.PaymentRiskDecisionDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 支付创建阶段的远程风控适配服务。
 *
 * <p>该服务在渠道路由前把支付上下文转换为 service-risk 的内部契约，并将风控原始决策映射为
 * 支付域可使用的结果。风控技术故障采用 Fail Closed，避免在无法确认风险结论时继续发起渠道交易；
 * 技术故障与真实风控拒绝始终保持语义隔离。</p>
 */
@Service
@Slf4j
public class RiskPaymentRiskInvokeService implements PaymentRiskInvokeService {

    /**
     * service-risk 不可用时使用的安全失败说明。
     */
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
        // 只记录业务定位字段和不可逆摘要，原始 IP、持卡人资料及完整 URL 不得进入日志。
        log.info("开始调用支付风控, event: PAYMENT_RISK_REQUEST_START, stage: RISK, traceId: {}, merchantId: {}, merchantOrderNo: {}, transactionId: {}, transactionType: {}, paymentMethod: {}, currency: {}, amount: {}, ingressClientIpHash: {}, payerIpHash: {}, sourceUrlHash: {}, requestFingerprint: {}",
                TraceContext.getTraceId(),
                commandDTO.getMerchantId(),
                commandDTO.getMerchantOrderNo(),
                commandDTO.getTransactionId(),
                commandDTO.getTransactionType(),
                commandDTO.getPaymentMethod(),
                commandDTO.getCurrency(),
                commandDTO.getAmount(),
                digestIp(commandDTO.getIngressClientIp()),
                digestIp(commandDTO.getPayerIp()),
                digestSensitiveValue(commandDTO.getSourceUrl()),
                commandDTO.getRequestFingerprint());
        PaymentRiskDecisionDTO decisionDTO;
        try {
            RiskPaymentEvaluateClientResponseDTO responseDTO =
                    riskInternalClient.evaluatePayment(buildRequest(commandDTO));
            // 空响应无法证明交易已通过风控，必须按技术故障关闭交易入口，不能默认为 ALLOW。
            if (responseDTO == null) {
                throw new IllegalStateException("service-risk returned empty response");
            }
            // 支付域只消费标准决策；风控原因作为审计证据保留，不参与支付状态字符串判断。
            PaymentRiskDecisionEnum decisionEnum = PaymentRiskDecisionEnum.of(responseDTO.getDecision());
            decisionDTO = new PaymentRiskDecisionDTO();
            decisionDTO.setPassed(decisionEnum.isAllowProceed());
            decisionDTO.setDecision(decisionEnum.getCode());
            decisionDTO.setRiskRecordNo(responseDTO.getRiskRecordNo());
            decisionDTO.setRiskCode(responseDTO.getReasonCode());
            decisionDTO.setRiskMessage(responseDTO.getReasonMessage());
            decisionDTO.setMerchantLimitReserved(responseDTO.isMerchantLimitReserved());
        } catch (RuntimeException exception) {
            log.error("支付风控调用失败, event: PAYMENT_RISK_REQUEST_FAILED, stage: RISK, traceId: {}, merchantId: {}, merchantOrderNo: {}, transactionId: {}, transactionType: {}, exceptionType: {}, rootCauseType: {}, durationMs: {}",
                    TraceContext.getTraceId(),
                    commandDTO.getMerchantId(),
                    commandDTO.getMerchantOrderNo(),
                    commandDTO.getTransactionId(),
                    commandDTO.getTransactionType(),
                    exception.getClass().getSimpleName(),
                    rootCauseType(exception),
                    elapsedMillis(startNanos));
            // 技术故障必须 Fail Closed，但不能伪装成真实风控拒绝并落终态记录。
            throw new ServiceException(ApiResultEnum.NETWORK_BUSY.getCode(),
                    RISK_SERVICE_UNAVAILABLE_MESSAGE, exception);
        }
        // 将风控证据带入后续持久化，但调用方仍以标准 decision/passed 字段决定是否继续路由。
        commandDTO.setRiskRecordNo(decisionDTO.getRiskRecordNo());
        commandDTO.setRiskCode(decisionDTO.getRiskCode());
        commandDTO.setRiskMessage(decisionDTO.getRiskMessage());
        log.info("支付风控调用完成, event: PAYMENT_RISK_REQUEST_END, stage: RISK, traceId: {}, merchantId: {}, merchantOrderNo: {}, transactionId: {}, transactionType: {}, currency: {}, amount: {}, decision: {}, passed: {}, riskRecordNo: {}, reasonCode: {}, durationMs: {}",
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
        // service-risk 以交易号保证撤销幂等；重复补偿不得重复释放商户累计限额。
        RiskMerchantLimitReservationClientRequestDTO requestDTO =
                new RiskMerchantLimitReservationClientRequestDTO();
        requestDTO.setTransactionId(commandDTO.getTransactionId());
        requestDTO.setRiskRecordNo(decisionDTO.getRiskRecordNo());
        requestDTO.setReason(reason);
        riskInternalClient.cancelMerchantLimitReservation(requestDTO);
        log.info("风控限额预占已撤销, event: PAYMENT_RISK_RESERVATION_CANCELLED, stage: RISK_COMPENSATION, traceId: {}, transactionId: {}, riskRecordNo: {}, reason: {}",
                TraceContext.getTraceId(),
                commandDTO.getTransactionId(),
                decisionDTO.getRiskRecordNo(),
                reason);
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
     * 返回受控异常链中最深层的异常类型，避免日志输出数据库语句或敏感报文。
     */
    private String rootCauseType(Throwable throwable) {
        Throwable rootCause = throwable;
        int depth = 0;
        while (rootCause.getCause() != null && rootCause.getCause() != rootCause && depth++ < 16) {
            rootCause = rootCause.getCause();
        }
        return rootCause.getClass().getSimpleName();
    }

    /**
     * 将 IP 转换为不可逆摘要，兼顾同源关联与敏感数据保护。
     *
     * @param value 原始 IP
     * @return IP 摘要；空输入返回 {@code null}
     */
    private String digestIp(String value) {
        return digestSensitiveValue(value);
    }

    /**
     * 将敏感文本转换为不可逆摘要，避免日志泄露 URL、IP 等完整内容。
     *
     * @param value 原始敏感文本
     * @return 文本摘要；空输入返回 {@code null}
     */
    private String digestSensitiveValue(String value) {
        return StringUtils.hasText(value) ? RedisKeyDigest.sha256(value.trim()) : null;
    }

    /**
     * 构建 service-risk 内部请求，保持支付、付款人、收货人与卡信息的语义边界。
     */
    private RiskPaymentEvaluateClientRequestDTO buildRequest(PaymentCreateCommandDTO commandDTO) {
        RiskPaymentEvaluateClientRequestDTO requestDTO = new RiskPaymentEvaluateClientRequestDTO();
        requestDTO.setMerchantId(commandDTO.getMerchantId());
        requestDTO.setMerchantOrderNo(commandDTO.getMerchantOrderNo());
        requestDTO.setTransactionId(commandDTO.getTransactionId());
        requestDTO.setTransactionType(commandDTO.getTransactionType());
        requestDTO.setPaymentMethod(commandDTO.getPaymentMethod());
        requestDTO.setRequestId(commandDTO.getRequestId());
        requestDTO.setRequestSource(commandDTO.getRequestSource());
        requestDTO.setIngressClientIp(commandDTO.getIngressClientIp());
        requestDTO.setAmount(commandDTO.getAmount());
        requestDTO.setCurrency(commandDTO.getCurrency());
        requestDTO.setTransactionDateTime(commandDTO.getTransactionDateTime());
        requestDTO.setRequestFingerprint(commandDTO.getRequestFingerprint());
        requestDTO.setSourceUrl(commandDTO.getSourceUrl());
        requestDTO.setPayerIp(commandDTO.getPayerIp());
        requestDTO.setUserAgent(commandDTO.getUserAgent());
        // 可选上下文按业务对象分组填充，避免空对象覆盖风控侧默认值。
        fillSubMerchantInfo(commandDTO, requestDTO);
        fillBillingInfo(commandDTO, requestDTO);
        fillPayerInfo(commandDTO, requestDTO);
        fillShippingInfo(commandDTO, requestDTO);
        fillRiskContext(commandDTO, requestDTO);
        fillCardInfo(commandDTO, requestDTO);
        fillThreeDsInfo(commandDTO, requestDTO);
        return requestDTO;
    }

    /**
     * 填充平台型商户场景下的子商户身份与经营信息。
     */
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

    /**
     * 填充账单持卡人信息；这些字段仅进入内部风控请求，不得写入日志。
     */
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

    /**
     * 填充付款人身份，付款人与账单持卡人是两个独立业务主体。
     */
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
        // 顶层采集值优先；仅在缺失时使用付款人对象中的兼容字段。
        if (!StringUtils.hasText(requestDTO.getPayerIp())) {
            requestDTO.setPayerIp(payerInfo.getIpAddress());
        }
        if (StringUtils.hasText(payerInfo.getUserAgent())) {
            requestDTO.setUserAgent(payerInfo.getUserAgent());
        }
    }

    /**
     * 填充收货人信息，显式 shippingInfo 优先于旧版 riskContext 兼容字段。
     */
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
        // 新版收货对象不存在时才读取旧字段，防止同一语义出现两套冲突数据。
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

    /**
     * 填充卡风险特征。
     *
     * <p>完整卡号只通过内部服务契约传输，日志只能记录摘要；BIN 最多取 11 位以支持扩展 BIN 规则，
     * 同时保留末四位用于风控记录关联。</p>
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
        requestDTO.setCardNo(normalizedCardNo);
        if (normalizedCardNo.length() >= 6) {
            requestDTO.setCardBin(normalizedCardNo.substring(0, Math.min(normalizedCardNo.length(), 11)));
        }
        if (normalizedCardNo.length() >= 4) {
            requestDTO.setCardLast4(normalizedCardNo.substring(normalizedCardNo.length() - 4));
        }
    }

    /**
     * 填充 3DS 认证证据，供风控评估认证强度和责任转移。
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
