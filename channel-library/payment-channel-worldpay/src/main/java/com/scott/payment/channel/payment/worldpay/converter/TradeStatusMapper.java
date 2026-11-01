package com.scott.payment.channel.payment.worldpay.converter;

import com.scott.payment.channel.payment.enums.ChannelCapability;
import com.scott.payment.channel.payment.enums.ChannelTradeStatus;
import org.springframework.util.StringUtils;

import java.util.Locale;
import java.util.Set;

/**
 * WorldPay 渠道状态映射器，位于 payment-channel-worldpay 渠道实现层，将 WPG 原始状态归一为 provider 无关的渠道动作状态。
 */
public class TradeStatusMapper {

    /**
     * Worldpay 授权完成原始状态集合。
     * <p>
     * 单位：无；格式：Worldpay lastEvent/outcome 归一化文本；不允许为空；非敏感字段。
     * 数据来源：WPGXML/WPGJSON 协议状态；平台资金终态由 service-payment 再按交易类型确认。
     * </p>
     */
    private static final Set<String> AUTHORIZED_STATUSES = Set.of(
            "AUTHORISED", "AUTHORIZED", "AUTHORISE", "AUTHORIZE"
    );

    /**
     * Worldpay 资金捕获完成原始状态集合。
     */
    private static final Set<String> CAPTURED_STATUSES = Set.of(
            "CAPTURED", "CAPTURED_OK", "SETTLED", "SETTLEMENT_REQUESTED"
    );

    /**
     * Worldpay 退款完成原始状态集合。
     */
    private static final Set<String> REFUNDED_STATUSES = Set.of("REFUNDED", "REFUND");

    /**
     * Worldpay 失败方向原始状态集合。
     * <p>
     * 单位：无；格式：Worldpay lastEvent/outcome 归一化文本；不允许为空；非敏感字段。
     * 数据来源：WPGXML/WPGJSON 协议状态；命中后返回渠道统一失败状态。
     * </p>
     */
    private static final Set<String> FAILED_STATUSES = Set.of(
            "REFUSED", "ERROR", "FAILED", "FAILURE", "DECLINED", "EXPIRED"
    );

    /**
     * Worldpay 已完成取消的原始状态集合；是否成功由当前平台动作决定。
     */
    private static final Set<String> CANCELLED_STATUSES = Set.of("CANCELLED", "CANCELED");

    /**
     * Worldpay 待处理方向原始状态集合。
     * <p>
     * 单位：无；格式：Worldpay lastEvent/outcome 归一化文本；不允许为空；非敏感字段。
     * 数据来源：WPGXML/WPGJSON 协议状态；命中后返回渠道统一待处理状态，等待回调或查询确认。
     * </p>
     */
    private static final Set<String> PENDING_STATUSES = Set.of(
            "PENDING", "PROCESSING", "SENT_FOR_AUTHORISATION", "SENT_FOR_AUTHORIZATION",
            "SHOPPER_REDIRECTED", "OPEN", "UNKNOWN", "CAPTURE_REQUESTED", "REFUND_REQUESTED",
            "CANCEL_REQUESTED", "CANCEL_OR_REFUND_REQUESTED", "SENT_FOR_REFUND"
    );

    /**
     * 将 WorldPay 原始状态映射为渠道统一状态。
     * <p>
     * 该映射表达 provider 无关的动作结果，不表达平台资金终态；例如一步支付的 AUTHORIZED
     * 仍需等待 CAPTURED 后，支付核心才允许推进成功。
     *
     * @param rawStatus WorldPay 原始状态
     * @return 渠道统一状态编码
     */
    public String map(String rawStatus) {
        return map(rawStatus, null);
    }

    /**
     * 将 Worldpay 原始状态按当前平台动作映射为统一渠道状态。
     *
     * @param rawStatus Worldpay 原始状态
     * @param transactionType 平台交易类型；查询时传入原始交易类型
     * @return 渠道统一状态编码
     */
    public String map(String rawStatus, String transactionType) {
        String status = normalize(rawStatus);
        if (!StringUtils.hasText(status)) {
            return ChannelTradeStatus.PROCESSING.getCode();
        }
        if (AUTHORIZED_STATUSES.contains(status)) {
            return ChannelTradeStatus.AUTHORIZED.getCode();
        }
        if (CAPTURED_STATUSES.contains(status)) {
            return ChannelTradeStatus.CAPTURED.getCode();
        }
        if (REFUNDED_STATUSES.contains(status)) {
            return ChannelTradeStatus.REFUNDED.getCode();
        }
        if (CANCELLED_STATUSES.contains(status)) {
            return ChannelCapability.VOID.getCode().equals(normalize(transactionType))
                    ? ChannelTradeStatus.VOIDED.getCode()
                    : ChannelTradeStatus.FAILED.getCode();
        }
        if (FAILED_STATUSES.contains(status)) {
            return ChannelTradeStatus.FAILED.getCode();
        }
        if (PENDING_STATUSES.contains(status)) {
            return ChannelTradeStatus.PENDING.getCode();
        }
        return ChannelTradeStatus.PROCESSING.getCode();
    }

    /**
     * 规范化 Worldpay 原始状态文本。
     * <p>
     * 将 null 归一为空字符串，并把 camelCase、连字符和空格转换为下划线，便于同时匹配
     * Access Worldpay outcome 与 XML lastEvent。
     * </p>
     *
     * @param value Worldpay lastEvent、outcome、journalType 或错误状态
     * @return 大写下划线形式的状态文本
     */
    private String normalize(String value) {
        return value == null ? "" : value.trim()
                .replaceAll("([a-z0-9])([A-Z])", "$1_$2")
                .replace('-', '_')
                .replace(' ', '_')
                .toUpperCase(Locale.ROOT);
    }
}
