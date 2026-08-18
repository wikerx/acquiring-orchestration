package com.scott.payment.payment.service.impl;

import com.scott.payment.channel.payment.dto.request.ChannelPaymentRequest;
import com.scott.payment.channel.payment.dto.request.ChannelThreeDsAuthenticationRequest;
import com.scott.payment.channel.payment.dto.response.ChannelThreeDsAuthenticationResponse;
import com.scott.payment.channel.payment.enums.ChannelThreeDsPhase;
import com.scott.payment.channel.payment.enums.ChannelThreeDsStatus;
import com.scott.payment.channel.payment.exception.ChannelException;
import com.scott.payment.channel.payment.executor.PaymentChannelExecutor;
import com.scott.payment.component.db.systemconfig.service.SystemConfigReadService;
import com.scott.payment.payment.api.internal.dto.PaymentCheckoutPaymentSubmitCommandDTO;
import com.scott.payment.payment.api.internal.dto.PaymentCreateCommandDTO;
import com.scott.payment.payment.client.risk.RiskInternalClient;
import com.scott.payment.payment.client.risk.dto.RiskThreeDsPolicyClientRequestDTO;
import com.scott.payment.payment.client.risk.dto.RiskThreeDsPolicyClientResponseDTO;
import com.scott.payment.payment.entity.PaymentCheckoutAttemptDO;
import com.scott.payment.payment.entity.PaymentCheckoutSessionDO;
import com.scott.payment.payment.service.PaymentChannelRouteService;
import com.scott.payment.payment.service.PaymentAuthenticationRecordService;
import com.scott.payment.payment.service.PaymentCheckoutThreeDsService;
import com.scott.payment.payment.service.dto.PaymentCheckoutThreeDsResultDTO;
import com.scott.payment.payment.service.dto.PaymentRouteResultDTO;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : DefaultPaymentCheckoutThreeDsService
 * @date : 2026-08-11 00:00
 * @email : scott_x@163.com
 * @description : Hosted Checkout 统一 3DS 后端阶段服务，负责路由、策略判断和无浏览器交互阶段编排。
 * @status : create
 */
@Service
@Slf4j
public class DefaultPaymentCheckoutThreeDsService implements PaymentCheckoutThreeDsService {

    private static final String GATEWAY_BASE_URL_CONFIG_KEY = "platform.gateway.base-url";
    private static final String THREE_DS_CALLBACK_PATH_PREFIX = "/channel/v1/callbacks/";

    /** 统一渠道执行器，根据路由结果定位 3DS provider。 */
    private final PaymentChannelExecutor paymentChannelExecutor;

    /** 支付渠道路由服务，用于选择并固化本次 3DS 使用的渠道和 MID。 */
    private final PaymentChannelRouteService paymentChannelRouteService;

    /** service-risk 路由后 3DS 策略只读客户端。 */
    private final RiskInternalClient riskInternalClient;

    /** Platform-owned configuration used to construct provider Webhook URLs. */
    private final SystemConfigReadService systemConfigReadService;

    /** 平台认证审计服务，每次渠道 3DS 阶段调用后只落安全摘要。 */
    private final PaymentAuthenticationRecordService authenticationRecordService;

    /**
     * 创建 Hosted Checkout 统一 3DS 编排服务。
     *
     * @param paymentChannelExecutor    统一渠道执行器
     * @param paymentChannelRouteService 支付渠道路由服务
     * @param riskInternalClient         service-risk 内部客户端
     */
    @Autowired
    public DefaultPaymentCheckoutThreeDsService(PaymentChannelExecutor paymentChannelExecutor,
                                                PaymentChannelRouteService paymentChannelRouteService,
                                                RiskInternalClient riskInternalClient,
                                                SystemConfigReadService systemConfigReadService,
                                                PaymentAuthenticationRecordService authenticationRecordService) {
        this.paymentChannelExecutor = paymentChannelExecutor;
        this.paymentChannelRouteService = paymentChannelRouteService;
        this.riskInternalClient = riskInternalClient;
        this.systemConfigReadService = systemConfigReadService;
        this.authenticationRecordService = authenticationRecordService;
    }

