package com.scott.payment.channel.payment.worldpay;

import com.scott.payment.channel.payment.dto.request.ChannelPaymentRequest;
import com.scott.payment.channel.payment.dto.response.ChannelPaymentResponse;
import com.scott.payment.channel.payment.dto.response.ChannelPaymentResponse.PaymentMethodSummary;
import com.scott.payment.component.core.iso.IsoCurrencyInfo;
import com.scott.payment.component.core.iso.IsoCurrencyResolver;
import org.springframework.util.StringUtils;

import java.util.Locale;
import java.util.Map;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : WorldPayJsonResponseMapper
 * @date : 2026-07-26 00:00
 * @email : scott_x@163.com
 * @description : WorldPay JSON 响应映射器，位于 payment-channel-worldpay 渠道适配层，负责把 WPGJSON 原始响应状态、响应码、授权码和支付工具摘要转换为平台统一渠道响应；不决定平台交易终态。
 * @status : create
 */
public class WorldPayJsonResponseMapper {

    /**
     * WorldPay 原始状态到渠道统一状态的映射器。
     */
    private final WorldPayTradeStatusMapper tradeStatusMapper;

    /**
     * 创建 WorldPay JSON 响应映射器。
     */
    public WorldPayJsonResponseMapper() {
        this(new WorldPayTradeStatusMapper());
    }

    /**
     * 创建 WorldPay JSON 响应映射器。
     *
     * @param tradeStatusMapper WorldPay 状态映射器
     */
    WorldPayJsonResponseMapper(WorldPayTradeStatusMapper tradeStatusMapper) {
        this.tradeStatusMapper = tradeStatusMapper;
    }

    /**
     * 将 WPGJSON 响应映射为平台统一渠道响应。
     *
     * @param request 平台统一渠道请求
     * @param response WPGJSON 原始响应
     * @return 平台统一渠道响应
     */
    public ChannelPaymentResponse toChannelResponse(ChannelPaymentRequest request, WorldPayJsonResponsePayload response) {
        ChannelPaymentResponse target = new ChannelPaymentResponse();
        target.setChannelCode(WorldPayChannelCode.WPGJSON);
        target.setOperationId(request == null ? null : request.getOperationId());
        target.setTransactionId(request == null ? null : request.getTransactionId());
        target.setChannelOrderNo(request == null ? null : request.getChannelOrderNo());
        target.setChannelTransactionId(request == null ? null : request.getChannelTransactionId());
        if (response == null) {
            target.setChannelTradeStatus(tradeStatusMapper.map(null));
            target.setChannelResponseCode("EMPTY_RESPONSE");
            target.setChannelResponseMessage("WorldPay JSON response body is empty");
            return target;
        }
        String rawStatus = normalizeStatus(firstText(response.getStatus(), response.getOutcome()));
        target.setRawChannelStatus(rawStatus);
        target.setChannelTradeStatus(tradeStatusMapper.map(rawStatus));
        target.setChannelResponseCode(firstText(response.getResponseCode(), issuerResponseCode(response),
                response.getRefusalCode(), response.getResultCode(), response.getOutcome(), errorCode(response)));
        target.setChannelResponseMessage(firstText(response.getResultMessage(), issuerResponseMessage(response),
                response.getRefusalDescription(), errorMessage(response), rawStatus));
        target.setAuthCode(firstText(response.getAuthorizationCode(), issuerAuthorizationCode(response)));
        target.setRrn(firstText(response.getRrn(), issuerRrn(response)));
        target.setAcquirerReferenceNo(firstText(response.getAcquirerReference(), issuerAcquirerReference(response)));
        if (StringUtils.hasText(response.getOrderCode())) {
            target.setChannelOrderNo(response.getOrderCode());
        }
        if (StringUtils.hasText(firstText(response.getPaymentId(), response.getTransactionId()))) {
            target.setChannelTransactionId(firstText(response.getPaymentId(), response.getTransactionId()));
        }
        mapChannelAmount(target, response.getValue());
        target.setPaymentMethodSummary(paymentMethodSummary(response.getPaymentInstrument()));
        putIfText(target, "outcome", response.getOutcome());
        putIfText(target, "paymentId", response.getPaymentId());
        putIfText(target, "status", response.getStatus());
        putIfText(target, "rawStatusNormalized", rawStatus);
        putIfText(target, "resultCode", response.getResultCode());
        putIfText(target, "resultMessage", response.getResultMessage());
        putIfText(target, "refusalCode", response.getRefusalCode());
        putIfText(target, "refusalDescription", response.getRefusalDescription());
        putIfText(target, "refusalSource", response.getRefusalSource());
        putIfText(target, "requestId", response.getRequestId());
        putIfText(target, "acquirerCode", firstText(response.getAcquirerCode(), issuerResponseCode(response)));
        putIfText(target, "responseCode", firstText(response.getResponseCode(), issuerResponseCode(response)));
        putIfText(target, "authorizationCode", firstText(response.getAuthorizationCode(), issuerAuthorizationCode(response)));
        putIfText(target, "stan", firstText(response.getStan(), issuerStan(response)));
        putIfText(target, "rrn", firstText(response.getRrn(), issuerRrn(response)));
        putIfText(target, "acquirerReference", firstText(response.getAcquirerReference(), issuerAcquirerReference(response)));
        putIfText(target, "riskDecision", response.getRisk() == null ? null : response.getRisk().getDecision());
        putIfText(target, "riskReasonCode", response.getRisk() == null ? null : response.getRisk().getReasonCode());
        putIfText(target, "riskScore", response.getRisk() == null ? null : response.getRisk().getScore());
        putIfText(target, "errorCode", errorCode(response));
        putIfText(target, "errorType", response.getError() == null ? null : response.getError().getType());
        putIfText(target, "errorMessage", errorMessage(response));
        putLinks(target, response.getLinks());
        putLinks(target, response.getActions());
        return target;
    }

