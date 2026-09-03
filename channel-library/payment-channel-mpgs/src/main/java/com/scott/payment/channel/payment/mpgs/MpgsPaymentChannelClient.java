package com.scott.payment.channel.payment.mpgs;

import com.scott.payment.channel.payment.api.AbstractPaymentChannelClient;
import com.scott.payment.channel.payment.dto.request.ChannelAuthorizeRequest;
import com.scott.payment.channel.payment.dto.request.ChannelCaptureRequest;
import com.scott.payment.channel.payment.dto.request.ChannelIncrementalAuthorizeRequest;
import com.scott.payment.channel.payment.dto.request.ChannelPaymentRequest;
import com.scott.payment.channel.payment.dto.request.ChannelPreAuthorizeRequest;
import com.scott.payment.channel.payment.dto.request.ChannelQueryRequest;
import com.scott.payment.channel.payment.dto.request.ChannelRefundRequest;
import com.scott.payment.channel.payment.dto.request.ChannelReversalRequest;
import com.scott.payment.channel.payment.dto.request.ChannelThreeDsAuthenticationRequest;
import com.scott.payment.channel.payment.dto.request.ChannelVoidRequest;
import com.scott.payment.channel.payment.dto.response.ChannelPaymentResponse;
import com.scott.payment.channel.payment.dto.response.ChannelThreeDsAuthenticationResponse;
import com.scott.payment.channel.payment.enums.ChannelCapability;
import com.scott.payment.channel.payment.enums.ChannelThreeDsPhase;
import com.scott.payment.channel.payment.enums.ChannelThreeDsStatus;
import org.springframework.util.StringUtils;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MpgsPaymentChannelClient
 * @date : 2026-07-12 00:00
 * @email : scott_x@163.com
 * @description : MPGS 收单渠道客户端，位于 payment-channel-mpgs 渠道实现层，负责把平台统一渠道能力路由到 MPGS API 客户端；不创建平台交易单、不更新平台交易状态。
 * @status : create
 */
public class MpgsPaymentChannelClient extends AbstractPaymentChannelClient {

    private final MpgsApiClient mpgsApiClient;

    /**
     * 创建 MPGS 渠道客户端。
     *
     * @param mpgsApiClient MPGS REST API 客户端
     */
    public MpgsPaymentChannelClient(MpgsApiClient mpgsApiClient) {
        this.mpgsApiClient = mpgsApiClient;
    }

    /**
     * 获取 MPGS 渠道编码。
     *
     * @return MPGS 渠道编码
     */
    @Override
    public String channelCode() {
        return MpgsChannelCode.MPGS;
    }

    /**
     * 获取当前 MPGS 适配器已接入的交易能力。
     *
     * @return MPGS 渠道能力集合
     */
    @Override
    public Set<ChannelCapability> capabilities() {
        return EnumSet.of(
                ChannelCapability.THREE_DS_AUTHENTICATION,
                ChannelCapability.PAYMENT,
                ChannelCapability.AUTHORIZATION,
                ChannelCapability.CAPTURE,
                ChannelCapability.PRE_AUTHORIZATION,
                ChannelCapability.PRE_AUTH_COMPLETION,
                ChannelCapability.INCREMENTAL_AUTHORIZATION,
                ChannelCapability.REFUND,
                ChannelCapability.VOID,
                ChannelCapability.REVERSAL,
                ChannelCapability.QUERY
        );
    }

    /**
     * 执行请求指定的单个 MPGS 3DS 阶段，并把渠道建议和认证状态转换为平台统一结果。
     *
     * <p>INITIATE_AUTHENTICATION 返回的 3DS Method HTML 必须先交给浏览器执行，之后才能调用
     * AUTHENTICATE_PAYER；本方法禁止跨浏览器交互连续调用两个 API。PAN、CVV 和 CAVV 只在
     * 当前内存调用链传递，禁止写入日志和持久化存储。</p>
     *
     * @param request 渠道统一 3DS 认证请求
     * @return 平台统一 3DS 认证响应
     */
    @Override
    public ChannelThreeDsAuthenticationResponse authenticateThreeDs(ChannelThreeDsAuthenticationRequest request) {
        requireCapability(ChannelCapability.THREE_DS_AUTHENTICATION);
        MpgsThreeDsAuthenticationRequest mpgsRequest = toMpgsRequest(request);
        ChannelThreeDsPhase phase = request.getPhase() == null
                ? ChannelThreeDsPhase.INITIALIZE : request.getPhase();
        MpgsThreeDsAuthenticationResponse response = switch (phase) {
            case AUTHENTICATE -> mpgsApiClient.authenticatePayer(mpgsRequest);
            case VERIFY -> mpgsApiClient.retrieveAuthentication(mpgsRequest);
            case INITIALIZE -> mpgsApiClient.initiateAuthentication(mpgsRequest);
        };
        return toChannelResponse(response, phase);
    }

