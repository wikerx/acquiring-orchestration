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
import java.util.Map;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AbstractWorldPayCallbackHandler
 * @date : 2026-07-19 23:00
 * @email : scott_x@163.com
 * @description : WorldPay 回调处理器抽象基类，位于 payment-channel-library 渠道实现层，仅负责解析 WPGXML/WPGJSON 通知基础字段并输出渠道统一回调结果；平台终态推进、幂等和商户通知必须留在 service-payment。
 * @status : create
 */
public abstract class AbstractWorldPayCallbackHandler implements PaymentChannelCallbackHandler {

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
            Document document = factory.newDocumentBuilder().parse(new InputSource(new StringReader(body)));
            Element root = document.getDocumentElement();
            String orderCode = attr(root, "orderCode");
            Element orderStatusEvent = first(root, "orderStatusEvent");
            if (orderStatusEvent != null) {
                orderCode = firstText(attr(orderStatusEvent, "orderCode"), orderCode);
            }
            Element payment = first(root, "payment");
            Element journal = first(root, "journal");
            Element amount = first(root, "amount");
            String rawStatus = firstText(attr(payment, "lastEvent"), attr(journal, "journalType"), attr(journal, "type"));
            ChannelCallbackResult result = baseResult(orderCode,
                    firstText(attr(payment, "id"), attr(journal, "id"), orderCode),
                    rawStatus,
                    firstText(attr(payment, "responseCode"), attr(journal, "responseCode")),
                    firstText(attr(payment, "message"), attr(journal, "message")));
            fillAmount(result, attr(amount, "value"), attr(amount, "exponent"), attr(amount, "currencyCode"));
            put(result, "orderCode", orderCode);
            put(result, "journalType", attr(journal, "journalType"));
            put(result, "lastEvent", attr(payment, "lastEvent"));
            return result;
        } catch (Exception exception) {
            throw new ChannelRequestException(channelCode() + " callback XML parse failed", exception);
        }
    }

    /**
     * 解析 WorldPay JSON 通知基础字段。
     * <p>
     * JSON 结构目前按通用扁平字段兼容，后续接真实 WPGJSON 前必须按官方响应样例和签名规则补齐字段映射。
     *
     * @param body JSON 回调原文
     * @return 渠道回调解析结果
     */
    private ChannelCallbackResult parseJson(String body) {
        Map<String, Object> payload = JsonUtils.parseObject(body, new TypeReference<Map<String, Object>>() {
        });
        String orderNo = firstText(stringValue(payload, "orderCode"), stringValue(payload, "orderId"));
        String transactionId = firstText(stringValue(payload, "transactionId"), stringValue(payload, "paymentId"), orderNo);
        String rawStatus = firstText(stringValue(payload, "lastEvent"), stringValue(payload, "status"), stringValue(payload, "eventType"));
        ChannelCallbackResult result = baseResult(orderNo,
                transactionId,
                rawStatus,
                stringValue(payload, "responseCode"),
                firstText(stringValue(payload, "message"), stringValue(payload, "responseMessage")));
        result.setCurrency(stringValue(payload, "currencyCode"));
        result.setAmount(decimalValue(payload, "amount"));
        put(result, "eventType", stringValue(payload, "eventType"));
        put(result, "lastEvent", stringValue(payload, "lastEvent"));
        return result;
    }

    /**
     * 构造 WorldPay 回调统一结果。
     * <p>
     * callbackEventId 包含渠道交易号/订单号和原始状态，避免 WorldPay 先 AUTHORISED 后 CAPTURED 时后续终态事件被幂等吞掉。
     * signatureValid 固定为 false，提醒调用方当前尚未完成生产级签名或认证校验。
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
        // 当前 WorldPay 处理器只解析通知字段，尚未实现渠道签名/认证校验；生产接入前不能把该值标记为已验签。
        result.setSignatureValid(false);
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