    /**
     * 构造渠道金额对象，完成字段复制、格式标准化和敏感数据处理。
     * <p>
     * 转换过程不改变来源对象的业务状态；敏感字段仅保留目标模型所需的最小集合。
     * </p>
     * @param target 源对象、目标对象或查询结果行，用于字段映射、补充展示信息或汇总统计
     * @param value 待标准化的文本、编码或说明值，允许为空时由当前方法按默认规则处理
     */
    private void mapChannelAmount(ChannelPaymentResponse target,
                                  WorldPayJsonResponsePayload.ValuePayload value) {
        if (value == null || value.getAmount() == null || !StringUtils.hasText(value.getCurrency())) {
            return;
        }
        String currency = value.getCurrency().trim().toUpperCase(Locale.ROOT);
        Integer exponent = value.getExponent();
        if (exponent == null) {
            exponent = IsoCurrencyResolver.resolve(currency)
                    .map(IsoCurrencyInfo::defaultFractionDigits)
                    .filter(digits -> digits >= 0)
                    .orElse(null);
        }
        if (exponent == null || exponent < 0 || exponent > 9) {
            return;
        }
        target.setChannelCurrency(currency);
        target.setChannelAmount(value.getAmount().movePointLeft(exponent));
    }

    /**
     * 将渠道支付工具摘要映射到平台统一支付工具摘要。
     *
     * @param source WPGJSON 支付工具节点
     * @return 平台统一支付工具摘要
     */
    private PaymentMethodSummary paymentMethodSummary(WorldPayJsonResponsePayload.PaymentInstrument source) {
        if (source == null || !hasPaymentMethodSummary(source)) {
            return null;
        }
        PaymentMethodSummary summary = new PaymentMethodSummary();
        summary.setPaymentMethod(source.getType());
        summary.setPaymentBrand(firstText(source.getBrand(), source.getCardBrand()));
        summary.setScheme(source.getScheme());
        summary.setCardNumberMasked(firstText(source.getCardNumberMasked(), maskedPan(source.getCardBin(), source.getLastFour())));
        summary.setExpiryMonth(source.getExpiryMonth());
        summary.setExpiryYear(source.getExpiryYear());
        summary.setIssuerCountry(firstText(source.getIssuerCountry(), source.getCountryCode()));
        summary.setFundingMethod(firstText(source.getFundingMethod(), source.getFundingType()));
        summary.setCscResult(source.getCscResult());
        return summary;
    }

    /**
     * 判断支付工具摘要是否包含可用字段。
     *
     * @param source WPGJSON 支付工具节点
     * @return true 表示存在至少一个可用摘要字段
     */
    private boolean hasPaymentMethodSummary(WorldPayJsonResponsePayload.PaymentInstrument source) {
        return StringUtils.hasText(source.getType())
                || StringUtils.hasText(source.getBrand())
                || StringUtils.hasText(source.getCardBrand())
                || StringUtils.hasText(source.getScheme())
                || StringUtils.hasText(source.getCardNumberMasked())
                || StringUtils.hasText(source.getCardBin())
                || StringUtils.hasText(source.getLastFour())
                || StringUtils.hasText(source.getIssuerCountry())
                || StringUtils.hasText(source.getCountryCode())
                || StringUtils.hasText(source.getFundingMethod())
                || StringUtils.hasText(source.getFundingType())
                || StringUtils.hasText(source.getCscResult());
    }

    /**
     * 根据 BIN 和尾四位生成脱敏卡号摘要。
     *
     * @param cardBin 卡 BIN
     * @param lastFour 卡尾四位
     * @return 脱敏卡号；字段不足时为空
     */
    private String maskedPan(String cardBin, String lastFour) {
        if (!StringUtils.hasText(cardBin) || !StringUtils.hasText(lastFour)) {
            return null;
        }
        return cardBin.trim() + "******" + lastFour.trim();
    }

    /**
     * 读取渠道错误码。
     *
     * @param response WPGJSON 响应
     * @return 错误码
     */
    private String errorCode(WorldPayJsonResponsePayload response) {
        return response == null || response.getError() == null ? null : response.getError().getCode();
    }

    /**
     * 读取渠道错误描述。
     *
     * @param response WPGJSON 响应
     * @return 错误描述
     */
    private String errorMessage(WorldPayJsonResponsePayload response) {
        return response == null || response.getError() == null ? null : response.getError().getMessage();
    }

