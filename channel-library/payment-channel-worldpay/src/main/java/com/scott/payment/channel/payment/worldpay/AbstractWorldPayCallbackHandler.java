package com.scott.payment.channel.payment.worldpay;

import com.alibaba.fastjson2.TypeReference;
import com.scott.payment.channel.payment.api.PaymentChannelCallbackHandler;
import com.scott.payment.channel.payment.dto.callback.ChannelCallbackRequest;
import com.scott.payment.channel.payment.dto.callback.ChannelCallbackResult;
import com.scott.payment.channel.payment.exception.ChannelRequestException;
import com.scott.payment.component.core.json.JsonUtils;
import org.springframework.util.StringUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AbstractWorldPayCallbackHandler
 * @date : 2026-07-19 23:00
 * @email : scott_x@163.com
 * @description : WorldPay 回调处理器抽象基类，位于 payment-channel-worldpay 渠道实现层，仅负责解析 WPGXML/WPGJSON 通知基础字段并输出渠道统一回调结果；平台终态推进、幂等和商户通知必须留在 service-payment。
 * @status : create
 */
public abstract class AbstractWorldPayCallbackHandler implements PaymentChannelCallbackHandler {

    /**
     * 回调解析前剥离 DTD 声明，兼容 Worldpay 官方 DOCTYPE，同时阻断外部实体解析。
     */
    private static final Pattern DOCTYPE_PATTERN = Pattern.compile("<!DOCTYPE[^>]*>", Pattern.CASE_INSENSITIVE);

    /**
     * Worldpay 回调原始状态映射器。
     * <p>
     * 单位：无；格式：本地无状态映射对象；不允许为空；非敏感字段。
     * 数据来源：当前 handler 内部创建，用于把 AUTHORISED、CAPTURED、REFUSED 等 WPGXML/WPGJSON 原始状态转换为渠道统一状态。
     * 字段关系：只参与回调结果归一，不直接推进 service-payment 平台交易终态。
     * </p>
     */
    private final WorldPayTradeStatusMapper tradeStatusMapper = new WorldPayTradeStatusMapper();

    /**
     * 解析 WorldPay 回调。
     * <p>
     * 该方法当前支持基础 XML/JSON 通知字段提取，用于打通回调记录和状态映射框架；正式上线前仍需按 WorldPay
     * 商户维度签名/认证、通知字段样例和渠道 IP 白名单补齐安全校验，不能把本解析能力等同于完整生产回调验签。
     *
     * @param request 渠道回调请求
     * @return 渠道回调解析结果
     */
    @Override
    public ChannelCallbackResult handle(ChannelCallbackRequest request) {
        if (request == null || !StringUtils.hasText(request.getBody())) {
            throw new ChannelRequestException(channelCode() + " callback body can not be empty");
        }
        String body = request.getBody().trim();
        return body.startsWith("<") ? parseXml(body) : parseJson(body);
    }

