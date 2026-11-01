package com.scott.payment.payment.service.impl;

import com.scott.payment.channel.payment.dto.request.ChannelPaymentRequest;
import com.scott.payment.channel.payment.dto.response.ChannelPaymentResponse;
import com.scott.payment.channel.payment.executor.PaymentChannelExecutor;
import com.scott.payment.channel.payment.exception.ChannelException;
import com.scott.payment.channel.payment.exception.ChannelResponseException;
import com.scott.payment.channel.payment.exception.ChannelTimeoutException;
import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.core.trace.TraceContext;
import com.scott.payment.component.core.util.SensitiveDataMaskUtils;
import com.scott.payment.component.core.util.identity.PaymentOrderNoGenerator;
import com.scott.payment.component.core.iso.IsoCurrencyInfo;
import com.scott.payment.component.core.iso.IsoCurrencyResolver;
import com.scott.payment.component.db.iso.service.IsoDictionaryService;
import com.scott.payment.payment.api.internal.dto.PaymentCreateCommandDTO;
import com.scott.payment.payment.domain.state.PaymentFailureReasonEnum;
import com.scott.payment.payment.service.PaymentChannelInvokeService;
import com.scott.payment.payment.service.dto.PaymentChannelInvokeResultDTO;
import com.scott.payment.payment.service.dto.PaymentPreparedChannelRequestDTO;
import com.scott.payment.payment.service.dto.PaymentRouteResultDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.net.URI;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 收单渠道调用服务的默认实现。
 *
 * <p>该服务把支付核心上下文转换为渠道统一请求，通过 {@link PaymentChannelExecutor} 调用渠道 SPI，
 * 并返回可审计的交互摘要。它不推进平台交易状态；超时和响应不确定结果必须交由查询或回调勾兑。</p>
 */
@Service
@Slf4j
public class DefaultPaymentChannelInvokeService implements PaymentChannelInvokeService {

    /**
     * 渠道交易 ID 前缀，用于 MPGS transactionId 这类渠道侧请求幂等标识。
     */
    private static final String CHANNEL_TRANSACTION_ID_PREFIX = "CH";

    /**
     * 平台渠道请求 ID 前缀，对应 transaction_channel_request.request_id。
     */
    private static final String CHANNEL_REQUEST_ID_PREFIX = "CR";

    private final PaymentChannelExecutor paymentChannelExecutor;

    /**
     * 数据库 ISO 币种字典服务，用于读取 base_iso_currency 中维护的辅币位。
     */
    private final IsoDictionaryService isoDictionaryService;

    /**
     * 创建渠道调用服务。
     *
     * @param paymentChannelExecutor 渠道执行器
     */
    public DefaultPaymentChannelInvokeService(PaymentChannelExecutor paymentChannelExecutor) {
        this(paymentChannelExecutor, null);
    }

    /**
     * 创建渠道调用服务。
     *
     * @param paymentChannelExecutor 渠道执行器
     * @param isoDictionaryService 数据库币种字典服务
     */
    @Autowired
    public DefaultPaymentChannelInvokeService(PaymentChannelExecutor paymentChannelExecutor,
                                              IsoDictionaryService isoDictionaryService) {
        this.paymentChannelExecutor = paymentChannelExecutor;
        this.isoDictionaryService = isoDictionaryService;
    }

    /**
     * 调用收单渠道并返回可审计的调用上下文。
     * <p>
     * 平台与渠道交互时，MPGS 的 orderId 使用平台原始交易 transactionId，
     * MPGS 的 transactionId 使用平台生成的 channel_transaction_id；该方法只负责生成渠道请求、
     * 记录请求开始/结束摘要和封装渠道异常，不在此处推进平台交易状态。
     *
     * @param commandDTO      支付核心交易命令
     * @param routeResult     渠道路由和 MID 配置结果
     * @param operationId     平台内部生命周期关联标识，不对商户开放
     * @param transactionId   平台当前交易唯一标识
     * @param channelOrderNo  渠道订单号，MPGS 场景为原始授权或一步支付的 transactionId
     * @return 渠道调用上下文和同步响应摘要
     */
    @Override
    public PaymentChannelInvokeResultDTO invoke(PaymentCreateCommandDTO commandDTO,
                                                PaymentRouteResultDTO routeResult,
                                                String operationId,
                                                String transactionId,
                                                String channelOrderNo) {
        PaymentPreparedChannelRequestDTO preparedChannelRequest = new PaymentPreparedChannelRequestDTO();
        preparedChannelRequest.setRequestId(PaymentOrderNoGenerator.nextOrderNo(CHANNEL_REQUEST_ID_PREFIX, commandDTO.getTransactionDateTime()));
        preparedChannelRequest.setChannelOrderNo(channelOrderNo);
        preparedChannelRequest.setChannelTransactionId(resolveChannelTransactionId(commandDTO));
        return invoke(commandDTO, routeResult, operationId, transactionId, preparedChannelRequest);
    }