    /**
     * 提交 MPGS 一步支付交易。
     *
     * @param request 渠道支付请求，需包含卡信息、金额、币种和交易标识
     * @return 渠道统一响应
     */
    @Override
    public ChannelPaymentResponse payment(ChannelPaymentRequest request) {
        requireCapability(ChannelCapability.PAYMENT);
        return mpgsApiClient.execute(request);
    }

    /**
     * 提交 MPGS 授权交易。
     *
     * @param request 渠道授权请求，需包含卡信息、金额、币种和交易标识
     * @return 渠道统一响应
     */
    @Override
    public ChannelPaymentResponse authorize(ChannelAuthorizeRequest request) {
        requireCapability(ChannelCapability.AUTHORIZATION);
        return mpgsApiClient.execute(request);
    }

    /**
     * 提交 MPGS 预授权交易。
     *
     * @param request 渠道预授权请求，当前映射为 MPGS AUTHORIZE
     * @return 渠道统一响应
     */
    @Override
    public ChannelPaymentResponse preAuthorize(ChannelPreAuthorizeRequest request) {
        requireCapability(ChannelCapability.PRE_AUTHORIZATION);
        return mpgsApiClient.execute(request);
    }

    /**
     * 提交 MPGS 增量授权交易。
     *
     * @param request 渠道增量授权请求，当前映射为 MPGS UPDATE_AUTHORIZATION
     * @return 渠道统一响应
     */
    @Override
    public ChannelPaymentResponse incrementalAuthorize(ChannelIncrementalAuthorizeRequest request) {
        requireCapability(ChannelCapability.INCREMENTAL_AUTHORIZATION);
        return mpgsApiClient.execute(request);
    }

    /**
     * 提交 MPGS 请款交易。
     *
     * @param request 渠道请款请求，需包含请款金额、币种和交易标识
     * @return 渠道统一响应
     */
    @Override
    public ChannelPaymentResponse capture(ChannelCaptureRequest request) {
        requireCapability(ChannelCapability.CAPTURE);
        return mpgsApiClient.execute(request);
    }

    /**
     * 提交 MPGS 退款交易。
     *
     * @param request 渠道退款请求，需包含退款金额、币种和交易标识
     * @return 渠道统一响应
     */
    @Override
    public ChannelPaymentResponse refund(ChannelRefundRequest request) {
        requireCapability(ChannelCapability.REFUND);
        return mpgsApiClient.execute(request);
    }

    /**
     * 提交 MPGS 撤销交易。
     *
     * @param request 渠道撤销请求，需通过 sourceTransactionId 或扩展字段 targetTransactionId 指定目标渠道交易
     * @return 渠道统一响应
     */
    @Override
    public ChannelPaymentResponse voidPayment(ChannelVoidRequest request) {
        requireCapability(ChannelCapability.VOID);
        return mpgsApiClient.execute(request);
    }

    /**
     * 提交 MPGS 冲正交易。
     *
     * @param request 渠道冲正请求，当前映射为 MPGS VOID，平台侧仍需在状态机中区分 REVERSAL 语义
     * @return 渠道统一响应
     */
    @Override
    public ChannelPaymentResponse reversal(ChannelReversalRequest request) {
        requireCapability(ChannelCapability.REVERSAL);
        return mpgsApiClient.execute(request);
    }

