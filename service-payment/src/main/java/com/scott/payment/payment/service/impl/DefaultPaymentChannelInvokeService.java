package com.scott.payment.payment.service.impl;

import com.scott.payment.channel.payment.dto.request.ChannelPaymentRequest;
import com.scott.payment.channel.payment.dto.response.ChannelPaymentResponse;
import com.scott.payment.channel.payment.executor.PaymentChannelExecutor;
import com.scott.payment.channel.payment.exception.ChannelException;
import com.scott.payment.channel.payment.exception.ChannelTimeoutException;
import com.scott.payment.component.core.util.identity.PaymentOrderNoGenerator;
import com.scott.payment.payment.api.internal.dto.PaymentCreateCommandDTO;
import com.scott.payment.payment.service.PaymentChannelInvokeService;
import com.scott.payment.payment.service.dto.PaymentChannelInvokeResultDTO;
import com.scott.payment.payment.service.dto.PaymentPreparedChannelRequestDTO;
import com.scott.payment.payment.service.dto.PaymentRouteResultDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : DefaultPaymentChannelInvokeService
 * @date : 2026-07-12 00:00
 * @email : scott_x@163.com
 * @description : 收单渠道调用默认实现，位于 service-payment 服务实现层，负责把支付核心上下文转换为渠道请求并通过 PaymentChannelExecutor 调用渠道 SPI。
 * @status : create
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

    /**
     * payment Channel Executor 字段，表示当前模型在所属业务流程中的对应属性。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private final PaymentChannelExecutor paymentChannelExecutor;

    /**
     * 创建渠道调用服务。
     *
     * @param paymentChannelExecutor 渠道执行器
     */
    public DefaultPaymentChannelInvokeService(PaymentChannelExecutor paymentChannelExecutor) {
        this.paymentChannelExecutor = paymentChannelExecutor;
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
        PaymentChannelInvokeResultDTO resultDTO = new PaymentChannelInvokeResultDTO();
        resultDTO.setRequestId(preparedChannelRequest == null ? null : preparedChannelRequest.getRequestId());
        resultDTO.setChannelRequest(channelRequest);
        resultDTO.setRequestStartTime(LocalDateTime.now());
        resultDTO.setHttpMethod(resolveHttpMethod(commandDTO));
        resultDTO.setRequestScene(resolveRequestScene(commandDTO));
        resultDTO.setRequestUrlMasked(resolveRequestUrl(routeResult, channelRequest));
        log.info("event: PAYMENT_CHANNEL_REQUEST_START merchantId: {} merchantOrderNo: {} transactionId: {} operationId: {} transactionType: {} channelCode: {} requestId: {} channelOrderNo: {} channelTransactionId: {} httpMethod: {} requestScene: {}",
                commandDTO.getMerchantId(),
                commandDTO.getMerchantOrderNo(),
                transactionId,
                operationId,
                commandDTO.getTransactionType(),
                channelRequest.getChannelCode(),
                resultDTO.getRequestId(),
                channelRequest.getChannelOrderNo(),
                channelRequest.getChannelTransactionId(),
                resultDTO.getHttpMethod(),
                resultDTO.getRequestScene());
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
            log.info("event: PAYMENT_CHANNEL_REQUEST_END merchantId: {} merchantOrderNo: {} transactionId: {} operationId: {} transactionType: {} channelCode: {} requestId: {} channelOrderNo: {} channelTransactionId: {} channelResponseCode: {} rawChannelStatus: {} channelTradeStatus: {} httpStatus: {} requestStatus: {} durationMs: {}",
                    commandDTO.getMerchantId(),
                    commandDTO.getMerchantOrderNo(),
                    transactionId,
                    operationId,
                    commandDTO.getTransactionType(),
                    channelRequest.getChannelCode(),
                    resultDTO.getRequestId(),
                    channelRequest.getChannelOrderNo(),
                    channelRequest.getChannelTransactionId(),
                    channelResponse == null ? null : channelResponse.getChannelResponseCode(),
                    channelResponse == null ? null : channelResponse.getRawChannelStatus(),
                    channelResponse == null ? null : channelResponse.getChannelTradeStatus(),
                    channelResponse == null ? null : channelResponse.getHttpStatus(),
                    resultDTO.getRequestStatus(),
                    resultDTO.getDurationMillis());
            return resultDTO;
        } catch (ChannelException exception) {
            LocalDateTime responseTime = LocalDateTime.now();
            resultDTO.setRequestStatus(exception instanceof ChannelTimeoutException ? "TIMEOUT" : "FAILED");
            resultDTO.setResponseTime(responseTime);
            resultDTO.setDurationMillis(durationMillis(resultDTO.getRequestStartTime(), responseTime));
            resultDTO.setExceptionType(exception.getClass().getSimpleName());
            resultDTO.setExceptionMessage(exception.getMessage());
            log.warn("event: PAYMENT_CHANNEL_REQUEST_FAILED merchantId: {} merchantOrderNo: {} transactionId: {} operationId: {} transactionType: {} channelCode: {} requestId: {} channelOrderNo: {} channelTransactionId: {} requestStatus: {} exceptionType: {} durationMs: {}",
                    commandDTO.getMerchantId(),
                    commandDTO.getMerchantOrderNo(),
                    transactionId,
                    operationId,
                    commandDTO.getTransactionType(),
                    channelRequest.getChannelCode(),
                    resultDTO.getRequestId(),
                    channelRequest.getChannelOrderNo(),
                    channelRequest.getChannelTransactionId(),
                    resultDTO.getRequestStatus(),
                    resultDTO.getExceptionType(),
                    resultDTO.getDurationMillis());
            throw new PaymentChannelInvokeException(resultDTO, exception);
        }
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
     * 执行 first Text 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * <p>
     * 层级边界：支付核心服务层；输入来源、输出结构和异常语义由 DefaultPaymentChannelInvokeService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param values values 输入值，含义由调用方法名称和所属业务对象限定
     * @return 方法签名声明的返回值，具体结构由返回类型定义
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
     * 渠道调用异常包装，保留审计上下文后继续让上层事务按失败或超时语义处理。
     */
    public static class PaymentChannelInvokeException extends RuntimeException {

        /**
         * invoke Result 字段，表示当前模型在所属业务流程中的对应属性。
         * <p>
         * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
         * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
         * </p>
         */
        private final PaymentChannelInvokeResultDTO invokeResult;

        /**
         * 创建渠道调用异常包装。
         *
         * @param invokeResult 渠道调用上下文，包含请求、响应和异常摘要
         * @param cause        原始渠道异常
         */
        public PaymentChannelInvokeException(PaymentChannelInvokeResultDTO invokeResult, Throwable cause) {
            super(cause == null ? null : cause.getMessage(), cause);
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
