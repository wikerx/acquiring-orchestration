package com.scott.payment.channel.payment.scottchannela.converter;

import com.scott.payment.channel.payment.dto.request.ChannelPaymentRequest;
import com.scott.payment.channel.payment.enums.ChannelCapability;
import com.scott.payment.channel.payment.exception.ChannelRequestException;
import com.scott.payment.channel.payment.scottchannela.model.request.CardAuthorizationRequestPayload;
import com.scott.payment.channel.payment.scottchannela.model.request.CardOperationRequestPayload;
import com.scott.payment.channel.payment.scottchannela.model.request.CardQueryRequestPayload;
import com.scott.payment.channel.payment.scottchannela.model.request.OrderQueryRequestPayload;
import com.scott.payment.channel.payment.scottchannela.model.request.PayinRequestPayload;
import com.scott.payment.channel.payment.scottchannela.model.request.RefundQueryRequestPayload;
import com.scott.payment.channel.payment.scottchannela.model.request.RefundRequestPayload;
import com.scott.payment.component.core.json.JsonUtils;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import static com.scott.payment.channel.payment.scottchannela.converter.PaymentOperationClassifier.isCardAuthorizationOperation;
import static com.scott.payment.channel.payment.scottchannela.converter.PaymentOperationClassifier.isCardAuthorizationQuery;
import static com.scott.payment.channel.payment.scottchannela.converter.PaymentOperationClassifier.isInitialAuthorization;
import static com.scott.payment.channel.payment.scottchannela.converter.PaymentOperationClassifier.isRefundQuery;
import static com.scott.payment.channel.payment.scottchannela.converter.PaymentOperationClassifier.normalize;

/**
 * 将平台收单请求校验并转换为与 ScottChannelA 操作端点严格匹配的协议对象。
 *
 * <p>转换器不读取环境凭据，也不发起网络请求；敏感卡数据只保留在本次调用生成的 Payload 中。</p>
 **/
public class PaymentRequestConverter {

    private static final String PAYIN_DIRECT_PATH = "/payins/direct";
    private static final String PAYIN_QUERY_PATH = "/payins/query";
    private static final String REFUND_PATH = "/payins/refunds";
    private static final String REFUND_QUERY_PATH = "/payins/refunds/query";
    private static final String AUTHORIZATION_PATH = "/card/authorizations";
    private static final String PRE_AUTHORIZATION_PATH = "/card/preauthorizations";
    private static final String CAPTURE_PATH = "/card/authorizations/captures";
    private static final String PRE_AUTH_COMPLETION_PATH = "/card/preauthorizations/completions";
    private static final String INCREMENTAL_AUTHORIZATION_PATH = "/card/authorizations/increments";
    private static final String VOID_PATH = "/card/authorizations/voids";
    private static final String AUTHORIZATION_QUERY_PATH = "/card/authorizations/query";
    private static final DateTimeFormatter REQUEST_TIME_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    /**
     * 校验平台请求，并原子生成端点与请求体。
     *
     * @param request 平台统一收单请求
     * @return 端点和渠道协议对象
     * @throws ChannelRequestException 请求身份、金额、币种或交易类型无效时抛出
     **/
    public PreparedPaymentRequest convert(ChannelPaymentRequest request) {
        validateRequest(request);
        String operation = normalize(request.getTransactionType());
        return new PreparedPaymentRequest(resolvePath(operation, request), buildPayload(request, operation));
    }

    /**
     * 按平台交易类型构造互斥的渠道请求对象。
     **/
    private Object buildPayload(ChannelPaymentRequest request, String operation) {
        if (ChannelCapability.REFUND.getCode().equals(operation)) {
            return buildRefundRequest(request);
        }
        if (ChannelCapability.QUERY.getCode().equals(operation)) {
            if (isRefundQuery(request)) {
                return buildRefundQueryRequest(request);
            }
            if (isCardAuthorizationQuery(request)) {
                return buildCardAuthorizationQueryRequest(request);
            }
            return buildOrderQueryRequest(request);
        }
        if (isCardAuthorizationOperation(operation)) {
            return buildCardAuthorizationPayload(request, operation);
        }
        return buildPayinRequest(request);
    }