    /** Constructor retained for isolated unit tests that do not exercise provider Webhook URL mapping. */
    DefaultPaymentCheckoutThreeDsService(PaymentChannelExecutor paymentChannelExecutor,
                                         PaymentChannelRouteService paymentChannelRouteService,
                                         RiskInternalClient riskInternalClient) {
        this(paymentChannelExecutor, paymentChannelRouteService, riskInternalClient, null, null);
    }

    DefaultPaymentCheckoutThreeDsService(PaymentChannelExecutor paymentChannelExecutor,
                                         PaymentChannelRouteService paymentChannelRouteService,
                                         RiskInternalClient riskInternalClient,
                                         SystemConfigReadService systemConfigReadService) {
        this(paymentChannelExecutor, paymentChannelRouteService, riskInternalClient,
                systemConfigReadService, null);
    }

    /**
     * 路由渠道和 MID，并通过统一 Channel SPI 发起 3DS 初始化。
     *
     * <p>PAN、CVV、CAVV 和账单资料只在当前内存调用链传递，不得写入日志、缓存或业务表。
     * 渠道异常按 PROCESSING 返回，等待可靠查询或回调确认，不能直接宣告支付失败或成功。
     * 3DS Method/Challenge 页面执行和回跳恢复完成前，不允许由本服务触发 PAY 或 AUTHORIZE。</p>
     *
     * @param sessionDO 收银台会话快照
     * @param attemptDO 本次付款尝试快照
     * @param commandDTO 付款人提交的卡信息和浏览器上下文
     * @param returnUrl 认证完成后回到平台 3DS bridge 的地址
     * @return 平台收银台 3DS 认证摘要
     */
    @Override
    public PaymentCheckoutThreeDsResultDTO authenticate(PaymentCheckoutSessionDO sessionDO,
                                                        PaymentCheckoutAttemptDO attemptDO,
                                                        PaymentCheckoutPaymentSubmitCommandDTO commandDTO,
                                                        String returnUrl,
                                                        PaymentRouteResultDTO preparedRoute) {
        PaymentRouteResultDTO routeResultDTO = preparedRoute != null
                ? preparedRoute
                : attemptDO.getChannelMidConfigId() == null
                ? paymentChannelRouteService.route(routeCommand(sessionDO, attemptDO, commandDTO))
                : paymentChannelRouteService.restore(
                        attemptDO.getChannelCode(), null, attemptDO.getChannelMidConfigId(), null);
        RiskThreeDsPolicyClientResponseDTO policy = riskInternalClient.evaluateThreeDsPolicy(
                policyRequest(sessionDO, attemptDO, routeResultDTO));
        if (policy == null || !policy.isRequired()) {
            return routeResult(new PaymentCheckoutThreeDsResultDTO(), routeResultDTO, policy, "NOT_REQUIRED");
        }
        if (!routeResultDTO.isThreeDsSupported()
                || !paymentChannelExecutor.supports(
                routeResultDTO.getChannelCode(),
                com.scott.payment.channel.payment.enums.ChannelCapability.THREE_DS_AUTHENTICATION)) {
            PaymentCheckoutThreeDsResultDTO result = routeResult(
                    new PaymentCheckoutThreeDsResultDTO(), routeResultDTO, policy, "FAILED");
            result.setFailureCode("THREE_DS_CAPABILITY_UNAVAILABLE");
            result.setFailureMessage("routed channel does not support required 3DS authentication");
            return result;
        }
        ChannelThreeDsAuthenticationRequest request = buildRequest(
                sessionDO, attemptDO, commandDTO, returnUrl, routeResultDTO, ChannelThreeDsPhase.INITIALIZE);
        return invoke(request, routeResultDTO, policy, true);
    }