    /**
     * 读取发卡行授权码。
     *
     * @param response WPGJSON 响应
     * @return 授权码
     */
    private String issuerAuthorizationCode(WorldPayJsonResponsePayload response) {
        return response == null || response.getIssuer() == null ? null : response.getIssuer().getAuthorizationCode();
    }

    /**
     * 读取发卡行响应码。
     *
     * @param response WPGJSON 响应
     * @return 响应码
     */
    private String issuerResponseCode(WorldPayJsonResponsePayload response) {
        return response == null || response.getIssuer() == null ? null : response.getIssuer().getResponseCode();
    }

    /**
     * 读取发卡行响应描述。
     *
     * @param response WPGJSON 响应
     * @return 响应描述
     */
    private String issuerResponseMessage(WorldPayJsonResponsePayload response) {
        return response == null || response.getIssuer() == null ? null : response.getIssuer().getResponseMessage();
    }

    /**
     * 读取发卡行 STAN。
     *
     * @param response WPGJSON 响应
     * @return STAN
     */
    private String issuerStan(WorldPayJsonResponsePayload response) {
        return response == null || response.getIssuer() == null ? null : response.getIssuer().getStan();
    }

    /**
     * 读取发卡行检索参考号。
     *
     * @param response WPGJSON 响应
     * @return RRN
     */
    private String issuerRrn(WorldPayJsonResponsePayload response) {
        return response == null || response.getIssuer() == null ? null : response.getIssuer().getRetrievalReferenceNumber();
    }

    /**
     * 读取收单参考号。
     *
     * @param response WPGJSON 响应
     * @return 收单参考号
     */
    private String issuerAcquirerReference(WorldPayJsonResponsePayload response) {
        return response == null || response.getIssuer() == null ? null : response.getIssuer().getAcquirerReferenceNumber();
    }

    /**
     * 将 Access Worldpay outcome 归一为平台已有 WorldPay 状态识别集合。
     *
     * @param status Worldpay status 或 outcome
     * @return AUTHORISED、CAPTURED、REFUSED 等渠道原始状态
     */
    private String normalizeStatus(String status) {
        if (!StringUtils.hasText(status)) {
            return null;
        }
        String normalized = status.trim()
                .replaceAll("([a-z0-9])([A-Z])", "$1_$2")
                .replace('-', '_')
                .replace(' ', '_')
                .toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "AUTHORIZED", "AUTHORISATION_REQUESTED", "SENT_FOR_AUTHORISATION", "SENT_FOR_AUTHORIZATION" -> "AUTHORISED";
            case "SENT_FOR_SETTLEMENT", "SETTLEMENT_REQUESTED", "CAPTURE_REQUESTED", "SETTLED" -> "CAPTURED";
            case "SENT_FOR_REFUND", "REFUND_REQUESTED" -> "SENT_FOR_REFUND";
            case "REFUSED_BY_ISSUER", "DO_NOT_HONOUR" -> "REFUSED";
            default -> normalized;
        };
    }

    /**
     * 保存 Worldpay 后续动作链接，供 service-payment 后续动作或人工排查使用。
     *
     * @param response 平台统一渠道响应
     * @param links Worldpay 链接集合
     */
    private void putLinks(ChannelPaymentResponse response, Map<String, WorldPayJsonResponsePayload.LinkPayload> links) {
        if (response == null || links == null || links.isEmpty()) {
            return;
        }
        links.forEach((key, link) -> {
            if (link == null || !StringUtils.hasText(link.getHref())) {
                return;
            }
            String normalizedKey = normalizeLinkKey(key);
            putIfText(response, normalizedKey, link.getHref());
            putIfText(response, normalizedKey + "Method", link.getMethod());
        });
    }

    /**
     * 将 Worldpay 链接 key 转为稳定 rawResponse key。
     *
     * @param key Worldpay 链接 key
     * @return rawResponse key
     */
    private String normalizeLinkKey(String key) {
        if (!StringUtils.hasText(key)) {
            return "worldpayActionLink";
        }
        String normalized = key.trim().toLowerCase(Locale.ROOT);
        if (normalized.contains("settle") || normalized.contains("capture")) {
            return "worldpaySettleLink";
        }
        if (normalized.contains("refund")) {
            return "worldpayRefundLink";
        }
        if (normalized.contains("cancel") || normalized.contains("void") || normalized.contains("reverse")) {
            return "worldpayCancelLink";
        }
        if (normalized.contains("event") || normalized.contains("query")) {
            return "worldpayEventsLink";
        }
        return "worldpayLink." + normalized.replace(':', '.');
    }

    /**
     * 写入非空渠道扩展字段。
     *
     * @param response 平台统一渠道响应
     * @param key 扩展字段 key
     * @param value 扩展字段值
     */
    private void putIfText(ChannelPaymentResponse response, String key, String value) {
        if (response != null && StringUtils.hasText(value)) {
            response.getRawResponse().put(key, value);
        }
    }

    /**
     * 返回首个非空文本。
     *
     * @param values 候选文本
     * @return 首个非空文本
     */
    private String firstText(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }
}