    /**
     * 构造直连代收请求，平台系统交易号作为渠道幂等订单号。
     **/
    private PayinRequestPayload buildPayinRequest(ChannelPaymentRequest request) {
        String merchantOrderNo = firstText(request.getTransactionId(), request.getChannelOrderNo(),
                request.getMerchantOrderNo());
        if (!StringUtils.hasText(merchantOrderNo)) {
            throw new ChannelRequestException("ScottChannelA merchantOrderNo is required for payins");
        }
        PayinRequestPayload payload = new PayinRequestPayload();
        payload.setMerchantOrderNo(merchantOrderNo);
        payload.setAmount(normalizeAmount(request.getAmount()));
        payload.setCurrency(upper(request.getCurrency()));
        payload.setPaymentMethod(resolveChannelPaymentMethod(request.getPaymentMethod()));
        payload.setNotifyUrl(request.getExtension().get("callbackUrl"));
        payload.setReturnUrl(request.getExtension().get("returnUrl"));
        payload.setClientIp(request.getExtension().get("payerIp"));
        payload.setRequestTime(requestTime(request));
        payload.setSimulationResult(request.getExtension().get("simulationResult"));
        payload.setPaymentData(buildPaymentData(request));
        return payload;
    }

    /**
     * 构造退款请求，并引用原支付渠道身份。
     **/
    private RefundRequestPayload buildRefundRequest(ChannelPaymentRequest request) {
        RefundRequestPayload payload = new RefundRequestPayload();
        putRefundOrderIdentity(payload, request);
        String merchantRefundNo = firstText(request.getExtension().get("merchantRefundNo"),
                request.getMerchantOrderId());
        if (!StringUtils.hasText(merchantRefundNo)) {
            throw new ChannelRequestException("ScottChannelA merchantRefundNo is required for refunds");
        }
        payload.setMerchantRefundNo(merchantRefundNo);
        payload.setAmount(normalizeAmount(request.getAmount()));
        payload.setNotifyUrl(request.getExtension().get("callbackUrl"));
        payload.setSimulationResult(request.getExtension().get("simulationResult"));
        return payload;
    }

    /**
     * 组合支付方式开放参数与平台卡信息。
     *
     * <p>该对象可能包含 PAN 和安全码，只能在发送前短暂存在，并由客户端统一生成脱敏审计快照。</p>
     **/
    private Map<String, Object> buildPaymentData(ChannelPaymentRequest request) {
        Map<String, Object> paymentData = parseDynamicObject(request.getExtension().get("paymentData"));
        putIfText(paymentData, "cardNo", request.getCardNo());
        putIfText(paymentData, "expirationMonth", request.getExpirationMonth());
        putIfText(paymentData, "expirationYear", request.getExpirationYear());
        putIfText(paymentData, "securityCode", request.getSecurityCode());
        putIfText(paymentData, "cardholderName", request.getCardholderName());
        if (request.getBillingInfo() != null) {
            putIfText(paymentData, "billingFirstName", request.getBillingInfo().getFirstName());
            putIfText(paymentData, "billingLastName", request.getBillingInfo().getLastName());
            putIfText(paymentData, "billingEmail", request.getBillingInfo().getEmail());
            putIfText(paymentData, "billingPhone", request.getBillingInfo().getPhone());
            putIfText(paymentData, "billingCountry", request.getBillingInfo().getCountry());
            putIfText(paymentData, "billingState", request.getBillingInfo().getState());
            putIfText(paymentData, "billingCity", request.getBillingInfo().getCity());
            putIfText(paymentData, "billingStreet", request.getBillingInfo().getStreet());
            putIfText(paymentData, "billingPostal", request.getBillingInfo().getPostal());
        }
        return paymentData.isEmpty() ? null : paymentData;
    }

    /**
     * 解析交易类型对应的渠道端点，未知类型在网络调用前失败。
     **/
    private String resolvePath(String operation, ChannelPaymentRequest request) {
        if (ChannelCapability.REFUND.getCode().equals(operation)) {
            return REFUND_PATH;
        }
        if (ChannelCapability.QUERY.getCode().equals(operation)) {
            if (isRefundQuery(request)) {
                return REFUND_QUERY_PATH;
            }
            return isCardAuthorizationQuery(request) ? AUTHORIZATION_QUERY_PATH : PAYIN_QUERY_PATH;
        }
        return switch (operation) {
            case "AUTHORIZATION" -> AUTHORIZATION_PATH;
            case "PRE_AUTHORIZATION" -> PRE_AUTHORIZATION_PATH;
            case "CAPTURE" -> CAPTURE_PATH;
            case "PRE_AUTH_COMPLETION" -> PRE_AUTH_COMPLETION_PATH;
            case "INCREMENTAL_AUTHORIZATION" -> INCREMENTAL_AUTHORIZATION_PATH;
            case "VOID" -> VOID_PATH;
            case "PAYMENT" -> PAYIN_DIRECT_PATH;
            default -> throw new ChannelRequestException(
                    "ScottChannelA unsupported transaction type: " + operation);
        };
    }