    /**
     * 使用本地准备事务预生成的渠道请求身份调用渠道。
     *
     * @param commandDTO             支付核心交易命令
     * @param routeResult            渠道路由和 MID 配置结果
     * @param operationId            平台内部生命周期关联标识
     * @param transactionId          平台当前交易唯一标识
     * @param preparedChannelRequest 已提交的渠道请求身份
     * @return 渠道调用上下文和同步响应摘要
     */
    @Override
    public PaymentChannelInvokeResultDTO invoke(PaymentCreateCommandDTO commandDTO,
                                                PaymentRouteResultDTO routeResult,
                                                String operationId,
                                                String transactionId,
                                                PaymentPreparedChannelRequestDTO preparedChannelRequest) {
        ChannelPaymentRequest channelRequest = toChannelRequest(commandDTO, routeResult, operationId, transactionId,
                preparedChannelRequest == null ? null : preparedChannelRequest.getChannelOrderNo(),
                preparedChannelRequest == null ? null : preparedChannelRequest.getChannelTransactionId());
        copyPreparedExtensions(preparedChannelRequest, channelRequest);
        PaymentChannelInvokeResultDTO resultDTO = new PaymentChannelInvokeResultDTO();
        resultDTO.setRequestId(preparedChannelRequest == null ? null : preparedChannelRequest.getRequestId());
        resultDTO.setChannelRequest(channelRequest);
        resultDTO.setRequestStartTime(LocalDateTime.now());
        resultDTO.setHttpMethod(resolveHttpMethod(commandDTO));
        resultDTO.setRequestScene(resolveRequestScene(commandDTO));
        resultDTO.setRequestUrlMasked(resolveRequestUrl(routeResult, channelRequest));
        log.info("渠道请求开始, event: PAYMENT_CHANNEL_REQUEST_START, stage: {}, traceId: {}, merchantId: {}, merchantOrderNo: {}, transactionId: {}, operationId: {}, transactionType: {}, paymentMethod: {}, currency: {}, amount: {}, channelCode: {}, channelMidId: {}, midNo: {}, requestId: {}, channelOrderNo: {}, channelTransactionId: {}, endpointHost: {}, endpointPath: {}, httpMethod: {}, requestScene: {}, connectTimeoutSeconds: {}, readTimeoutSeconds: {}, requestSummary: {}",
                "CHANNEL_CALL",
                TraceContext.getTraceId(),
                commandDTO.getMerchantId(),
                commandDTO.getMerchantOrderNo(),
                transactionId,
                operationId,
                commandDTO.getTransactionType(),
                commandDTO.getPaymentMethod(),
                channelRequest.getCurrency(),
                channelRequest.getAmount(),
                channelRequest.getChannelCode(),
                routeResult == null ? null : routeResult.getMidConfigId(),
                routeResult == null ? null : maskShort(routeResult.getMidNo()),
                resultDTO.getRequestId(),
                channelRequest.getChannelOrderNo(),
                channelRequest.getChannelTransactionId(),
                endpointHost(resultDTO.getRequestUrlMasked()),
                endpointPath(resultDTO.getRequestUrlMasked()),
                resultDTO.getHttpMethod(),
                resultDTO.getRequestScene(),
                routeResult == null ? null : routeResult.getConnectTimeoutSeconds(),
                routeResult == null ? null : routeResult.getReadTimeoutSeconds(),
                channelRequestSummary(channelRequest));
        try {
            ChannelPaymentResponse channelResponse = paymentChannelExecutor.execute(channelRequest);
            LocalDateTime responseTime = LocalDateTime.now();
            resultDTO.setChannelResponse(channelResponse);
            if (channelResponse != null) {
                resultDTO.setHttpMethod(firstText(channelResponse.getHttpMethod(), resultDTO.getHttpMethod()));
                resultDTO.setRequestUrlMasked(firstText(channelResponse.getRequestUrlMasked(), resultDTO.getRequestUrlMasked()));
            }
            resultDTO.setRequestStatus("SUCCESS");
            resultDTO.setResponseTime(responseTime);
            resultDTO.setDurationMillis(durationMillis(resultDTO.getRequestStartTime(), responseTime));
            log.info("渠道请求完成, event: PAYMENT_CHANNEL_REQUEST_END, stage: {}, traceId: {}, merchantId: {}, merchantOrderNo: {}, transactionId: {}, operationId: {}, transactionType: {}, paymentMethod: {}, currency: {}, amount: {}, channelCode: {}, requestId: {}, channelOrderNo: {}, channelTransactionId: {}, channelResponseCode: {}, rawChannelStatus: {}, channelTradeStatus: {}, acquirerCode: {}, httpStatus: {}, requestStatus: {}, responseSummary: {}, durationMs: {}",
                    "CHANNEL_CALL",
                    TraceContext.getTraceId(),
                    commandDTO.getMerchantId(),
                    commandDTO.getMerchantOrderNo(),
                    transactionId,
                    operationId,
                    commandDTO.getTransactionType(),
                    commandDTO.getPaymentMethod(),
                    channelRequest.getCurrency(),
                    channelRequest.getAmount(),
                    channelRequest.getChannelCode(),
                    resultDTO.getRequestId(),
                    channelRequest.getChannelOrderNo(),
                    channelRequest.getChannelTransactionId(),
                    channelResponse == null ? null : channelResponse.getChannelResponseCode(),
                    channelResponse == null ? null : channelResponse.getRawChannelStatus(),
                    channelResponse == null ? null : channelResponse.getChannelTradeStatus(),
                    channelResponse == null ? null : channelResponse.getAcquirerReferenceNo(),
                    channelResponse == null ? null : channelResponse.getHttpStatus(),
                    resultDTO.getRequestStatus(),
                    channelResponseSummary(channelResponse),
                    resultDTO.getDurationMillis());
            return resultDTO;
        } catch (ChannelException exception) {
            LocalDateTime responseTime = LocalDateTime.now();
            resultDTO.setRequestStatus(exception instanceof ChannelTimeoutException ? "TIMEOUT" : "FAILED");
            resultDTO.setResponseTime(responseTime);
            resultDTO.setDurationMillis(durationMillis(resultDTO.getRequestStartTime(), responseTime));
            resultDTO.setExceptionType(exception.getClass().getSimpleName());
            resultDTO.setExceptionMessage(resultDTO.getExceptionType());
            resultDTO.setOutcomeUncertain(exception.isOutcomeUncertain());
            resultDTO.setFailureReasonCode(failureReasonCode(exception));
            log.warn("渠道请求异常, event: PAYMENT_CHANNEL_REQUEST_FAILED, stage: {}, traceId: {}, merchantId: {}, merchantOrderNo: {}, transactionId: {}, operationId: {}, transactionType: {}, paymentMethod: {}, currency: {}, amount: {}, channelCode: {}, requestId: {}, channelOrderNo: {}, channelTransactionId: {}, endpointHost: {}, endpointPath: {}, requestStatus: {}, failureReasonCode: {}, exceptionType: {}, outcomeUncertain: {}, durationMs: {}",
                    "CHANNEL_CALL",
                    TraceContext.getTraceId(),
                    commandDTO.getMerchantId(),
                    commandDTO.getMerchantOrderNo(),
                    transactionId,
                    operationId,
                    commandDTO.getTransactionType(),
                    commandDTO.getPaymentMethod(),
                    channelRequest.getCurrency(),
                    channelRequest.getAmount(),
                    channelRequest.getChannelCode(),
                    resultDTO.getRequestId(),
                    channelRequest.getChannelOrderNo(),
                    channelRequest.getChannelTransactionId(),
                    endpointHost(resultDTO.getRequestUrlMasked()),
                    endpointPath(resultDTO.getRequestUrlMasked()),
                    resultDTO.getRequestStatus(),
                    resultDTO.getFailureReasonCode(),
                    resultDTO.getExceptionType(),
                    resultDTO.isOutcomeUncertain(),
                    resultDTO.getDurationMillis());
            throw new PaymentChannelInvokeException(resultDTO, exception);
        }
    }

