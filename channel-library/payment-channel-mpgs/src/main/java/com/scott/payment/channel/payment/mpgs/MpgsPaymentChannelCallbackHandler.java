package com.scott.payment.channel.payment.mpgs;

import com.scott.payment.channel.payment.api.PaymentChannelCallbackHandler;
import com.scott.payment.channel.payment.dto.callback.ChannelCallbackRequest;
import com.scott.payment.channel.payment.dto.callback.ChannelCallbackResult;
import com.scott.payment.channel.payment.enums.ChannelCallbackKind;
import com.scott.payment.channel.payment.enums.ChannelTradeStatus;
import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.channel.payment.exception.ChannelRequestException;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MpgsPaymentChannelCallbackHandler
 * @date : 2026-07-12 00:00
 * @email : scott_x@163.com
 * @description : MPGS 渠道回调处理器，位于 payment-channel-mpgs 渠道实现层，负责解析 MPGS 回调中的 order.id、transaction.id、result 和收单响应码。
 * @status : create
 */
public class MpgsPaymentChannelCallbackHandler implements PaymentChannelCallbackHandler {

    /**
     * MPGS 3DS Method 和付款人认证回调路径标识。
     */
    private static final String THREE_DS_CALLBACK_PATH_SEGMENT = "/3ds";

    /**
     * 3DS Method 完成回调的内部原始状态。
     */
    private static final String THREE_DS_METHOD_COMPLETED = "3DS_METHOD_COMPLETED";

    /**
     * 3DS 付款人认证回调的内部原始状态。
     */
    private static final String THREE_DS_PAYER_AUTHENTICATION = "3DS_PAYER_AUTHENTICATION";

    /** MPGS Webhook 中明确表示 3DS 认证交易的 transaction.type。 */
    private static final String AUTHENTICATION_TRANSACTION_TYPE = "AUTHENTICATION";

    /**
     * order.notificationUrl 是订单级通知地址，认证完成后的资金动作会继续投递到同一 URL。
     * 这些 transaction.type 必须进入普通资金状态映射。
     */
    private static final Set<String> FINANCIAL_TRANSACTION_TYPES = Set.of(
            "AUTHORIZATION", "PAYMENT", "CAPTURE", "REFUND", "VOID",
            "UPDATE_AUTHORIZATION", "VERIFICATION", "DISBURSEMENT");

    private final MpgsTradeStatusMapper tradeStatusMapper;

    private final MpgsErrorCodeMapper errorCodeMapper;

    /**
     * 创建 MPGS 回调处理器。
     */
    public MpgsPaymentChannelCallbackHandler() {
        this(new MpgsTradeStatusMapper(), new MpgsErrorCodeMapper());
    }

    /**
     * 创建 MPGS 回调处理器。
     *
     * @param tradeStatusMapper MPGS 状态映射器
     * @param errorCodeMapper MPGS 错误码映射器
     */
    public MpgsPaymentChannelCallbackHandler(MpgsTradeStatusMapper tradeStatusMapper,
                                             MpgsErrorCodeMapper errorCodeMapper) {
        this.tradeStatusMapper = tradeStatusMapper;
        this.errorCodeMapper = errorCodeMapper;
    }

    /**
     * 返回本处理器绑定的 MPGS 渠道编码。
     *
     * @return MPGS 平台渠道编码
     */
    @Override
    public String channelCode() {
        return MpgsChannelCode.MPGS;
    }

    /**
     * 解析 MPGS 回调。
     *
     * @param request 渠道回调请求
     * @return 渠道回调解析结果
     */
    @Override
    public ChannelCallbackResult handle(ChannelCallbackRequest request) {
        if (request == null || !StringUtils.hasText(request.getBody())) {
            throw new ChannelRequestException("MPGS callback body can not be empty");
        }
        if (isThreeDsCallback(request)) {
            return handleThreeDsCallback(request);
        }
        MpgsResponsePayload payload = JsonUtils.parseObject(request.getBody(), MpgsResponsePayload.class);
        if (payload == null) {
            throw new ChannelRequestException("MPGS callback body can not be parsed");
        }
        ChannelCallbackResult result = new ChannelCallbackResult();
        result.setChannelCode(MpgsChannelCode.MPGS);
        result.setCallbackKind(ChannelCallbackKind.FINANCIAL_TRANSACTION);
        result.setCallbackEventId(firstText(header(request, MpgsCallbackVerifier.NOTIFICATION_ID_HEADER),
                callbackEventId(payload)));
        result.setChannelOrderNo(payload.getOrder() == null ? null : payload.getOrder().getId());
        result.setChannelTransactionId(payload.getTransaction() == null ? null : payload.getTransaction().getId());
        result.setRawChannelStatus(firstText(payload.getOrder() == null ? null : payload.getOrder().getStatus(), payload.getResult()));
        result.setChannelTradeStatus(tradeStatusMapper.map(payload));
        result.setAmount(parseAmount(payload));
        result.setCurrency(firstText(
                payload.getTransaction() == null ? null : payload.getTransaction().getCurrency(),
                payload.getOrder() == null ? null : payload.getOrder().getCurrency()));
        result.setSignatureValid(true);
        result.setChannelResponseCode(channelResponseCode(payload));
        result.setChannelResponseMessage(errorCodeMapper.responseMessage(payload));
        put(result, "result", payload.getResult());
        put(result, "orderStatus", payload.getOrder() == null ? null : payload.getOrder().getStatus());
        put(result, "transactionType", payload.getTransaction() == null ? null : payload.getTransaction().getType());
        if (payload.getResponse() != null) {
            put(result, "gatewayCode", payload.getResponse().getGatewayCode());
            put(result, "acquirerCode", payload.getResponse().getAcquirerCode());
            put(result, "acquirerMessage", payload.getResponse().getAcquirerMessage());
        }
        put(result, "notificationAttempt", header(request, MpgsCallbackVerifier.NOTIFICATION_ATTEMPT_HEADER));
        return result;
    }