    /**
     * 构造卡授权生命周期请求。
     *
     * <p>平台交易号作为 merchantOrderNo；后续动作使用首次授权返回的 authorizationId。VOID 不发送金额。</p>
     **/
    private Object buildCardAuthorizationPayload(ChannelPaymentRequest request, String operation) {
        String merchantOrderNo = request.getTransactionId();
        if (!StringUtils.hasText(merchantOrderNo)) {
            throw new ChannelRequestException("ScottChannelA card merchantOrderNo is required");
        }
        if (isInitialAuthorization(operation)) {
            CardAuthorizationRequestPayload payload = new CardAuthorizationRequestPayload();
            payload.setMerchantOrderNo(merchantOrderNo);
            payload.setAmount(normalizeAmount(request.getAmount()));
            payload.setCurrency(upper(request.getCurrency()));
            payload.setClientIp(request.getExtension().get("payerIp"));
            payload.setRequestTime(requestTime(request));
            payload.setNotifyUrl(request.getExtension().get("callbackUrl"));
            payload.setSimulationResult(request.getExtension().get("simulationResult"));
            return payload;
        }
        String authorizationId = firstText(
                request.getExtension().get("authorizationId"), request.getChannelOrderNo());
        if (!StringUtils.hasText(authorizationId)) {
            throw new ChannelRequestException("ScottChannelA authorizationId is required");
        }
        CardOperationRequestPayload payload = new CardOperationRequestPayload();
        payload.setAuthorizationId(authorizationId);
        payload.setMerchantOrderNo(merchantOrderNo);
        if (!ChannelCapability.VOID.getCode().equals(operation)) {
            payload.setAmount(normalizeAmount(request.getAmount()));
        }
        payload.setNotifyUrl(request.getExtension().get("callbackUrl"));
        payload.setSimulationResult(request.getExtension().get("simulationResult"));
        return payload;
    }

    /**
     * 按 merchantOrderNo、transactionId、authorizationId 的可用顺序只发送一个查询标识。
     **/
    private CardQueryRequestPayload buildCardAuthorizationQueryRequest(ChannelPaymentRequest request) {
        CardQueryRequestPayload payload = new CardQueryRequestPayload();
        String merchantOrderNo = firstText(
                request.getExtension().get("originalMerchantOrderNo"), request.getTransactionId());
        if (StringUtils.hasText(merchantOrderNo)) {
            payload.setMerchantOrderNo(merchantOrderNo);
            return payload;
        }
        String transactionId = request.getChannelTransactionId();
        if (StringUtils.hasText(transactionId)) {
            payload.setTransactionId(transactionId);
            return payload;
        }
        String authorizationId = firstText(
                request.getExtension().get("authorizationId"), request.getChannelOrderNo());
        if (StringUtils.hasText(authorizationId)) {
            payload.setAuthorizationId(authorizationId);
            return payload;
        }
        throw new ChannelRequestException("ScottChannelA authorization query requires exactly one identifier");
    }

    /**
     * 构造 merchantOrderNo 与 transactionId 二选一的代收查询请求。
     **/
    private OrderQueryRequestPayload buildOrderQueryRequest(ChannelPaymentRequest request) {
        OrderQueryRequestPayload payload = new OrderQueryRequestPayload();
        String transactionId = firstText(request.getChannelTransactionId(), request.getSourceTransactionId());
        if (StringUtils.hasText(transactionId)) {
            payload.setTransactionId(transactionId);
            return payload;
        }
        String merchantOrderNo = firstText(request.getMerchantOrderNo(), request.getChannelOrderNo());
        if (!StringUtils.hasText(merchantOrderNo)) {
            throw new ChannelRequestException("ScottChannelA order query requires merchantOrderNo or transactionId");
        }
        payload.setMerchantOrderNo(merchantOrderNo);
        return payload;
    }

    /**
     * 构造 merchantRefundNo 与 refundId 二选一的退款查询请求。
     **/
    private RefundQueryRequestPayload buildRefundQueryRequest(ChannelPaymentRequest request) {
        String merchantRefundNo = request.getExtension().get("merchantRefundNo");
        String refundId = request.getExtension().get("refundId");
        if (StringUtils.hasText(merchantRefundNo) == StringUtils.hasText(refundId)) {
            throw new ChannelRequestException("ScottChannelA refund query requires exactly one identifier");
        }
        RefundQueryRequestPayload payload = new RefundQueryRequestPayload();
        payload.setMerchantRefundNo(merchantRefundNo);
        payload.setRefundId(refundId);
        return payload;
    }

