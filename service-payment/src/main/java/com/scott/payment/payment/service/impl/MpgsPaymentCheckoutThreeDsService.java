package com.scott.payment.payment.service.impl;

import com.scott.payment.channel.payment.dto.request.ChannelPaymentRequest;
import com.scott.payment.channel.payment.exception.ChannelException;
import com.scott.payment.channel.payment.mpgs.MpgsApiClient;
import com.scott.payment.channel.payment.mpgs.MpgsThreeDsAuthenticationRequest;
import com.scott.payment.channel.payment.mpgs.MpgsThreeDsAuthenticationResponse;
import com.scott.payment.payment.api.internal.dto.PaymentCheckoutPaymentSubmitCommandDTO;
import com.scott.payment.payment.entity.PaymentCheckoutAttemptDO;
import com.scott.payment.payment.entity.PaymentCheckoutSessionDO;
import com.scott.payment.payment.api.internal.dto.PaymentCreateCommandDTO;
import com.scott.payment.payment.service.PaymentChannelRouteService;
import com.scott.payment.payment.service.PaymentCheckoutThreeDsService;
import com.scott.payment.payment.service.dto.PaymentCheckoutThreeDsResultDTO;
import com.scott.payment.payment.service.dto.PaymentRouteResultDTO;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Locale;

/**
 * MPGS Direct 3DS 编排实现。
 */
@Service
public class MpgsPaymentCheckoutThreeDsService implements PaymentCheckoutThreeDsService {

    /** MPGS 渠道稳定编码。 */
    private static final String CHANNEL_MPGS = "MPGS";

    /** MPGS 3DS 渠道客户端。 */
    private final MpgsApiClient mpgsApiClient;
    /** 支付渠道路由服务，用于选择并固化本次 3DS 使用的 MID。 */
    private final PaymentChannelRouteService paymentChannelRouteService;

    /**
     * 创建 MPGS Hosted Checkout 3DS 编排服务。
     *
     * @param mpgsApiClient             MPGS 3DS 客户端
     * @param paymentChannelRouteService 支付渠道路由服务
     */
    public MpgsPaymentCheckoutThreeDsService(MpgsApiClient mpgsApiClient,
                                             PaymentChannelRouteService paymentChannelRouteService) {
        this.mpgsApiClient = mpgsApiClient;
        this.paymentChannelRouteService = paymentChannelRouteService;
    }

    /**
     * 路由 MID 并执行 MPGS 3DS 初始化与付款人认证。
     *
     * <p>PAN、CVV 和账单资料只在本次内存调用链中发送给渠道，不得写入日志、缓存或业务表。
     * 渠道异常返回 PROCESSING，等待可靠查询或回调确认，不能直接宣告支付失败或成功。</p>
     */
    @Override
    public PaymentCheckoutThreeDsResultDTO authenticate(PaymentCheckoutSessionDO sessionDO,
                                                       PaymentCheckoutAttemptDO attemptDO,
                                                       PaymentCheckoutPaymentSubmitCommandDTO commandDTO,
                                                       String returnUrl) {
        PaymentRouteResultDTO routeResultDTO = paymentChannelRouteService.route(routeCommand(sessionDO, attemptDO, commandDTO));
        MpgsThreeDsAuthenticationRequest request = buildRequest(sessionDO, attemptDO, commandDTO, returnUrl, routeResultDTO);
        try {
            MpgsThreeDsAuthenticationResponse initiate = mpgsApiClient.initiateAuthentication(request);
            initiate.setChannelCode(routeResultDTO.getChannelCode());
            if (hasChallengeHtml(initiate)) {
                return withRoute(challenge(initiate), routeResultDTO);
            }
            MpgsThreeDsAuthenticationResponse authenticate = mpgsApiClient.authenticatePayer(request);
            authenticate.setChannelCode(routeResultDTO.getChannelCode());
            if (hasChallengeHtml(authenticate)) {
                return withRoute(challenge(authenticate), routeResultDTO);
            }
            return withRoute(terminalAuthentication(authenticate), routeResultDTO);
        } catch (ChannelException exception) {
            PaymentCheckoutThreeDsResultDTO resultDTO = new PaymentCheckoutThreeDsResultDTO();
            resultDTO.setStatus("PROCESSING");
            resultDTO.setAuthenticationTransactionId(request.getAuthenticationTransactionId());
            resultDTO.setChannelOrderNo(request.getChannelOrderNo());
            resultDTO.setChannelTransactionId(request.getAuthenticationTransactionId());
            resultDTO.setFailureCode(exception.getClass().getSimpleName());
            resultDTO.setFailureMessage(exception.getMessage());
            resultDTO.setChannelMidConfigId(routeResultDTO.getMidConfigId());
            return resultDTO;
        }
    }