    /**
     * 根据渠道异常类型和明确的结果确定性生成受控失败来源。
     * <p>
     * 响应异常只有在适配器明确声明 outcomeUncertain=false 时才代表渠道已拒绝；超时和不可解析响应均不能形成失败终态。
     * </p>
     *
     * @param exception 渠道适配层结构化异常
     * @return 支付核心受控失败原因码
     */
    private String failureReasonCode(ChannelException exception) {
        if (exception instanceof ChannelTimeoutException) {
            return PaymentFailureReasonEnum.CHANNEL_TIMEOUT.getCode();
        }
        if (exception instanceof ChannelResponseException) {
            return exception.isOutcomeUncertain()
                    ? PaymentFailureReasonEnum.CHANNEL_RESPONSE_INVALID.getCode()
                    : PaymentFailureReasonEnum.CHANNEL_DECLINED.getCode();
        }
        return exception.isOutcomeUncertain()
                ? PaymentFailureReasonEnum.CHANNEL_REQUEST_FAILED.getCode()
                : PaymentFailureReasonEnum.CHANNEL_REQUEST_NOT_SENT.getCode();
    }

    /**
     * 判断当前渠道是否支持使用已持久化渠道身份发起查询。
     *
     * @param commandDTO 查询命令
     * @param routeResult 渠道路由快照
     * @param operationId 平台内部生命周期关联标识
     * @param transactionId 平台当前交易 ID
     * @param preparedChannelRequest 已持久化查询身份
     * @return true 表示渠道可以识别当前查询身份
     */
    @Override
    public boolean supportsQueryReference(PaymentCreateCommandDTO commandDTO,
                                          PaymentRouteResultDTO routeResult,
                                          String operationId,
                                          String transactionId,
                                          PaymentPreparedChannelRequestDTO preparedChannelRequest) {
        ChannelPaymentRequest channelRequest = toChannelRequest(commandDTO, routeResult, operationId, transactionId,
                preparedChannelRequest == null ? null : preparedChannelRequest.getChannelOrderNo(),
                preparedChannelRequest == null ? null : preparedChannelRequest.getChannelTransactionId());
        copyPreparedExtensions(preparedChannelRequest, channelRequest);
        if (preparedChannelRequest != null && StringUtils.hasText(preparedChannelRequest.getRequestId())) {
            channelRequest.getExtension().put("requestId", preparedChannelRequest.getRequestId());
        }
        return paymentChannelExecutor.supportsQueryReference(channelRequest);
    }