    @Override
    public PaymentCheckoutThreeDsResultDTO continueAuthentication(PaymentCheckoutSessionDO sessionDO,
                                                                  PaymentCheckoutAttemptDO attemptDO,
                                                                  PaymentCheckoutPaymentSubmitCommandDTO commandDTO,
                                                                  String returnUrl,
                                                                  ChannelThreeDsPhase phase) {
        if (phase == null || ChannelThreeDsPhase.INITIALIZE.equals(phase)) {
            throw new IllegalArgumentException("3DS continuation phase must be AUTHENTICATE or VERIFY");
        }
        PaymentRouteResultDTO routeResultDTO = paymentChannelRouteService.restore(
                attemptDO.getChannelCode(), null, attemptDO.getChannelMidConfigId(), null);
        if (!paymentChannelExecutor.supports(routeResultDTO.getChannelCode(),
                com.scott.payment.channel.payment.enums.ChannelCapability.THREE_DS_AUTHENTICATION)) {
            PaymentCheckoutThreeDsResultDTO result = routeResult(
                    new PaymentCheckoutThreeDsResultDTO(), routeResultDTO, null, "FAILED");
            result.setThreeDsPolicyAction("FORCE_3DS");
            result.setFailureCode("THREE_DS_CAPABILITY_UNAVAILABLE");
            return result;
        }
        ChannelThreeDsAuthenticationRequest request = buildRequest(
                sessionDO, attemptDO, commandDTO, returnUrl, routeResultDTO, phase);
        PaymentCheckoutThreeDsResultDTO result = invoke(request, routeResultDTO, null, false);
        result.setThreeDsPolicyAction("FORCE_3DS");
        return result;
    }

    /** 执行单个服务端阶段；仅初始调用允许在无需 Method 时紧接 AUTHENTICATE。 */
    private PaymentCheckoutThreeDsResultDTO invoke(ChannelThreeDsAuthenticationRequest request,
                                                   PaymentRouteResultDTO routeResultDTO,
                                                   RiskThreeDsPolicyClientResponseDTO policy,
                                                   boolean authenticateWhenReady) {
        try {
            ChannelThreeDsAuthenticationResponse response = paymentChannelExecutor.authenticateThreeDs(request);
            recordChannelResult(request, response);
            if (authenticateWhenReady && response != null
                    && ChannelThreeDsStatus.READY_TO_AUTHENTICATE.equals(response.getStatus())) {
                request.setPhase(ChannelThreeDsPhase.AUTHENTICATE);
                response = paymentChannelExecutor.authenticateThreeDs(request);
                recordChannelResult(request, response);
            }
            return routeResult(copy(response), routeResultDTO, policy, null);
        } catch (ChannelException exception) {
            ChannelThreeDsStatus failureStatus = exception.isOutcomeUncertain()
                    ? ChannelThreeDsStatus.PROCESSING : ChannelThreeDsStatus.FAILED;
            recordChannelFailure(request, failureStatus, exception.getClass().getSimpleName());
            PaymentCheckoutThreeDsResultDTO result = new PaymentCheckoutThreeDsResultDTO();
            result.setPhase(request.getPhase().name());
            result.setStatus(failureStatus.name());
            result.setAuthenticationTransactionId(request.getAuthenticationTransactionId());
            result.setChannelOrderNo(request.getChannelOrderNo());
            result.setFailureCode(exception.getClass().getSimpleName());
            result.setFailureMessage(exception.getMessage());
            return routeResult(result, routeResultDTO, policy, null);
        }
    }

    private void recordChannelResult(ChannelThreeDsAuthenticationRequest request,
                                     ChannelThreeDsAuthenticationResponse response) {
        if (authenticationRecordService != null) {
            try {
                authenticationRecordService.recordChannelResult(request, response);
            } catch (RuntimeException exception) {
                log.warn("event: THREE_DS_AUDIT_WRITE_FAILED transactionId: {} phase: {} errorType: {}",
                        request.getTransactionId(),
                        response != null && response.getPhase() != null
                                ? response.getPhase() : request.getPhase(),
                        exception.getClass().getSimpleName());
            }
        }
    }

    private void recordChannelFailure(ChannelThreeDsAuthenticationRequest request,
                                      ChannelThreeDsStatus status,
                                      String failureCode) {
        if (authenticationRecordService != null) {
            try {
                authenticationRecordService.recordChannelFailure(request, status, failureCode);
            } catch (RuntimeException exception) {
                log.warn("event: THREE_DS_AUDIT_WRITE_FAILED transactionId: {} phase: {} errorType: {}",
                        request.getTransactionId(), request.getPhase(), exception.getClass().getSimpleName());
            }
        }
    }

