package com.scott.payment.channel.payment.worldpay.xml.converter;

import com.scott.payment.channel.payment.worldpay.converter.TradeStatusMapper;
import com.scott.payment.channel.payment.worldpay.support.ChannelCode;
import com.scott.payment.channel.payment.worldpay.xml.codec.XmlCodec;
import com.scott.payment.channel.payment.worldpay.xml.model.response.ResponsePayload;

import com.scott.payment.channel.payment.dto.request.ChannelPaymentRequest;
import com.scott.payment.channel.payment.dto.response.ChannelPaymentResponse;
import com.scott.payment.channel.payment.exception.ChannelResponseException;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.Locale;

/**
 * WorldPay XML 响应映射器，位于 payment-channel-worldpay 渠道协议层，负责把 WPGXML 响应对象中的 orderStatus、payment、journal、ok 和 error 信息转换为平台统一渠道响应；输入为 XML 原文或响应对象，输出为 ChannelPaymentResponse。
 */
public class ResponseConverter {

    /**
     * XML 编解码器，负责把渠道 XML 原文安全解析为响应对象。
     */
    private final XmlCodec xmlCodec;

    /**
     * WorldPay 原始状态到渠道统一状态的映射器。
     */
    private final TradeStatusMapper tradeStatusMapper;

    /**
     * 创建 WorldPay XML 响应映射器。
     */
    public ResponseConverter() {
        this(new XmlCodec(), new TradeStatusMapper());
    }

    /**
     * 创建 WorldPay XML 响应映射器。
     *
     * @param xmlCodec XML 编解码器
     * @param tradeStatusMapper WorldPay 状态映射器
     */
    public ResponseConverter(XmlCodec xmlCodec, TradeStatusMapper tradeStatusMapper) {
        this.xmlCodec = xmlCodec;
        this.tradeStatusMapper = tradeStatusMapper;
    }

    /**
     * 解析 WPG XML 响应并映射为平台统一渠道响应。
     *
     * @param request 平台统一渠道请求
     * @param responseXml WPG XML 响应原文
     * @return 平台统一渠道响应
     */
    public ChannelPaymentResponse toChannelResponse(ChannelPaymentRequest request, String responseXml) {
        if (!StringUtils.hasText(responseXml)) {
            throw new ChannelResponseException("WorldPay XML response body is empty");
        }
        return toChannelResponse(request, xmlCodec.readResponse(responseXml));
    }

    /**
     * 将 WPGXML 响应对象映射为平台统一渠道响应。
     * <p>
     * 该方法只表达渠道响应归一，不决定平台交易成功、失败或待处理的最终推进规则；WPGXML AUTHORISED/CAPTURED 的资金语义由
     * service-payment 状态解析器结合交易类型判断。
     * </p>
     *
     * @param request 平台统一渠道请求
     * @param payload WPGXML 响应对象
     * @return 平台统一渠道响应
     */
    public ChannelPaymentResponse toChannelResponse(ChannelPaymentRequest request, ResponsePayload payload) {
        if (payload == null) {
            throw new ChannelResponseException("WorldPay XML response payload is empty");
        }
        ResponsePayload.OrderStatus orderStatus = payload.getOrderStatus();
        ResponsePayload.Payment payment = orderStatus == null ? null : orderStatus.getPayment();
        ResponsePayload.Journal journal = orderStatus == null ? null : orderStatus.getJournal();
        ResponsePayload.Amount amount = orderStatus == null ? null : orderStatus.getAmount();
        ResponsePayload.Error error = payload.getError();
        String orderCode = firstText(orderStatus == null ? null : orderStatus.getOrderCode(),
                request == null ? null : request.getChannelOrderNo());
        String channelTransactionId = firstText(payment == null ? null : payment.getId(), journal == null ? null : journal.getId(), orderCode);
        ResponseDetails details = responseDetails(payload, payment, journal, error);
        String rawStatus = details.rawStatus();
        String responseCode = details.responseCode();
        String responseMessage = details.responseMessage();
        ChannelPaymentResponse response = new ChannelPaymentResponse();
        response.setChannelCode(ChannelCode.WPGXML);
        response.setOperationId(request == null ? null : request.getOperationId());
        response.setTransactionId(request == null ? null : request.getTransactionId());
        response.setChannelOrderNo(orderCode);
        response.setChannelTransactionId(channelTransactionId);
        response.setRawChannelStatus(rawStatus);
        response.setChannelTradeStatus(tradeStatusMapper.map(rawStatus, effectiveTransactionType(request)));
        response.setChannelResponseCode(responseCode);
        response.setChannelResponseMessage(responseMessage);
        response.setAuthCode(payment == null ? null : payment.getAuthorisationCode());
        response.setRrn(firstText(payment == null ? null : payment.getReference(), journal == null ? null : journal.getReference()));
        response.setAcquirerReferenceNo(firstText(
                payment == null ? null : payment.getAcquirerReference(),
                journal == null ? null : journal.getAcquirerReference()));
        mapChannelAmount(response, amount);
        putResponseExtension(response, payload, payment, journal, amount, responseCode, responseMessage, orderCode, rawStatus, error);
        return response;
    }