    /**
     * 构造渠道统一请求。
     *
     * @param commandDTO  创建交易命令
     * @param routeResult 路由结果
     * @param operationId 平台内部生命周期关联标识
     * @param transactionId 平台当前交易唯一标识
     * @param channelOrderNo 渠道订单号
     * @param channelTransactionId 已持久化渠道交易 ID；查询场景必须复用原动作值
     * @return 渠道统一请求
     */
    private ChannelPaymentRequest toChannelRequest(PaymentCreateCommandDTO commandDTO,
                                                   PaymentRouteResultDTO routeResult,
                                                   String operationId,
                                                   String transactionId,
                                                   String channelOrderNo,
                                                   String channelTransactionId) {
        ChannelPaymentRequest request = new ChannelPaymentRequest();
        request.setChannelCode(routeResult.getChannelCode());
        request.setOperationId(operationId);
        request.setTransactionId(transactionId);
        request.setSourceTransactionId(commandDTO.getTransactionInfo() == null ? null : commandDTO.getTransactionInfo().getSourceTransactionId());
        request.setChannelOrderNo(channelOrderNo);
        request.setChannelTransactionId(StringUtils.hasText(channelTransactionId)
                ? channelTransactionId
                : resolveChannelTransactionId(commandDTO));
        request.setMerchantId(commandDTO.getMerchantId());
        request.setMerchantOrderNo(commandDTO.getMerchantOrderNo());
        request.setMerchantOrderId(commandDTO.getMerchantOrderId());
        request.setTransactionType(commandDTO.getTransactionType());
        request.setPaymentMethod(commandDTO.getPaymentMethod());
        request.setAmount(commandDTO.getTransactionAmount() == null ? commandDTO.getAmount() : commandDTO.getTransactionAmount());
        request.setCurrency(commandDTO.getTransactionCurrency() == null ? commandDTO.getCurrency() : commandDTO.getTransactionCurrency());
        request.setTransactionDateTime(commandDTO.getTransactionDateTime());
        if (commandDTO.getCardInfo() != null) {
            request.setCardNo(commandDTO.getCardInfo().getCardNo());
            request.setExpirationMonth(commandDTO.getCardInfo().getExpirationMonth());
            request.setExpirationYear(commandDTO.getCardInfo().getExpirationYear());
            request.setSecurityCode(commandDTO.getCardInfo().getSecurityCode());
            request.setCardholderName(commandDTO.getCardInfo().getCardholderName());
        }
        if (commandDTO.getTransactionInfo() != null) {
            request.setCardBrand(commandDTO.getTransactionInfo().getCardBrand());
            request.getExtension().put("targetTransactionId", emptyIfNull(commandDTO.getTransactionInfo().getSourceChannelTransactionId()));
        }
        request.setBillingInfo(toBillingInfo(commandDTO.getBillingCardHolderInfo()));
        request.setThreeDsInfo(toThreeDsInfo(commandDTO.getThreeDsInfo()));
        request.getExtension().put("callbackUrl", emptyIfNull(commandDTO.getCallbackUrl()));
        request.getExtension().put("sourceUrl", emptyIfNull(commandDTO.getSourceUrl()));
        request.getExtension().put("payerIp", emptyIfNull(commandDTO.getPayerIp()));
        request.getExtension().put("userAgent", emptyIfNull(commandDTO.getUserAgent()));
        request.getExtension().put("currencyExponent", resolveCurrencyExponent(request.getCurrency()));
        request.getExtension().put("midNo", emptyIfNull(routeResult.getMidNo()));
        request.getExtension().put("midConfigId", routeResult.getMidConfigId() == null ? "" : String.valueOf(routeResult.getMidConfigId()));
        request.getExtension().put("requestUrl", emptyIfNull(routeResult.getRequestUrl()));
        request.getExtension().put("connectTimeoutSeconds", routeResult.getConnectTimeoutSeconds() == null ? "" : String.valueOf(routeResult.getConnectTimeoutSeconds()));
        request.getExtension().put("readTimeoutSeconds", routeResult.getReadTimeoutSeconds() == null ? "" : String.valueOf(routeResult.getReadTimeoutSeconds()));
        for (Map.Entry<String, String> entry : routeResult.getMetadataValues().entrySet()) {
            request.getExtension().put("mid." + entry.getKey(), emptyIfNull(entry.getValue()));
        }
        return request;
    }