    /** 构造不含 PAN、CVV 或渠道凭据的 3DS 策略请求。 */
    private RiskThreeDsPolicyClientRequestDTO policyRequest(PaymentCheckoutSessionDO sessionDO,
                                                            PaymentCheckoutAttemptDO attemptDO,
                                                            PaymentRouteResultDTO routeResultDTO) {
        RiskThreeDsPolicyClientRequestDTO request = new RiskThreeDsPolicyClientRequestDTO();
        request.setMerchantId(sessionDO.getMerchantId());
        request.setChannelCode(routeResultDTO.getChannelCode());
        request.setPaymentMethod(attemptDO.getPaymentMethod());
        request.setCardBrand(attemptDO.getPaymentBrand());
        request.setAmount(sessionDO.getLabelAmount());
        request.setCurrency(sessionDO.getLabelCurrency());
        request.setCurrentRiskLevel("LOW");
        return request;
    }

    /** 将路由身份和只读策略摘要附加到每一种 3DS 结果。 */
    private PaymentCheckoutThreeDsResultDTO routeResult(PaymentCheckoutThreeDsResultDTO result,
                                                        PaymentRouteResultDTO route,
                                                        RiskThreeDsPolicyClientResponseDTO policy,
                                                        String statusOverride) {
        if (statusOverride != null) {
            result.setStatus(statusOverride);
        }
        result.setChannelCode(route.getChannelCode());
        result.setChannelId(route.getChannelId());
        result.setChannelMidConfigId(route.getMidConfigId());
        result.setThreeDsPolicyAction(policy == null ? "NONE" : policy.getAction());
        result.setThreeDsPolicyRuleId(policy == null ? null : policy.getRuleId());
        return result;
    }

    /**
     * 构造只供平台渠道路由使用的支付命令，不包含 PAN、CVV 或渠道协议字段。
     *
     * @param sessionDO 收银台会话快照
     * @param attemptDO 本次付款尝试快照
     * @param commandDTO 付款人提交的账单上下文
     * @return 渠道路由命令
     */
    private PaymentCreateCommandDTO routeCommand(PaymentCheckoutSessionDO sessionDO,
                                                 PaymentCheckoutAttemptDO attemptDO,
                                                 PaymentCheckoutPaymentSubmitCommandDTO commandDTO) {
        PaymentCreateCommandDTO routeCommand = new PaymentCreateCommandDTO();
        routeCommand.setMerchantId(sessionDO.getMerchantId());
        routeCommand.setMerchantOrderNo(sessionDO.getMerchantOrderNo());
        routeCommand.setMerchantOrderId(attemptDO.getAttemptRequestId());
        routeCommand.setTransactionId(attemptDO.getTransactionId());
        routeCommand.setTransactionType(sessionDO.getPaymentAction());
        routeCommand.setPaymentMethod(attemptDO.getPaymentMethod());
        routeCommand.setAmount(sessionDO.getLabelAmount());
        routeCommand.setCurrency(sessionDO.getLabelCurrency());
        routeCommand.setTransactionDateTime(attemptDO.getTransactionDateTime());
        PaymentCreateCommandDTO.TransactionInfoDTO transactionInfo = new PaymentCreateCommandDTO.TransactionInfoDTO();
        transactionInfo.setCardBrand(attemptDO.getPaymentBrand());
        routeCommand.setTransactionInfo(transactionInfo);
        routeCommand.setBillingCardHolderInfo(toPaymentBillingInfo(commandDTO.getBillingCardHolderInfo()));
        return routeCommand;
    }