    /**
     * 解析 MPGS 3DS JSON 或表单回调。
     * <p>
     * Method 完成和付款人认证均映射为 PENDING，只提供后续认证编排所需标识，不在渠道层
     * 直接把支付交易改为成功。sessionData 只记录存在性和长度。
     * </p>
     *
     * @param request 已经过回调入口安全校验的渠道请求
     * @return 统一 3DS 回调结果
     */
    private ChannelCallbackResult handleThreeDsCallback(ChannelCallbackRequest request) {
        MpgsResponsePayload payload = parsePayload(request.getBody());
        Map<String, String> form = isJson(request.getBody()) ? Map.of() : parseForm(request.getBody());
        String threeDsServerTransactionId = firstText(
                payload == null ? null : payload.getThreeDSServerTransID(),
                form.get("threeDSServerTransID"),
                form.get("threeDSServerTransId"),
                form.get("threeDsServerTransId"));
        String orderId = firstText(
                payload == null ? null : payload.getOrderId(),
                payload == null || payload.getOrder() == null ? null : payload.getOrder().getId(),
                form.get("orderId"),
                form.get("order.id"));
        String transactionId = firstText(
                payload == null ? null : payload.getTransactionId(),
                payload == null || payload.getTransaction() == null ? null : payload.getTransaction().getId(),
                form.get("transactionId"),
                form.get("transaction.id"),
                threeDsServerTransactionId);
        String gatewayRecommendation = firstText(
                payload == null || payload.getResponse() == null ? null : payload.getResponse().getGatewayRecommendation(),
                form.get("response.gatewayRecommendation"),
                form.get("gatewayRecommendation"));
        String resultValue = firstText(payload == null ? null : payload.getResult(), form.get("result"));
        String sessionData = firstText(payload == null ? null : payload.getThreeDSSessionData(), form.get("threeDSSessionData"));
        boolean methodCompletion = StringUtils.hasText(threeDsServerTransactionId)
                && !StringUtils.hasText(gatewayRecommendation)
                && !StringUtils.hasText(resultValue);

        ChannelCallbackResult result = new ChannelCallbackResult();
        result.setChannelCode(MpgsChannelCode.MPGS);
        result.setCallbackKind(ChannelCallbackKind.THREE_DS_AUTHENTICATION);
        result.setCallbackEventId(firstText(header(request, MpgsCallbackVerifier.NOTIFICATION_ID_HEADER),
                threeDsServerTransactionId, transactionId, orderId));
        result.setChannelOrderNo(orderId);
        result.setChannelTransactionId(transactionId);
        result.setRawChannelStatus(methodCompletion ? THREE_DS_METHOD_COMPLETED : firstText(gatewayRecommendation, resultValue, THREE_DS_PAYER_AUTHENTICATION));
        result.setChannelTradeStatus(ChannelTradeStatus.PENDING.getCode());
        result.setSignatureValid(true);
        result.setChannelResponseCode(firstText(gatewayRecommendation, methodCompletion ? THREE_DS_METHOD_COMPLETED : resultValue));
        result.setChannelResponseMessage(methodCompletion
                ? "3DS method completion callback received"
                : "3DS payer authentication callback received");
        put(result, "callbackKind", methodCompletion ? "3DS_METHOD_COMPLETION" : THREE_DS_PAYER_AUTHENTICATION);
        put(result, "threeDsServerTransactionId", threeDsServerTransactionId);
        put(result, "gatewayRecommendation", gatewayRecommendation);
        put(result, "result", resultValue);
        put(result, "notificationAttempt", header(request, MpgsCallbackVerifier.NOTIFICATION_ATTEMPT_HEADER));
        if (StringUtils.hasText(sessionData)) {
            put(result, "threeDsSessionData", "present,length=" + sessionData.length());
        }
        return result;
    }

    /** Read a callback header using HTTP case-insensitive semantics. */
    private String header(ChannelCallbackRequest request, String name) {
        if (request == null || request.getHeaders() == null || name == null) {
            return null;
        }
        String value = request.getHeaders().get(name);
        if (value != null) {
            return value;
        }
        for (Map.Entry<String, String> entry : request.getHeaders().entrySet()) {
            if (name.equalsIgnoreCase(entry.getKey())) {
                return entry.getValue();
            }
        }
        return null;
    }