    /**
     * 退款只引用原支付渠道身份，不能使用退款动作新生成的渠道交易号。
     **/
    private void putRefundOrderIdentity(RefundRequestPayload payload, ChannelPaymentRequest request) {
        String sourceTransactionId = firstText(request.getExtension().get("targetTransactionId"),
                request.getChannelTransactionId(), request.getChannelOrderNo());
        if (StringUtils.hasText(sourceTransactionId)) {
            payload.setTransactionId(sourceTransactionId);
            return;
        }
        String merchantOrderNo = request.getMerchantOrderNo();
        if (!StringUtils.hasText(merchantOrderNo)) {
            throw new ChannelRequestException("ScottChannelA refund requires the original payment identity");
        }
        payload.setMerchantOrderNo(merchantOrderNo);
    }

    /**
     * 在发送前校验各操作的最低必填字段。
     *
     * <p>请款和增量授权沿用原授权币种；VOID 不要求也不上送金额。</p>
     **/
    private void validateRequest(ChannelPaymentRequest request) {
        if (request == null || !StringUtils.hasText(request.getTransactionType())) {
            throw new ChannelRequestException("ScottChannelA transactionType is required");
        }
        String operation = normalize(request.getTransactionType());
        boolean query = ChannelCapability.QUERY.getCode().equals(operation);
        boolean refund = ChannelCapability.REFUND.getCode().equals(operation);
        boolean voidOperation = ChannelCapability.VOID.getCode().equals(operation);
        boolean hasOrderIdentity = StringUtils.hasText(request.getMerchantOrderNo())
                || StringUtils.hasText(request.getTransactionId())
                || StringUtils.hasText(request.getChannelOrderNo())
                || StringUtils.hasText(request.getChannelTransactionId())
                || StringUtils.hasText(request.getSourceTransactionId());
        boolean amountRequired = !voidOperation;
        boolean currencyRequired = ChannelCapability.PAYMENT.getCode().equals(operation)
                || ChannelCapability.REFUND.getCode().equals(operation)
                || isInitialAuthorization(operation);
        if (!query && (!hasOrderIdentity
                || (amountRequired && (request.getAmount() == null
                || request.getAmount().compareTo(BigDecimal.ZERO) <= 0))
                || (currencyRequired && !StringUtils.hasText(request.getCurrency())))) {
            throw new ChannelRequestException("ScottChannelA order identity, amount and currency are required");
        }
        if (refund && !StringUtils.hasText(firstText(request.getExtension().get("merchantRefundNo"),
                request.getMerchantOrderId()))) {
            throw new ChannelRequestException("ScottChannelA merchantRefundNo is required for refunds");
        }
    }

    /**
     * 按渠道协议固定两位小数且禁止静默舍入。
     **/
    private BigDecimal normalizeAmount(BigDecimal amount) {
        if (amount == null) {
            return null;
        }
        try {
            return amount.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException exception) {
            throw new ChannelRequestException("ScottChannelA amount supports at most 2 decimal places", exception);
        }
    }

    /**
     * 解析支付方式开放参数；仅接受 JSON 对象。
     **/
    @SuppressWarnings("unchecked")
    private Map<String, Object> parseDynamicObject(String json) {
        if (!StringUtils.hasText(json)) {
            return new LinkedHashMap<>();
        }
        try {
            Object parsed = JsonUtils.parseObject(json, Object.class);
            if (parsed instanceof Map<?, ?> values) {
                return new LinkedHashMap<>((Map<String, Object>) values);
            }
        } catch (RuntimeException exception) {
            throw new ChannelRequestException("ScottChannelA paymentData is invalid", exception);
        }
        throw new ChannelRequestException("ScottChannelA paymentData must be a JSON object");
    }

    /**
     * 使用平台交易时间生成渠道请求时间；缺失时使用当前本地时间。
     **/
    private String requestTime(ChannelPaymentRequest request) {
        return request.getTransactionDateTime() == null
                ? LocalDateTime.now().format(REQUEST_TIME_FORMATTER)
                : request.getTransactionDateTime().format(REQUEST_TIME_FORMATTER);
    }

    /**
     * 将平台支付方式编码转换为渠道协议编码。
     **/
    private String resolveChannelPaymentMethod(String paymentMethod) {
        return switch (normalize(paymentMethod)) {
            case "BANK_CARD" -> "CARD";
            case "PAYPAL" -> "PAY_PAL";
            case "CASH_APP_PAY", "CASH_APP", "CASHAPP" -> "CASH_APP";
            default -> normalize(paymentMethod);
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

    private void putIfText(Map<String, Object> values, String key, String value) {
        if (StringUtils.hasText(value)) {
            values.put(key, value);
        }
    }

    private String upper(String value) {
        return value == null ? null : value.trim().toUpperCase(Locale.ROOT);
    }
}