    /**
     * 构造平台统一 3DS 请求，完整卡号和 CVV 只写入瞬时内存对象。
     */
    private ChannelThreeDsAuthenticationRequest buildRequest(PaymentCheckoutSessionDO sessionDO,
                                                             PaymentCheckoutAttemptDO attemptDO,
                                                             PaymentCheckoutPaymentSubmitCommandDTO commandDTO,
                                                             String returnUrl,
                                                             PaymentRouteResultDTO routeResultDTO,
                                                             ChannelThreeDsPhase phase) {
        ChannelThreeDsAuthenticationRequest request = new ChannelThreeDsAuthenticationRequest();
        request.setPhase(phase);
        request.setChannelCode(routeResultDTO.getChannelCode());
        request.setOperationId(attemptDO.getOperationId());
        request.setTransactionId(attemptDO.getTransactionId());
        request.setChannelOrderNo(StringUtils.hasText(attemptDO.getChannelOrderNo())
                ? attemptDO.getChannelOrderNo() : attemptDO.getTransactionId());
        request.setAuthenticationTransactionId(authenticationTransactionId(attemptDO));
        request.setMerchantId(sessionDO.getMerchantId());
        request.setMerchantOrderNo(sessionDO.getMerchantOrderNo());
        request.setMerchantOrderId(attemptDO.getAttemptRequestId());
        request.setPaymentMethod(attemptDO.getPaymentMethod());
        request.setAmount(sessionDO.getLabelAmount());
        request.setCurrency(sessionDO.getLabelCurrency());
        request.setTransactionDateTime(attemptDO.getTransactionDateTime());
        request.setCardBrand(attemptDO.getPaymentBrand());
        request.setRedirectResponseUrl(returnUrl);
        if (ChannelThreeDsPhase.INITIALIZE.equals(phase)) {
            request.setNotificationUrl(notificationUrl(routeResultDTO.getChannelCode()));
        }
        request.setBrowserInfoJson(commandDTO.getBrowserInfoJson());
        request.getExtension().put("requestUrl", emptyIfNull(routeResultDTO.getRequestUrl()));
        request.getExtension().put("connectTimeoutSeconds", routeResultDTO.getConnectTimeoutSeconds() == null
                ? "" : String.valueOf(routeResultDTO.getConnectTimeoutSeconds()));
        request.getExtension().put("readTimeoutSeconds", routeResultDTO.getReadTimeoutSeconds() == null
                ? "" : String.valueOf(routeResultDTO.getReadTimeoutSeconds()));
        request.getExtension().put("midNo", emptyIfNull(routeResultDTO.getMidNo()));
        for (var entry : routeResultDTO.getMetadataValues().entrySet()) {
            request.getExtension().put("mid." + entry.getKey(), emptyIfNull(entry.getValue()));
        }
        if (commandDTO.getCardInfo() != null) {
            request.setCardNo(commandDTO.getCardInfo().getCardNo());
            request.setExpirationMonth(commandDTO.getCardInfo().getExpirationMonth());
            request.setExpirationYear(commandDTO.getCardInfo().getExpirationYear());
            request.setSecurityCode(commandDTO.getCardInfo().getSecurityCode());
            request.setCardholderName(commandDTO.getCardInfo().getCardholderName());
        }
        request.setPayerIp(commandDTO.getPayerIp());
        request.setBillingInfo(toBillingInfo(commandDTO.getBillingCardHolderInfo()));
        return request;
    }

    /** Build an optional provider callback URL from the platform-owned HTTPS gateway origin. */
    private String notificationUrl(String channelCode) {
        if (systemConfigReadService == null) {
            return null;
        }
        String configured = systemConfigReadService.findEnabledValue(GATEWAY_BASE_URL_CONFIG_KEY)
                .orElse(null);
        if (!StringUtils.hasText(configured)) {
            return null;
        }
        try {
            URI base = new URI(configured.trim());
            if (!"https".equalsIgnoreCase(base.getScheme())
                    || !StringUtils.hasText(base.getHost())
                    || base.getUserInfo() != null
                    || base.getQuery() != null
                    || base.getFragment() != null
                    || (StringUtils.hasText(base.getPath()) && !"/".equals(base.getPath()))) {
                log.warn("event: THREE_DS_NOTIFICATION_URL_SKIPPED configKey: {} channelCode: {} reason: UNSAFE_ORIGIN",
                        GATEWAY_BASE_URL_CONFIG_KEY, channelCode);
                return null;
            }
            return new URI("https", null, base.getHost(), base.getPort(),
                    THREE_DS_CALLBACK_PATH_PREFIX + channelCode + "/3ds", null, null).toString();
        } catch (URISyntaxException exception) {
            log.warn("event: THREE_DS_NOTIFICATION_URL_SKIPPED configKey: {} channelCode: {} reason: INVALID_URI exceptionType: {}",
                    GATEWAY_BASE_URL_CONFIG_KEY, channelCode, exception.getClass().getSimpleName());
            return null;
        }
    }

