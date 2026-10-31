package com.scott.payment.channel.payout.scottchannela.converter;

import com.scott.payment.channel.payout.dto.request.ChannelPayoutRequest;
import com.scott.payment.channel.payout.exception.PayoutChannelException;
import com.scott.payment.channel.payout.scottchannela.model.request.OrderRequestPayload;
import com.scott.payment.channel.payout.scottchannela.model.request.SubmitRequestPayload;
import com.scott.payment.component.core.json.JsonUtils;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 将平台代付提交、查询和取消参数转换为 ScottChannelA 强类型协议对象。
 **/
public class PayoutRequestConverter {

    private static final DateTimeFormatter REQUEST_TIME_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    /**
     * 构造代付提交对象，并在网络调用前校验金额、币种、方式和收款参数。
     *
     * @param request 平台统一代付请求
     * @return 渠道代付提交对象
     * @throws PayoutChannelException 必填参数或开放支付参数无效时抛出
     **/
    public SubmitRequestPayload convertSubmit(ChannelPayoutRequest request) {
        String merchantOrderNo = request == null
                ? null : firstText(request.getPayoutOrderNo(), request.getMerchantOrderNo());
        if (request == null || !StringUtils.hasText(merchantOrderNo) || request.getAmount() == null
                || request.getAmount().compareTo(BigDecimal.ZERO) <= 0 || !StringUtils.hasText(request.getCurrency())
                || !StringUtils.hasText(request.getBeneficiaryReference())) {
            throw new PayoutChannelException(
                    "ScottChannelA payout order number, amount, currency and beneficiary are required");
        }
        String paymentMethod = firstText(request.getPaymentMethod(), request.getExtension().get("paymentMethod"));
        if (!StringUtils.hasText(paymentMethod)) {
            throw new PayoutChannelException("ScottChannelA payout paymentMethod is required");
        }
        SubmitRequestPayload payload = new SubmitRequestPayload();
        payload.setMerchantOrderNo(merchantOrderNo);
        payload.setAmount(normalizeAmount(request.getAmount()));
        payload.setCurrency(request.getCurrency().trim().toUpperCase(Locale.ROOT));
        payload.setPaymentMethod(resolveChannelPaymentMethod(paymentMethod));
        payload.setNotifyUrl(request.getExtension().get("callbackUrl"));
        payload.setClientIp(request.getExtension().get("clientIp"));
        payload.setRequestTime(LocalDateTime.now().format(REQUEST_TIME_FORMATTER));
        payload.setSimulationResult(request.getExtension().get("simulationResult"));
        Map<String, Object> paymentData = parsePaymentData(request.getExtension().get("paymentData"));
        if (paymentData.isEmpty()) {
            paymentData.put("beneficiaryReference", request.getBeneficiaryReference());
        }
        payload.setPaymentData(paymentData);
        return payload;
    }

    /**
     * 查询和取消请求只允许使用一个已持久化的渠道身份。
     *
     * @param channelOrderNo 渠道商户订单号
     * @param channelTransactionId 渠道交易流水号
     * @return 单一查询身份对象
     **/
    public OrderRequestPayload convertOrder(String channelOrderNo, String channelTransactionId) {
        boolean hasOrderNo = StringUtils.hasText(channelOrderNo);
        boolean hasTransactionId = StringUtils.hasText(channelTransactionId);
        if (hasOrderNo == hasTransactionId) {
            throw new PayoutChannelException("ScottChannelA payout requires exactly one order identifier");
        }
        OrderRequestPayload payload = new OrderRequestPayload();
        payload.setMerchantOrderNo(hasOrderNo ? channelOrderNo : null);
        payload.setTransactionId(hasTransactionId ? channelTransactionId : null);
        return payload;
    }

    /**
     * 解析代付方式开放参数；仅接受 JSON 对象。
     **/
    @SuppressWarnings("unchecked")
    private Map<String, Object> parsePaymentData(String json) {
        if (!StringUtils.hasText(json)) {
            return new LinkedHashMap<>();
        }
        try {
            Object parsed = JsonUtils.parseObject(json, Object.class);
            if (parsed instanceof Map<?, ?> values) {
                return new LinkedHashMap<>((Map<String, Object>) values);
            }
        } catch (RuntimeException exception) {
            throw new PayoutChannelException("ScottChannelA payout paymentData is invalid", exception);
        }
        throw new PayoutChannelException("ScottChannelA payout paymentData must be a JSON object");
    }

    /**
     * 按渠道协议固定两位小数且禁止静默舍入。
     **/
    private BigDecimal normalizeAmount(BigDecimal amount) {
        try {
            return amount.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException exception) {
            throw new PayoutChannelException(
                    "ScottChannelA payout amount supports at most 2 decimal places", exception);
        }
    }

    /**
     * 将平台支付方式编码转换为渠道代付协议编码。
     **/
    private String resolveChannelPaymentMethod(String paymentMethod) {
        String normalized = paymentMethod == null ? "" : paymentMethod.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "BANK_CARD" -> "CARD";
            case "PAYPAL" -> "PAY_PAL";
            case "CASH_APP_PAY", "CASH_APP", "CASHAPP" -> "CASHAPP";
            default -> normalized;
        };
    }

    private String firstText(String... values) {
        if (values != null) {
            for (String value : values) {
                if (StringUtils.hasText(value)) {
                    return value;
                }
            }
        }
        return null;
    }
}