    /**
     * 构造仅供渠道路由使用的支付命令。
     *
     * @return 包含商户、金额、支付方式和账单维度的路由命令
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
        routeCommand.setBillingCardHolderInfo(toPaymentBillingInfo(commandDTO.getBillingCardHolderInfo()));
        return routeCommand;
    }

    /**
     * 构造 MPGS 3DS 请求。
     *
     * <p>完整卡号和 CVV 仅写入瞬时渠道请求对象；该对象禁止序列化到日志、Redis 或数据库。</p>
     *
     * @return 可发送至 MPGS 的 3DS 认证请求
     */
    private MpgsThreeDsAuthenticationRequest buildRequest(PaymentCheckoutSessionDO sessionDO,
                                                         PaymentCheckoutAttemptDO attemptDO,
                                                         PaymentCheckoutPaymentSubmitCommandDTO commandDTO,
                                                         String returnUrl,
                                                         PaymentRouteResultDTO routeResultDTO) {
        MpgsThreeDsAuthenticationRequest request = new MpgsThreeDsAuthenticationRequest();
        request.setChannelCode(routeResultDTO.getChannelCode());
        request.setOperationId(attemptDO.getOperationId());
        request.setTransactionId(attemptDO.getTransactionId());
        request.setChannelOrderNo(attemptDO.getTransactionId());
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
        request.setBrowserInfoJson(commandDTO.getBrowserInfoJson());
        request.getExtension().put("requestUrl", emptyIfNull(routeResultDTO.getRequestUrl()));
        request.getExtension().put("connectTimeoutSeconds", routeResultDTO.getConnectTimeoutSeconds() == null ? "" : String.valueOf(routeResultDTO.getConnectTimeoutSeconds()));
        request.getExtension().put("readTimeoutSeconds", routeResultDTO.getReadTimeoutSeconds() == null ? "" : String.valueOf(routeResultDTO.getReadTimeoutSeconds()));
        for (var entry : routeResultDTO.getMetadataValues().entrySet()) {
            request.getExtension().put("mid." + entry.getKey(), emptyIfNull(entry.getValue()));
        }
        if (commandDTO.getCardInfo() != null) {
            request.setCardNo(commandDTO.getCardInfo().getCardNo());
            request.setExpirationMonth(commandDTO.getCardInfo().getExpirationMonth());
            request.setExpirationYear(commandDTO.getCardInfo().getExpirationYear());
            request.setSecurityCode(commandDTO.getCardInfo().getSecurityCode());
        }
        request.setBillingInfo(toBillingInfo(commandDTO.getBillingCardHolderInfo()));
        return request;
    }

    /**
     * 将收银台账单资料转换为渠道路由识别模型。
     *
     * @param source 收银台账单资料
     * @return 支付命令账单资料；输入为空时返回 null
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
     * 复用既有 3DS 交易号，首次认证时按支付交易号生成稳定标识。
     *
     * @param attemptDO 支付尝试
     * @return 3DS 认证交易号
     */
    private String authenticationTransactionId(PaymentCheckoutAttemptDO attemptDO) {
        if (StringUtils.hasText(attemptDO.getThreeDsTransactionId())) {
            return attemptDO.getThreeDsTransactionId();
        }
        return "3DS" + attemptDO.getTransactionId();
    }