    /**
     * 按错误、支付结果、日志结果的优先级封装 XML 渠道诊断字段。
     *
     * <p>error 节点表示明确的渠道错误，必须优先于其他节点；正常响应再按 payment、journal、ok
     * 的顺序提取状态和响应码，确保日志、数据库审计与状态映射使用同一组字段。</p>
     *
     * @param payload WPGXML 原始响应
     * @param payment 支付结果节点，可为空
     * @param journal 修改操作结果节点，可为空
     * @param error 错误节点，可为空
     * @return 归一后的状态、响应码和响应描述
     */
    private ResponseDetails responseDetails(ResponsePayload payload,
                                            ResponsePayload.Payment payment,
                                            ResponsePayload.Journal journal,
                                            ResponsePayload.Error error) {
        String rawStatus = normalizeStatus(firstText(
                error == null ? null : "ERROR",
                payment == null ? null : payment.getLastEvent(),
                journal == null ? null : journal.getJournalType(),
                journal == null ? null : journal.getType(),
                payload.getOk() == null ? null : payload.getOk().getStatus()));
        String responseCode = firstText(
                error == null ? null : error.getCode(),
                payment == null ? null : payment.getResponseCode(),
                journal == null ? null : journal.getResponseCode(),
                rawStatus);
        String responseMessage = firstText(
                error == null ? null : error.getMessage(),
                payment == null ? null : payment.getMessage(),
                journal == null ? null : journal.getMessage(),
                rawStatus);
        return new ResponseDetails(rawStatus, responseCode, responseMessage);
    }

    /**
     * 将渠道最小辅币单位金额转换为平台主币单位金额。
     *
     * @param response 平台统一渠道响应
     * @param amount Worldpay 金额、币种和辅币位节点
     */
    private void mapChannelAmount(ChannelPaymentResponse response, ResponsePayload.Amount amount) {
        if (amount == null || !StringUtils.hasText(amount.getValue())
                || !StringUtils.hasText(amount.getCurrencyCode())
                || !StringUtils.hasText(amount.getExponent())) {
            return;
        }
        try {
            int exponent = Integer.parseInt(amount.getExponent());
            if (exponent < 0 || exponent > 9) {
                return;
            }
            response.setChannelCurrency(amount.getCurrencyCode().trim().toUpperCase(Locale.ROOT));
            response.setChannelAmount(new BigDecimal(amount.getValue()).movePointLeft(exponent));
        } catch (NumberFormatException ignored) {
            // 无效渠道金额不参与资金差异判断，原始摘要仍保留供排查。
        }
    }