    /**
     * 将已持久化查询身份之外的非敏感渠道查询标识透传到统一请求。
     */
    private void copyPreparedExtensions(PaymentPreparedChannelRequestDTO preparedChannelRequest,
                                        ChannelPaymentRequest channelRequest) {
        if (preparedChannelRequest == null || channelRequest == null
                || preparedChannelRequest.getExtension() == null
                || preparedChannelRequest.getExtension().isEmpty()) {
            return;
        }
        channelRequest.getExtension().putAll(preparedChannelRequest.getExtension());
    }

    /**
     * 解析本次渠道交易 ID。
     * <p>
     * 正常交易动作必须生成新的渠道交易 ID；查询勾兑必须由调用方通过 preparedChannelRequest 传入原动作单已保存的
     * 渠道交易 ID，不能回退使用平台 transactionId。
     *
     * @param commandDTO 支付核心交易命令
     * @return 渠道交易 ID
     */
    private String resolveChannelTransactionId(PaymentCreateCommandDTO commandDTO) {
        if ("QUERY".equalsIgnoreCase(commandDTO.getTransactionType())) {
            return null;
        }
        return PaymentOrderNoGenerator.nextOrderNo(CHANNEL_TRANSACTION_ID_PREFIX);
    }

    /**
     * 根据交易动作解析渠道 HTTP 方法。
     * <p>
     * MPGS 查询使用 GET，授权、支付、请款、退款和撤销等动作使用 PUT 保证渠道侧幂等语义。
     *
     * @param commandDTO 支付核心交易命令
     * @return 渠道 HTTP 方法
     */
    private String resolveHttpMethod(PaymentCreateCommandDTO commandDTO) {
        return "QUERY".equalsIgnoreCase(commandDTO.getTransactionType()) ? "GET" : "PUT";
    }

    /**
     * 解析渠道请求场景，用于渠道请求摘要表和后台日志展示。
     *
     * @param commandDTO 支付核心交易命令
     * @return 渠道请求场景
     */
    private String resolveRequestScene(PaymentCreateCommandDTO commandDTO) {
        if ("QUERY".equalsIgnoreCase(commandDTO.getTransactionType())) {
            return "RETRIEVE";
        }
        return commandDTO.getTransactionType();
    }