    /**
     * 将付款人账单信息映射到 MPGS 3DS 请求模型，卡号和 CVV 不从该对象承载。
     */
    private ChannelPaymentRequest.BillingInfo toBillingInfo(PaymentCheckoutPaymentSubmitCommandDTO.BillingCardHolderInfoDTO source) {
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
     * 判断 MPGS 是否返回 ACS 质询内容；只要存在 HTML，就交给自有收银台 bridge 渲染。
     */
    private boolean hasChallengeHtml(MpgsThreeDsAuthenticationResponse response) {
        return response != null && StringUtils.hasText(response.getRedirectHtml());
    }

    /**
     * 构造质询结果，后续由收银台页面展示 3DS iframe/form，不在后端拼接页面。
     */
    private PaymentCheckoutThreeDsResultDTO challenge(MpgsThreeDsAuthenticationResponse response) {
        PaymentCheckoutThreeDsResultDTO resultDTO = copy(response);
        resultDTO.setStatus("CHALLENGE_REQUIRED");
        return resultDTO;
    }

    /**
     * 将 MPGS 认证建议转换为平台 3DS 结果；认证通过只代表可继续扣款，不代表付款成功。
     */
    private PaymentCheckoutThreeDsResultDTO terminalAuthentication(MpgsThreeDsAuthenticationResponse response) {
        PaymentCheckoutThreeDsResultDTO resultDTO = copy(response);
        String recommendation = normalize(response == null ? null : response.getGatewayRecommendation());
        String status = normalize(response == null ? null : response.getAuthenticationStatus());
        if ("PROCEED".equals(recommendation) || "AUTHENTICATION_SUCCESSFUL".equals(status) || "AUTHENTICATION_ATTEMPTED".equals(status)) {
            resultDTO.setStatus("PASSED");
            return resultDTO;
        }
        if ("DO_NOT_PROCEED".equals(recommendation) || status.contains("FAILED") || status.contains("REJECTED")) {
            resultDTO.setStatus("FAILED");
            return resultDTO;
        }
        resultDTO.setStatus("PROCESSING");
        return resultDTO;
    }

    /**
     * 复制 3DS 认证摘要给收银台状态机，rawResponse 只使用渠道层已脱敏版本。
     */
    private PaymentCheckoutThreeDsResultDTO copy(MpgsThreeDsAuthenticationResponse response) {
        PaymentCheckoutThreeDsResultDTO resultDTO = new PaymentCheckoutThreeDsResultDTO();
        if (response == null) {
            resultDTO.setStatus("PROCESSING");
            return resultDTO;
        }
        resultDTO.setAuthenticationTransactionId(response.getAuthenticationTransactionId());
        resultDTO.setChannelOrderNo(response.getChannelOrderNo());
        resultDTO.setChannelTransactionId(response.getAuthenticationTransactionId());
        resultDTO.setThreeDsStatus(response.getAuthenticationStatus());
        resultDTO.setThreeDsVersion(response.getThreeDsVersion());
        resultDTO.setThreeDsTransactionId(response.getAuthenticationTransactionId());
        resultDTO.setThreeDsServerTransactionId(response.getThreeDsServerTransactionId());
        resultDTO.setAcsTransactionId(response.getAcsTransactionId());
        resultDTO.setDsTransactionId(response.getDsTransactionId());
        resultDTO.setEci(response.getEci());
        resultDTO.setCavv(response.getCavv());
        resultDTO.setRedirectHtml(response.getRedirectHtml());
        resultDTO.setRedirectUrl(response.getRedirectUrl());
        resultDTO.setFailureCode(response.getResponseCode());
        resultDTO.setFailureMessage(response.getResponseMessage());
        resultDTO.setRawResponseMasked(response.getRawResponseMasked());
        return resultDTO;
    }

    /**
     * 将路由到的 MID 配置写回结果，便于后续付款请求沿用同一通道路由。
     */
    private PaymentCheckoutThreeDsResultDTO withRoute(PaymentCheckoutThreeDsResultDTO resultDTO,
                                                      PaymentRouteResultDTO routeResultDTO) {
        resultDTO.setChannelMidConfigId(routeResultDTO.getMidConfigId());
        return resultDTO;
    }

    /**
     * MPGS 扩展参数 Map 不接受 null，这里统一转为空串以保持请求结构稳定。
     */
    private String emptyIfNull(String value) {
        return value == null ? "" : value;
    }

    /**
     * 规范化 MPGS 建议和认证状态，避免大小写差异影响平台状态判断。
     */
    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }
}