    /**
     * 写入 WPGXML 响应扩展字段。
     * <p>
     * 扩展字段用于交易日志、渠道请求表和人工排查，包含原始状态、响应码、收单响应码、金额辅币位、STAN 和错误码。
     * 本方法只写入非空摘要字段，不保存请求 PAN、CVC、CAVV 或 Basic Auth 凭据。
     * </p>
     *
     * @param response 平台统一渠道响应
     * @param payload WPGXML 响应对象
     * @param payment payment 响应节点
     * @param journal journal 响应节点
     * @param amount amount 响应节点
     * @param responseCode 渠道响应码
     * @param responseMessage 渠道响应描述
     * @param orderCode Worldpay orderCode
     * @param rawStatus Worldpay 原始状态
     * @param error error 响应节点
     */
    private void putResponseExtension(ChannelPaymentResponse response,
                                      ResponsePayload payload,
                                      ResponsePayload.Payment payment,
                                      ResponsePayload.Journal journal,
                                      ResponsePayload.Amount amount,
                                      String responseCode,
                                      String responseMessage,
                                      String orderCode,
                                      String rawStatus,
                                      ResponsePayload.Error error) {
        put(response, "result", response.getChannelTradeStatus());
        put(response, "gatewayCode", responseCode);
        put(response, "acquirerCode", firstText(
                payment == null ? null : payment.getAcquirerCode(),
                journal == null ? null : journal.getAcquirerCode(),
                responseCode));
        put(response, "acquirerMessage", responseMessage);
        put(response, "orderCode", orderCode);
        put(response, "lastEvent", rawStatus);
        put(response, "journalType", journal == null ? null : journal.getJournalType());
        put(response, "paymentId", payment == null ? null : payment.getId());
        put(response, "amountValue", amount == null ? null : amount.getValue());
        put(response, "currencyCode", amount == null ? null : amount.getCurrencyCode());
        put(response, "currencyExponent", amount == null ? null : amount.getExponent());
        put(response, "stan", payment == null ? null : payment.getStan());
        put(response, "cvcResultCode", payment == null ? null : payment.getCvcResultCode());
        put(response, "merchantCode", payload == null ? null : payload.getMerchantCode());
        put(response, "errorCode", error == null ? null : error.getCode());
    }

    /**
     * 查询响应沿用原交易类型解释撤销等动作状态。
     *
     * @param request 平台统一渠道请求
     * @return 查询动作对应的原交易类型；非查询动作返回当前交易类型
     */
    private String effectiveTransactionType(ChannelPaymentRequest request) {
        if (request == null) {
            return null;
        }
        return "QUERY".equalsIgnoreCase(request.getTransactionType())
                ? request.getExtension().get("originalTransactionType")
                : request.getTransactionType();
    }

    /**
     * 标准化 WPGXML 原始状态。
     * <p>
     * ok 响应节点归一为 PROCESSING，其他 lastEvent 或 journalType 保持大写下划线形式，后续由 TradeStatusMapper 转换统一状态。
     * </p>
     *
     * @param status Worldpay lastEvent、journalType、ok 派生状态或 ERROR
     * @return 大写下划线状态文本；输入为空时返回 null
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
        if ("OK".equals(normalized)) {
            return "PROCESSING";
        }
        return normalized;
    }

    /**
     * 写入非空 rawResponse 字段。
     *
     * @param response 平台统一渠道响应
     * @param key rawResponse 字段名
     * @param value rawResponse 字段值
     */
    private void put(ChannelPaymentResponse response, String key, String value) {
        if (response != null && StringUtils.hasText(value)) {
            response.getRawResponse().put(key, value);
        }
    }

    /**
     * 返回首个非空文本。
     *
     * @param values 候选文本
     * @return 首个非空文本；全部为空时返回 null
     */
    private String firstText(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    /**
     * 保存一次 WPGXML 响应映射所需的稳定诊断字段。
     *
     * @param rawStatus 归一后的渠道原始状态
     * @param responseCode 渠道响应码
     * @param responseMessage 渠道响应描述
     */
    private record ResponseDetails(String rawStatus, String responseCode, String responseMessage) {
    }
}