    /**
     * 拼接脱敏后的渠道请求 URL。
     * <p>
     * URL 只包含平台生成的渠道订单号和渠道交易 ID，不包含渠道用户名、密码或认证头。
     *
     * @param routeResult 渠道路由结果
     * @param request     渠道统一请求
     * @return 脱敏渠道请求 URL
     */
    private String resolveRequestUrl(PaymentRouteResultDTO routeResult, ChannelPaymentRequest request) {
        if (routeResult == null || routeResult.getRequestUrl() == null || routeResult.getRequestUrl().isBlank()) {
            return null;
        }
        String baseUrl = routeResult.getRequestUrl().endsWith("/") ? routeResult.getRequestUrl() : routeResult.getRequestUrl() + "/";
        return baseUrl + "order/" + request.getChannelOrderNo() + "/transaction/" + request.getChannelTransactionId();
    }

    /**
     * 生成渠道请求脱敏摘要。
     * <p>
     * 摘要只输出路由、金额币种、卡号掩码、3DS 存在性和受控扩展字段；
     * 不输出完整 PAN、CVV、CAVV、callbackUrl query、渠道 MID 元数据或认证密钥。
     * </p>
     * @param request 渠道统一请求
     * @return 可写入业务日志的渠道请求摘要 JSON
     */
    private String channelRequestSummary(ChannelPaymentRequest request) {
        if (request == null) {
            return null;
        }
        ChannelRequestLogSummary summary = new ChannelRequestLogSummary(
                request.getChannelCode(),
                request.getTransactionId(),
                request.getOperationId(),
                request.getSourceTransactionId(),
                request.getMerchantOrderNo(),
                request.getMerchantOrderId(),
                request.getTransactionType(),
                request.getPaymentMethod(),
                request.getCurrency(),
                request.getAmount(),
                extension(request, "currencyExponent"),
                SensitiveDataMaskUtils.maskPan(request.getCardNo()),
                request.getExpirationMonth(),
                request.getExpirationYear(),
                request.getCardBrand(),
                request.getBillingInfo() == null ? null : request.getBillingInfo().getCountry(),
                request.getBillingInfo() == null
                        ? null : SensitiveDataMaskUtils.maskEmail(request.getBillingInfo().getEmail()),
                request.getBillingInfo() == null
                        ? null : SensitiveDataMaskUtils.maskMobile(request.getBillingInfo().getPhone()),
                request.getThreeDsInfo() == null ? null : request.getThreeDsInfo().getEci(),
                request.getThreeDsInfo() != null && StringUtils.hasText(request.getThreeDsInfo().getCavv()),
                extension(request, "payerIp"),
                maskUrl(extension(request, "sourceUrl")),
                maskUrl(extension(request, "callbackUrl")),
                endpointHost(extension(request, "requestUrl")),
                endpointPath(extension(request, "requestUrl")),
                extension(request, "midConfigId"),
                request.getExtension().keySet().stream().sorted().toList());
        return SensitiveDataMaskUtils.maskJsonSafely(JsonUtils.toJsonString(summary));
    }

    /**
     * 生成渠道响应脱敏摘要。
     * <p>
     * 摘要覆盖渠道状态、业务码、授权码、RRN、收单参考号和渠道返回的支付工具摘要；
     * 原始响应体只使用渠道适配层提供的脱敏版本。
     * </p>
     * @param response 渠道统一响应
     * @return 可写入业务日志的渠道响应摘要 JSON
     */
    private String channelResponseSummary(ChannelPaymentResponse response) {
        if (response == null) {
            return null;
        }
        ChannelResponseLogSummary summary = new ChannelResponseLogSummary(
                response.getChannelCode(),
                response.getTransactionId(),
                response.getChannelOrderNo(),
                response.getChannelTransactionId(),
                response.getChannelTradeStatus(),
                response.getRawChannelStatus(),
                response.getChannelResponseCode(),
                response.getChannelResponseMessage(),
                response.getAuthCode(),
                response.getRrn(),
                response.getAcquirerReferenceNo(),
                response.getHttpStatus(),
                response.getPaymentMethodSummary(),
                response.getResponseBodyJsonMasked());
        return SensitiveDataMaskUtils.maskJsonSafely(JsonUtils.toJsonString(summary));
    }

    /**
     * 读取渠道扩展字段。
     *
     * @param request 渠道统一请求
     * @param key 扩展字段键名
     * @return 扩展字段值
     */
    private String extension(ChannelPaymentRequest request, String key) {
        return request.getExtension() == null ? null : request.getExtension().get(key);
    }

    /**
     * 对 URL 查询参数进行脱敏。
     *
     * @param url 原始 URL
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
     * 提取 URL 主机名。
     *
     * @param url 原始 URL
     * @return 主机名或 invalid_url
     */
    private String endpointHost(String url) {
        if (!StringUtils.hasText(url)) {
            return null;
        }
        try {
            return URI.create(url).getHost();
        } catch (RuntimeException exception) {
            return "invalid_url";
        }
    }