    /**
     * 根据 MPGS transaction.type、浏览器返回字段和路径判断是否属于 3DS 认证事件。
     *
     * <p>transaction.type 优先级最高。order.notificationUrl 是订单级 Webhook，同一 /3ds URL
     * 可能先后收到 AUTHENTICATION 和 PAYMENT/AUTHORIZATION，不能仅按 URL 分类。</p>
     *
     * @param request 渠道回调请求
     * @return 认证交易或浏览器 Method/Challenge 返回时返回 {@code true}
     */
    private boolean isThreeDsCallback(ChannelCallbackRequest request) {
        MpgsResponsePayload payload = parsePayload(request.getBody());
        String transactionType = payload == null || payload.getTransaction() == null
                ? "" : normalize(payload.getTransaction().getType());
        if (AUTHENTICATION_TRANSACTION_TYPE.equals(transactionType)) {
            return true;
        }
        if (FINANCIAL_TRANSACTION_TYPES.contains(transactionType)) {
            return false;
        }
        String requestUri = request.getRequestUri();
        if (StringUtils.hasText(requestUri) && requestUri.toLowerCase().contains(THREE_DS_CALLBACK_PATH_SEGMENT)) {
            return true;
        }
        String body = request.getBody();
        return containsIgnoreCase(body, "threeDSServerTransID")
                || containsIgnoreCase(body, "threeDSSessionData")
                || containsIgnoreCase(body, "gatewayRecommendation")
                || containsIgnoreCase(body, "\"orderId\"")
                || containsIgnoreCase(body, "orderId=");
    }

    /** 统一 MPGS 枚举型协议字段的大小写和空白。 */
    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    /**
     * 仅在请求体为 JSON 对象时解析 MPGS 响应模型。
     *
     * @param body 回调请求体
     * @return JSON 响应模型；表单回调返回 {@code null}
     */
    private MpgsResponsePayload parsePayload(String body) {
        if (!isJson(body)) {
            return null;
        }
        return JsonUtils.parseObject(body, MpgsResponsePayload.class);
    }

    /**
     * 判断非空请求体是否以 JSON 对象开始。
     *
     * @param body 回调请求体
     * @return 看起来是 JSON 对象时返回 {@code true}
     */
    private boolean isJson(String body) {
        return StringUtils.hasText(body) && body.trim().startsWith("{");
    }

    /**
     * 解析 {@code application/x-www-form-urlencoded} 形式的 MPGS 回调。
     * <p>
     * 重复字段按最后一个值保留；返回 Map 只在内存中参与状态映射，不整体写入日志。
     * </p>
     *
     * @param body URL 编码表单体
     * @return 解码后的字段 Map
     */
    private Map<String, String> parseForm(String body) {
        if (!StringUtils.hasText(body)) {
            return Map.of();
        }
        Map<String, String> values = new LinkedHashMap<>();
        for (String pair : body.split("&")) {
            if (!StringUtils.hasText(pair)) {
                continue;
            }
            String[] parts = pair.split("=", 2);
            String key = decode(parts[0]);
            String value = parts.length > 1 ? decode(parts[1]) : "";
            if (StringUtils.hasText(key)) {
                values.put(key, value);
            }
        }
        return values;
    }

    /**
     * 解码 MPGS 表单回调字段，空字段按空串处理以保持解析 Map 稳定。
     */
    private String decode(String value) {
        return URLDecoder.decode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    /**
     * 判断回调文本是否包含指定片段，兼容 MPGS 不同大小写的状态描述。
     */
    private boolean containsIgnoreCase(String value, String pattern) {
        return value != null && pattern != null
                && value.toLowerCase().contains(pattern.toLowerCase());
    }

    /**
     * 提取 MPGS 回调幂等事件号，优先使用 transaction.id，缺失时退到 order.id。
     */
    private String callbackEventId(MpgsResponsePayload payload) {
        String orderId = payload.getOrder() == null ? null : payload.getOrder().getId();
        String transactionId = payload.getTransaction() == null ? null : payload.getTransaction().getId();
        return firstText(transactionId, orderId);
    }

    /**
     * 解析渠道响应码，优先取 acquirerCode，缺失时使用统一错误码映射。
     */
    private String channelResponseCode(MpgsResponsePayload payload) {
        if (payload.getResponse() != null && StringUtils.hasText(payload.getResponse().getAcquirerCode())) {
            return payload.getResponse().getAcquirerCode();
        }
        return errorCodeMapper.responseCode(payload);
    }

    private BigDecimal parseAmount(MpgsResponsePayload payload) {
        if (payload.getTransaction() != null && payload.getTransaction().getAmount() != null) {
            return payload.getTransaction().getAmount();
        }
        return payload.getOrder() == null ? null : payload.getOrder().getAmount();
    }

    private void put(ChannelCallbackResult result, String key, String value) {
        if (StringUtils.hasText(value)) {
            result.getExtension().put(key, value);
        }
    }

    private String firstText(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }
}