    /**
     * 解析 WorldPay XML 通知基础字段。
     * <p>
     * 当前只提取 orderCode、payment/journal 状态、金额和响应摘要；生产接入前仍需要结合真实通知样例补齐签名、
     * IP 白名单和商户维度认证校验。
     *
     * @param body XML 回调原文
     * @return 渠道回调解析结果
     */
    private ChannelCallbackResult parseXml(String body) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            // 禁止外部实体，避免渠道回调 XML 解析引入 XXE 风险。
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setExpandEntityReferences(false);
            Document document = factory.newDocumentBuilder().parse(new InputSource(new StringReader(stripDoctype(body))));
            Element root = document.getDocumentElement();
            String orderCode = attr(root, "orderCode");
            Element orderStatusEvent = first(root, "orderStatusEvent");
            if (orderStatusEvent != null) {
                orderCode = firstText(attr(orderStatusEvent, "orderCode"), orderCode);
            }
            Element payment = first(root, "payment");
            Element journal = first(root, "journal");
            Element amount = firstTextElement(first(payment, "amount"), first(journal, "amount"), first(orderStatusEvent, "amount"), first(root, "amount"));
            Element iso8583 = firstTextElement(first(payment, "ISO8583ReturnCode"), first(journal, "ISO8583ReturnCode"));
            Element authorisationId = first(payment, "AuthorisationId");
            String rawStatus = normalizeStatus(firstText(attr(payment, "lastEvent"), attr(journal, "journalType"), attr(journal, "type")));
            String responseCode = firstText(attr(payment, "responseCode"), attr(journal, "responseCode"),
                    attr(iso8583, "code"), childText(payment, "ISO8583ReturnCode"), childText(journal, "ISO8583ReturnCode"));
            String responseMessage = firstText(attr(payment, "message"), attr(journal, "message"),
                    attr(iso8583, "description"), childText(payment, "refusalReason"), childText(payment, "refusalReasonCode"),
                    childText(journal, "message"));
            String channelTransactionId = firstText(attr(payment, "id"), attr(journal, "id"), orderCode);
            ChannelCallbackResult result = baseResult(orderCode,
                    channelTransactionId,
                    rawStatus,
                    responseCode,
                    responseMessage);
            fillAmount(result, attr(amount, "value"), attr(amount, "exponent"), attr(amount, "currencyCode"));
            put(result, "orderCode", orderCode);
            put(result, "eventId", firstText(attr(orderStatusEvent, "eventId"), attr(orderStatusEvent, "reference"),
                    attr(journal, "id"), attr(payment, "id")));
            put(result, "journalType", attr(journal, "journalType"));
            put(result, "lastEvent", attr(payment, "lastEvent"));
            put(result, "authorizationCode", firstText(attr(payment, "authorisationCode"), attr(authorisationId, "id"),
                    childText(payment, "AuthorisationId")));
            put(result, "acquirerCode", firstText(attr(payment, "acquirerCode"), attr(journal, "acquirerCode"), responseCode));
            put(result, "stan", firstText(attr(payment, "stan"), childText(payment, "stan"), childText(journal, "stan")));
            put(result, "cvcResultCode", childText(payment, "CVCResultCode"));
            return result;
        } catch (Exception exception) {
            throw new ChannelRequestException(channelCode() + " callback XML parse failed", exception);
        }
    }

    /**
     * 解析 WorldPay JSON 通知基础字段。
     * <p>
     * JSON 结构兼容 Access Worldpay 事件通知的 eventId、eventType、summary、merchant.entity、
     * paymentId 和 transactionReference，同时保留早期扁平字段解析能力。
     *
     * @param body JSON 回调原文
     * @return 渠道回调解析结果
     */
    private ChannelCallbackResult parseJson(String body) {
        Map<String, Object> payload = JsonUtils.parseObject(body, new TypeReference<Map<String, Object>>() {
        });
        Map<?, ?> summary = mapValue(payload, "summary");
        Map<?, ?> resource = mapValue(payload, "resource");
        Map<?, ?> payment = firstMap(
                mapValue(payload, "payment"),
                mapValue(summary, "payment"),
                mapValue(resource, "payment"));
        Map<?, ?> merchant = firstMap(
                mapValue(payload, "merchant"),
                mapValue(summary, "merchant"),
                mapValue(resource, "merchant"));
        Map<?, ?> value = firstMap(
                mapValue(payload, "value"),
                mapValue(summary, "value"),
                mapValue(resource, "value"),
                mapValue(mapValue(payload, "instruction"), "value"),
                mapValue(mapValue(summary, "instruction"), "value"));
        String orderNo = firstText(
                stringValue(payload, "transactionReference"),
                stringValue(summary, "transactionReference"),
                stringValue(resource, "transactionReference"),
                stringValue(payload, "orderCode"),
                stringValue(payload, "orderId"));
        String transactionId = firstText(
                stringValue(payload, "paymentId"),
                stringValue(summary, "paymentId"),
                stringValue(resource, "paymentId"),
                stringValue(payment, "id"),
                stringValue(payment, "paymentId"),
                stringValue(payload, "transactionId"),
                orderNo);
        String rawStatus = normalizeStatus(firstText(
                stringValue(payload, "lastEvent"),
                stringValue(payload, "status"),
                stringValue(summary, "status"),
                stringValue(resource, "status"),
                stringValue(payload, "outcome"),
                stringValue(summary, "outcome"),
                stringValue(resource, "outcome"),
                stringValue(payload, "eventType"),
                stringValue(payload, "type")));
        ChannelCallbackResult result = baseResult(orderNo,
                transactionId,
                rawStatus,
                firstText(stringValue(payload, "responseCode"), stringValue(summary, "responseCode"), stringValue(payload, "resultCode")),
                firstText(stringValue(payload, "message"), stringValue(payload, "responseMessage"), stringValue(summary, "message")));
        result.setCurrency(firstText(stringValue(payload, "currencyCode"), stringValue(value, "currency"), stringValue(value, "currencyCode")));
        result.setAmount(firstDecimal(
                minorAmount(value),
                decimalValue(payload, "amount"),
                decimalValue(value, "amount")));
        put(result, "eventId", firstText(stringValue(payload, "eventId"), stringValue(payload, "id")));
        put(result, "eventType", firstText(stringValue(payload, "eventType"), stringValue(payload, "type")));
        put(result, "lastEvent", stringValue(payload, "lastEvent"));
        put(result, "outcome", firstText(stringValue(payload, "outcome"), stringValue(summary, "outcome"), stringValue(resource, "outcome")));
        put(result, "transactionReference", orderNo);
        put(result, "paymentId", transactionId);
        put(result, "merchantEntity", stringValue(merchant, "entity"));
        return result;
    }

    /**
     * 构造 WorldPay 回调统一结果。
     * <p>
     * callbackEventId 包含渠道交易号/订单号和原始状态，避免 WorldPay 先 AUTHORISED 后 CAPTURED 时后续终态事件被幂等吞掉。
     * 签名结论由 service-openapi 根据 Event-Signature 或平台自定义签名写入，渠道 handler 只解析业务字段。
     *
     * @param channelOrderNo 渠道订单号
     * @param channelTransactionId 渠道交易号
     * @param rawStatus WorldPay 原始状态
     * @param responseCode 渠道响应码
     * @param responseMessage 渠道响应描述
     * @return 渠道回调统一结果
     */
    private ChannelCallbackResult baseResult(String channelOrderNo,
                                             String channelTransactionId,
                                             String rawStatus,
                                             String responseCode,
                                             String responseMessage) {
        ChannelCallbackResult result = new ChannelCallbackResult();
        result.setChannelCode(channelCode());
        // WorldPay 可能先通知 AUTHORISED 再通知 CAPTURED，事件幂等键必须包含原始状态，避免吞掉后续终态。
        result.setCallbackEventId(firstText(channelTransactionId, channelOrderNo) + ":" + firstText(rawStatus, "-"));
        result.setChannelOrderNo(channelOrderNo);
        result.setChannelTransactionId(channelTransactionId);
        result.setRawChannelStatus(rawStatus);
        result.setChannelTradeStatus(tradeStatusMapper.map(rawStatus));
        result.setChannelResponseCode(responseCode);
        result.setChannelResponseMessage(responseMessage);
        return result;
    }

    /**
     * 填充 WorldPay 回调金额。
     * <p>
     * XML 通知金额通常是最小单位，exponent 表示小数位；无法解析时不抛异常，保留为空交由回调日志排查。
     *
     * @param result 回调结果
     * @param minorValue 最小单位金额字符串
     * @param exponent 小数位字符串
     * @param currency ISO 4217 币种
     */
    private void fillAmount(ChannelCallbackResult result, String minorValue, String exponent, String currency) {
        result.setCurrency(currency);
        if (!StringUtils.hasText(minorValue)) {
            return;
        }
        try {
            BigDecimal amount = new BigDecimal(minorValue);
            int scale = StringUtils.hasText(exponent) ? Integer.parseInt(exponent) : 0;
            result.setAmount(amount.movePointLeft(scale));
        } catch (NumberFormatException ignored) {
            result.setAmount(null);
        }
    }

    /**
     * 剥离 XML DOCTYPE，避免合法 Worldpay DTD 声明触发禁用 DOCTYPE 解析，同时阻断外部实体。
     *
     * @param xml XML 原文
     * @return 去除 DOCTYPE 后的 XML
     */
    private String stripDoctype(String xml) {
        return xml == null ? null : DOCTYPE_PATTERN.matcher(xml).replaceFirst("");
    }

    /**
     * 获取 XML 中第一个指定节点。
     *
     * @param root XML 根节点
     * @param tagName 节点名称
     * @return 第一个匹配节点
     */
    private Element first(Element root, String tagName) {
        if (root == null || !StringUtils.hasText(tagName) || root.getElementsByTagName(tagName).getLength() == 0) {
            return null;
        }
        return (Element) root.getElementsByTagName(tagName).item(0);
    }

    /**
     * 返回第一个非空 XML 节点。
     *
     * @param values 候选节点
     * @return 第一个非空节点
     */
    private Element firstTextElement(Element... values) {
        if (values == null) {
            return null;
        }
        for (Element value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    /**
     * 读取 XML 子节点文本。
     *
     * @param root 父节点
     * @param tagName 子节点名称
     * @return 子节点文本
     */
    private String childText(Element root, String tagName) {
        Element element = first(root, tagName);
        return element == null ? null : element.getTextContent();
    }

    /**
     * 读取 XML 节点属性。
     *
     * @param element XML 节点
     * @param name 属性名
     * @return 属性值，节点或属性不存在时为空
     */
    private String attr(Element element, String name) {
        return element == null || !element.hasAttribute(name) ? null : element.getAttribute(name);
    }

    /**
     * 读取 JSON 扁平字段文本值。
     *
     * @param payload JSON Map
     * @param key 字段名
     * @return 文本值
     */
    private String stringValue(Map<?, ?> payload, String key) {
        Object value = payload == null ? null : payload.get(key);
        return value == null ? null : String.valueOf(value);
    }

    /**
     * 读取 JSON 子对象。
     *
     * @param payload JSON Map
     * @param key 字段名
     * @return 子对象 Map
     */
    private Map<?, ?> mapValue(Map<?, ?> payload, String key) {
        Object value = payload == null ? null : payload.get(key);
        return value instanceof Map<?, ?> map ? map : null;
    }

    /**
     * 返回第一个非空 Map。
     *
     * @param values 候选 Map
     * @return 第一个非空 Map
     */
    private Map<?, ?> firstMap(Map<?, ?>... values) {
        if (values == null) {
            return null;
        }
        for (Map<?, ?> value : values) {
            if (value != null && !value.isEmpty()) {
                return value;
            }
        }
        return null;
    }

    /**
     * 读取 JSON 金额字段。
     *
     * @param payload JSON Map
     * @param key 字段名
     * @return 金额值，无法解析时为空
     */
    private BigDecimal decimalValue(Map<?, ?> payload, String key) {
        String value = stringValue(payload, key);
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    /**
     * 读取 Worldpay value.amount/minorUnits 金额。
     *
     * @param value Worldpay value 节点
     * @return 主币种单位金额
     */
    private BigDecimal minorAmount(Map<?, ?> value) {
        BigDecimal amount = null;
        String exponentText = firstText(stringValue(value, "exponent"), stringValue(value, "currencyExponent"));
        if (StringUtils.hasText(exponentText)) {
            amount = decimalValue(value, "amount");
        }
        if (amount == null) {
            amount = decimalValue(value, "minorUnits");
        }
        if (amount == null) {
            amount = decimalValue(value, "minorAmount");
        }
        if (amount == null || !StringUtils.hasText(exponentText)) {
            return amount;
        }
        try {
            return amount.movePointLeft(Integer.parseInt(exponentText));
        } catch (NumberFormatException exception) {
            return amount;
        }
    }

    /**
     * 返回第一个非空金额。
     *
     * @param values 候选金额
     * @return 第一个非空金额
     */
    private BigDecimal firstDecimal(BigDecimal... values) {
        if (values == null) {
            return null;
        }
        for (BigDecimal value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    /**
     * 归一化 Worldpay JSON 状态。
     *
     * @param status 原始状态、outcome 或 eventType
     * @return AUTHORISED、CAPTURED、REFUSED 等状态文本
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
        if (normalized.contains("AUTHORIZED") || normalized.contains("AUTHORISED")) {
            return "AUTHORISED";
        }
        if (containsAny(normalized, List.of("SENT_FOR_SETTLEMENT", "SETTLEMENT_REQUESTED", "CAPTURED", "SETTLED"))) {
            return "CAPTURED";
        }
        if (containsAny(normalized, List.of("SENT_FOR_REFUND", "REFUNDED", "REFUND_REQUESTED"))) {
            return "SENT_FOR_REFUND";
        }
        if (containsAny(normalized, List.of("REFUSED", "DECLINED", "DO_NOT_HONOUR"))) {
            return "REFUSED";
        }
        return normalized;
    }

    /**
     * 判断文本是否包含任一候选片段。
     *
     * @param value 待检查文本
     * @param candidates 候选片段
     * @return true 表示命中
     */
    private boolean containsAny(String value, List<String> candidates) {
        if (!StringUtils.hasText(value) || candidates == null) {
            return false;
        }
        for (String candidate : candidates) {
            if (value.contains(candidate)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 写入非空扩展字段。
     *
     * @param result 回调结果
     * @param key 扩展字段名
     * @param value 扩展字段值
     */
    private void put(ChannelCallbackResult result, String key, String value) {
        if (StringUtils.hasText(value)) {
            result.getExtension().put(key, value);
        }
    }

    /**
     * 取第一个非空文本。
     *
     * @param values 候选文本
     * @return 第一个非空文本
     */
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