    /**
     * 查询 MPGS 交易结果。
     *
     * @param request 渠道查询请求，使用商户订单号和交易动作单号构造 MPGS 查询 URL
     * @return 渠道统一响应
     */
    @Override
    public ChannelPaymentResponse query(ChannelQueryRequest request) {
        requireCapability(ChannelCapability.QUERY);
        return mpgsApiClient.execute(request);
    }

    /**
     * MPGS RETRIEVE 交易级查询要求同时具备 order.id 与 transaction.id。
     *
     * @param request 渠道查询请求
     * @return true 表示当前查询引用满足 MPGS REST URL 身份要求
     */
    @Override
    public boolean supportsQueryReference(ChannelQueryRequest request) {
        return supports(ChannelCapability.QUERY)
                && request != null
                && StringUtils.hasText(request.getChannelOrderNo())
                && StringUtils.hasText(request.getChannelTransactionId());
    }

    /**
     * 将平台统一 3DS 请求转换为 MPGS 内部协议模型。
     *
     * @param source 平台统一 3DS 请求
     * @return MPGS 3DS 协议请求
     */
    private MpgsThreeDsAuthenticationRequest toMpgsRequest(ChannelThreeDsAuthenticationRequest source) {
        MpgsThreeDsAuthenticationRequest target = new MpgsThreeDsAuthenticationRequest();
        target.setChannelCode(source.getChannelCode());
        target.setOperationId(source.getOperationId());
        target.setTransactionId(source.getTransactionId());
        target.setChannelOrderNo(source.getChannelOrderNo());
        target.setAuthenticationTransactionId(source.getAuthenticationTransactionId());
        target.setMerchantId(source.getMerchantId());
        target.setMerchantOrderNo(source.getMerchantOrderNo());
        target.setMerchantOrderId(source.getMerchantOrderId());
        target.setPaymentMethod(source.getPaymentMethod());
        target.setAmount(source.getAmount());
        target.setCurrency(source.getCurrency());
        target.setTransactionDateTime(source.getTransactionDateTime());
        target.setCardNo(source.getCardNo());
        target.setExpirationMonth(source.getExpirationMonth());
        target.setExpirationYear(source.getExpirationYear());
        target.setSecurityCode(source.getSecurityCode());
        target.setCardholderName(source.getCardholderName());
        target.setCardBrand(source.getCardBrand());
        target.setRedirectResponseUrl(source.getRedirectResponseUrl());
        target.setNotificationUrl(source.getNotificationUrl());
        target.setBrowserInfoJson(source.getBrowserInfoJson());
        target.setPayerIp(source.getPayerIp());
        target.setBillingInfo(source.getBillingInfo());
        if (source.getExtension() != null) {
            target.getExtension().putAll(source.getExtension());
        }
        return target;
    }

    /**
     * 将 MPGS 3DS 响应转换为统一渠道响应，并在 provider 内完成原始状态解释。
     *
     * @param source MPGS 3DS 响应
     * @return 平台统一 3DS 认证响应
     */
    private ChannelThreeDsAuthenticationResponse toChannelResponse(MpgsThreeDsAuthenticationResponse source,
                                                                   ChannelThreeDsPhase phase) {
        ChannelThreeDsAuthenticationResponse target = new ChannelThreeDsAuthenticationResponse();
        target.setPhase(phase);
        target.setStatus(toChannelStatus(source, phase));
        if (source == null) {
            return target;
        }
        target.setChannelCode(StringUtils.hasText(source.getChannelCode()) ? source.getChannelCode() : channelCode());
        target.setOperationId(source.getOperationId());
        target.setTransactionId(source.getTransactionId());
        target.setChannelOrderNo(source.getChannelOrderNo());
        target.setAuthenticationTransactionId(source.getAuthenticationTransactionId());
        target.setThreeDsStatus(source.getAuthenticationStatus());
        target.setThreeDsVersion(source.getThreeDsVersion());
        target.setThreeDsTransactionId(source.getThreeDsTransactionId());
        target.setThreeDsServerTransactionId(source.getThreeDsServerTransactionId());
        target.setAcsTransactionId(source.getAcsTransactionId());
        target.setDsTransactionId(source.getDsTransactionId());
        target.setEci(source.getEci());
        target.setCavv(source.getCavv());
        target.setRedirectHtml(source.getRedirectHtml());
        target.setRedirectUrl(source.getRedirectUrl());
        target.setFailureCode(source.getResponseCode());
        target.setFailureMessage(source.getResponseMessage());
        target.setRawResponseMasked(source.getRawResponseMasked());
        if (source.getExtension() != null) {
            target.getExtension().putAll(source.getExtension());
        }
        return target;
    }