    /**
     * 将收银台账单资料转换为支付路由模型。
     *
     * @param source 收银台账单资料
     * @return 路由账单资料；输入为空时返回 null
     */
    private PaymentCreateCommandDTO.BillingCardHolderInfoDTO toPaymentBillingInfo(
            PaymentCheckoutPaymentSubmitCommandDTO.BillingCardHolderInfoDTO source) {
        if (source == null) {
            return null;
        }
        PaymentCreateCommandDTO.BillingCardHolderInfoDTO target = new PaymentCreateCommandDTO.BillingCardHolderInfoDTO();
        target.setFirstName(source.getFirstName());
        target.setLastName(source.getLastName());
        target.setEmail(source.getEmail());
        target.setPhone(source.getPhone());
        target.setCountry(source.getCountry());
        target.setState(source.getState());
        target.setCity(source.getCity());
        target.setStreet(source.getStreet());
        target.setPostal(source.getPostal());
        return target;
    }

    /**
     * 复用已持久化的 3DS 交易号；首次认证时按平台交易号生成稳定标识。
     *
     * @param attemptDO 本次付款尝试快照
     * @return 稳定的 3DS authentication transaction id
     */
    private String authenticationTransactionId(PaymentCheckoutAttemptDO attemptDO) {
        if (StringUtils.hasText(attemptDO.getThreeDsTransactionId())) {
            return attemptDO.getThreeDsTransactionId();
        }
        return "3DS" + attemptDO.getTransactionId();
    }

    /**
     * 将收银台账单资料转换为渠道 API 公共账单模型，卡号和 CVV 不由该对象承载。
     *
     * @param source 收银台账单资料
     * @return 渠道公共账单资料；输入为空时返回 null
     */
    private ChannelPaymentRequest.BillingInfo toBillingInfo(
            PaymentCheckoutPaymentSubmitCommandDTO.BillingCardHolderInfoDTO source) {
        if (source == null) {
            return null;
        }
        ChannelPaymentRequest.BillingInfo target = new ChannelPaymentRequest.BillingInfo();
        target.setFirstName(source.getFirstName());
        target.setLastName(source.getLastName());
        target.setEmail(source.getEmail());
        target.setPhone(source.getPhone());
        target.setCountry(source.getCountry());
        target.setState(source.getState());
        target.setCity(source.getCity());
        target.setStreet(source.getStreet());
        target.setPostal(source.getPostal());
        return target;
    }

    /**
     * 将统一渠道 3DS 响应转换为现有收银台结果 DTO，保持下游状态机字段兼容。
     *
     * @param response 统一渠道 3DS 响应
     * @return 收银台 3DS 结果；空响应按 PROCESSING 处理
     */
    private PaymentCheckoutThreeDsResultDTO copy(ChannelThreeDsAuthenticationResponse response) {
        PaymentCheckoutThreeDsResultDTO result = new PaymentCheckoutThreeDsResultDTO();
        if (response == null) {
            result.setPhase(ChannelThreeDsPhase.INITIALIZE.name());
            result.setStatus(ChannelThreeDsStatus.PROCESSING.name());
            return result;
        }
        result.setPhase(response.getPhase() == null
                ? ChannelThreeDsPhase.INITIALIZE.name() : response.getPhase().name());
        result.setStatus(response.getStatus() == null
                ? ChannelThreeDsStatus.PROCESSING.name() : response.getStatus().name());
        result.setAuthenticationTransactionId(response.getAuthenticationTransactionId());
        result.setChannelOrderNo(response.getChannelOrderNo());
        result.setChannelRequestId(response.getChannelRequestId());
        result.setThreeDsStatus(response.getThreeDsStatus());
        result.setThreeDsVersion(response.getThreeDsVersion());
        result.setThreeDsTransactionId(response.getAuthenticationTransactionId());
        result.setThreeDsServerTransactionId(response.getThreeDsServerTransactionId());
        result.setAcsTransactionId(response.getAcsTransactionId());
        result.setDsTransactionId(response.getDsTransactionId());
        result.setEci(response.getEci());
        result.setCavv(response.getCavv());
        result.setRedirectHtml(response.getRedirectHtml());
        result.setRedirectUrl(response.getRedirectUrl());
        result.setFailureCode(response.getFailureCode());
        result.setFailureMessage(response.getFailureMessage());
        result.setRawResponseMasked(response.getRawResponseMasked());
        return result;
    }

    /**
     * 将可空路由扩展值转换为空串，保持现有渠道扩展 Map 结构稳定。
     *
     * @param value 路由扩展值
     * @return 原值或空串
     */
    private String emptyIfNull(String value) {
        return value == null ? "" : value;
    }
}