    /**
     * 提取 URL path。
     *
     * @param url 原始 URL
     * @return path 或 invalid_url
     */
    private String endpointPath(String url) {
        if (!StringUtils.hasText(url)) {
            return null;
        }
        try {
            return URI.create(url).getPath();
        } catch (RuntimeException exception) {
            return "invalid_url";
        }
    }

    /**
     * 脱敏 MID 或类似短标识。
     *
     * @param value 原始值
     * @return 掩码值
     */
    private String maskShort(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() <= 6) {
            return "***";
        }
        return normalized.substring(0, 3) + "***" + normalized.substring(normalized.length() - 3);
    }

    /**
     * 计算渠道调用耗时。
     *
     * @param startTime 请求开始时间
     * @param endTime   响应或异常时间
     * @return 耗时毫秒，超过 int 上限时截断
     */
    private int durationMillis(LocalDateTime startTime, LocalDateTime endTime) {
        if (startTime == null || endTime == null) {
            return 0;
        }
        long millis = java.time.Duration.between(startTime, endTime).toMillis();
        return millis > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) millis;
    }

    /**
     * 转换账单持卡人信息。
     * <p>
     * 该信息会进入渠道请求日志，落库前仍需经过统一 JSON 脱敏。
     *
     * @param source OpenAPI 账单持卡人信息
     * @return 渠道账单持卡人信息
     */
    private ChannelPaymentRequest.BillingInfo toBillingInfo(PaymentCreateCommandDTO.BillingCardHolderInfoDTO source) {
        if (source == null) {
            return null;
        }
        ChannelPaymentRequest.BillingInfo target = new ChannelPaymentRequest.BillingInfo();
        target.setFirstName(source.getFirstName());
        target.setLastName(source.getLastName());
        target.setPhone(source.getPhone());
        target.setEmail(source.getEmail());
        target.setCountry(source.getCountry());
        target.setState(source.getState());
        target.setCity(source.getCity());
        target.setStreet(source.getStreet());
        target.setPostal(source.getPostal());
        return target;
    }

    /**
     * 转换 3DS 信息。
     * <p>
     * CAVV、认证令牌等值属于敏感认证数据，后续日志入库必须通过统一脱敏工具处理。
     *
     * @param source OpenAPI 3DS 信息
     * @return 渠道 3DS 信息
     */
    private ChannelPaymentRequest.ThreeDsInfo toThreeDsInfo(PaymentCreateCommandDTO.ThreeDsInfoDTO source) {
        if (source == null) {
            return null;
        }
        ChannelPaymentRequest.ThreeDsInfo target = new ChannelPaymentRequest.ThreeDsInfo();
        target.setAuthenticationTransactionId(source.getAuthenticationTransactionId());
        target.setEci(source.getEci());
        target.setCavv(source.getCavv());
        target.setDsTransactionId(source.getDsTransactionId());
        target.setThreeDsVersion(source.getThreeDsVersion());
        return target;
    }

    /**
     * 将空值转换为空字符串，避免渠道扩展参数 Map 出现 null 值。
     *
     * @param value 原始值
     * @return 非 null 字符串
     */
    private String emptyIfNull(String value) {
        return value == null ? "" : value;
    }

    /**
     * 从统一 ISO 币种定义解析交易币种辅币位并透传给渠道库。
     * <p>
     * 渠道库不直接访问 payment 数据库；这里把支付核心已经使用的币种规则下沉到渠道请求，避免渠道最小单位金额默认按两位小数换算。
     * </p>
     * @param currency 交易币种
     * @return 辅币位字符串；无法解析时返回空字符串，由渠道库再次按 ISO resolver 校验
     */
    private String resolveCurrencyExponent(String currency) {
        if (!StringUtils.hasText(currency)) {
            return "";
        }
        if (isoDictionaryService != null) {
            return isoDictionaryService.getCurrency(currency)
                    .map(IsoCurrencyInfo::defaultFractionDigits)
                    .filter(exponent -> exponent >= 0)
                    .map(String::valueOf)
                    .orElse("");
        }
        return IsoCurrencyResolver.resolve(currency)
                .map(IsoCurrencyInfo::defaultFractionDigits)
                .filter(exponent -> exponent >= 0)
                .map(String::valueOf)
                .orElse("");
    }

    /**
     * 按优先级返回第一个非空白文本，渠道响应未回传字段时保留请求阶段已有值。
     */
    private String firstText(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    /**
     * 渠道请求的脱敏日志协议。
     *
     * @param channelCode 渠道编码
     * @param transactionId 平台交易号
     * @param operationId 平台动作号
     * @param sourceTransactionId 原平台交易号
     * @param merchantOrderNo 商户订单号
     * @param merchantOrderId 商户请求唯一标识
     * @param transactionType 交易类型
     * @param paymentMethod 支付方式
     * @param currency 上送渠道币种
     * @param amount 上送渠道金额
     * @param currencyExponent 币种辅币位
     * @param cardNoMasked 卡号掩码
     * @param expirationMonth 卡有效期月份
     * @param expirationYear 卡有效期年份
     * @param cardBrand 卡品牌
     * @param billingCountry 账单国家或地区
     * @param billingEmail 账单邮箱掩码
     * @param billingPhone 账单电话掩码
     * @param threeDsEci 3DS ECI
     * @param threeDsCavvPresent 是否携带 CAVV
     * @param payerIp 付款人 IP，序列化后还会经过统一脱敏
     * @param sourceUrl 不含查询参数的来源地址
     * @param callbackUrl 不含查询参数的回调地址
     * @param requestUrlHost 渠道请求主机
     * @param requestUrlPath 渠道请求路径
     * @param midConfigId 渠道 MID 配置主键
     * @param extensionKeys 渠道扩展字段名集合
     */
    private record ChannelRequestLogSummary(String channelCode,
                                            String transactionId,
                                            String operationId,
                                            String sourceTransactionId,
                                            String merchantOrderNo,
                                            String merchantOrderId,
                                            String transactionType,
                                            String paymentMethod,
                                            String currency,
                                            BigDecimal amount,
                                            String currencyExponent,
                                            String cardNoMasked,
                                            String expirationMonth,
                                            String expirationYear,
                                            String cardBrand,
                                            String billingCountry,
                                            String billingEmail,
                                            String billingPhone,
                                            String threeDsEci,
                                            boolean threeDsCavvPresent,
                                            String payerIp,
                                            String sourceUrl,
                                            String callbackUrl,
                                            String requestUrlHost,
                                            String requestUrlPath,
                                            String midConfigId,
                                            List<String> extensionKeys) {
    }

    /**
     * 渠道响应的脱敏日志协议。
     *
     * @param channelCode 渠道编码
     * @param transactionId 平台交易号
     * @param channelOrderNo 渠道商户订单号
     * @param channelTransactionId 渠道交易号
     * @param channelTradeStatus 标准渠道交易状态
     * @param rawChannelStatus 渠道原始状态编码
     * @param channelResponseCode 渠道业务响应码
     * @param channelResponseMessage 渠道业务响应描述
     * @param authCode 授权码
     * @param rrn 渠道检索参考号
     * @param acquirerReferenceNo 收单参考号
     * @param httpStatus HTTP 状态码
     * @param paymentMethodSummary 支付工具脱敏摘要
     * @param responseBodyMasked 渠道适配层提供的脱敏响应摘要
     */
    private record ChannelResponseLogSummary(String channelCode,
                                             String transactionId,
                                             String channelOrderNo,
                                             String channelTransactionId,
                                             String channelTradeStatus,
                                             String rawChannelStatus,
                                             String channelResponseCode,
                                             String channelResponseMessage,
                                             String authCode,
                                             String rrn,
                                             String acquirerReferenceNo,
                                             Integer httpStatus,
                                             ChannelPaymentResponse.PaymentMethodSummary paymentMethodSummary,
                                             String responseBodyMasked) {
    }

    /**
     * 渠道调用异常包装，保留审计上下文后继续让上层事务按失败或超时语义处理。
     */
    public static class PaymentChannelInvokeException extends RuntimeException {

        /**
         * 渠道交互的结构化审计结果，原始异常正文不会写入该对象。
         */
        private final PaymentChannelInvokeResultDTO invokeResult;

        /**
         * 创建渠道调用异常包装。
         *
         * @param invokeResult 渠道调用上下文，包含请求、响应和异常摘要
         * @param cause        原始渠道异常
         */
        public PaymentChannelInvokeException(PaymentChannelInvokeResultDTO invokeResult, Throwable cause) {
            super(cause == null ? null : cause.getClass().getSimpleName(), cause);
            this.invokeResult = invokeResult;
        }

        /**
         * 获取渠道调用上下文。
         *
         * @return 渠道调用上下文，供上层落渠道日志和流程事件
         */
        public PaymentChannelInvokeResultDTO getInvokeResult() {
            return invokeResult;
        }
    }
}