    /**
     * 将 MPGS 建议和认证状态归一化为平台 3DS 状态。
     *
     * @param response MPGS 3DS 响应
     * @return 平台统一认证状态
     */
    private ChannelThreeDsStatus toChannelStatus(MpgsThreeDsAuthenticationResponse response,
                                                ChannelThreeDsPhase phase) {
        if (response == null) {
            return ChannelThreeDsStatus.PROCESSING;
        }
        if (hasNonSuccessfulHttpStatus(response)) {
            return ChannelThreeDsStatus.FAILED;
        }
        String recommendation = normalize(response.getGatewayRecommendation());
        String authenticationStatus = normalize(response.getAuthenticationStatus());
        String result = normalize(response.getResult());
        if ("FAILURE".equals(result)
                || "DO_NOT_PROCEED".equals(recommendation)
                || "DO_NOT_PROCEED_ABANDON_ORDER".equals(recommendation)
                || "RESUBMIT_WITH_ALTERNATIVE_PAYMENT_DETAILS".equals(recommendation)
                || authenticationStatus.contains("FAILED")
                || authenticationStatus.contains("REJECTED")
                || "AUTHENTICATION_ATTEMPTED".equals(authenticationStatus)
                || authenticationStatus.contains("NOT_SUPPORTED")
                || "AUTHENTICATION_UNAVAILABLE".equals(authenticationStatus)
                || "AUTHENTICATION_REQUIRED".equals(authenticationStatus)
                || "AUTHENTICATION_NOT_IN_EFFECT".equals(authenticationStatus)) {
            return ChannelThreeDsStatus.FAILED;
        }
        if (ChannelThreeDsPhase.INITIALIZE.equals(phase)) {
            if (hasBrowserHtml(response)) {
                return ChannelThreeDsStatus.METHOD_REQUIRED;
            }
            if ("AUTHENTICATION_AVAILABLE".equals(authenticationStatus)) {
                return ChannelThreeDsStatus.READY_TO_AUTHENTICATE;
            }
            return ChannelThreeDsStatus.PROCESSING;
        }
        if (ChannelThreeDsPhase.AUTHENTICATE.equals(phase) && hasBrowserHtml(response)) {
            return ChannelThreeDsStatus.CHALLENGE_REQUIRED;
        }
        if ("AUTHENTICATION_SUCCESSFUL".equals(authenticationStatus)) {
            return ChannelThreeDsStatus.PASSED;
        }
        return ChannelThreeDsStatus.PROCESSING;
    }

    /**
     * MPGS 已返回结构化 HTTP 错误时结果是确定失败，不能按未知渠道结果继续轮询。
     *
     * @param response MPGS 3DS 响应
     * @return true 表示渠道 HTTP 状态明确不在 2xx 范围
     */
    private boolean hasNonSuccessfulHttpStatus(MpgsThreeDsAuthenticationResponse response) {
        String httpStatus = response.getExtension() == null
                ? null : response.getExtension().get("httpStatus");
        if (!StringUtils.hasText(httpStatus)) {
            return false;
        }
        try {
            int status = Integer.parseInt(httpStatus.trim());
            return status < 200 || status >= 300;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    /**
     * 判断 MPGS 是否已返回需要交给受控收银台执行的 3DS Method 或 ACS Challenge HTML。
     *
     * @param response MPGS 3DS 响应
     * @return true 表示应停止后续渠道步骤并返回质询结果
     */
    private boolean hasBrowserHtml(MpgsThreeDsAuthenticationResponse response) {
        return response != null && StringUtils.hasText(response.getRedirectHtml());
    }

    /**
     * 规范化 MPGS 建议和认证状态，避免大小写或空白差异影响状态映射。
     *
     * @param value MPGS 原始状态文本
     * @return 大写且已去除首尾空白的状态文本
     */
    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }
}
