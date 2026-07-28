package com.scott.payment.channel.payment.worldpay;

import com.scott.payment.channel.payment.enums.ChannelTradeStatus;
import org.springframework.util.StringUtils;

import java.util.Locale;
import java.util.Set;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : WorldPayTradeStatusMapper
 * @date : 2026-07-19 22:50
 * @email : scott_x@163.com
 * @description : WorldPay 渠道状态映射器，位于 payment-channel-library 渠道实现层，仅将 WPG 原始状态归一为渠道统一状态；AUTHORISED/AUTHORIZED 是否代表平台成功必须由 service-payment 结合交易类型判断。
 * @status : create
 */
public class WorldPayTradeStatusMapper {

    /**
     * Worldpay 成功方向原始状态集合。
     * <p>
     * 单位：无；格式：Worldpay lastEvent/outcome 归一化文本；不允许为空；非敏感字段。
     * 数据来源：WPGXML/WPGJSON 协议状态；这里只映射渠道事件方向，平台资金终态由 service-payment 再按交易类型确认。
     * </p>
     */
    private static final Set<String> SUCCESS_STATUSES = Set.of(
            "AUTHORISED", "AUTHORIZED", "CAPTURED", "SETTLED", "REFUNDED", "SENT_FOR_REFUND"
    );

    /**
     * Worldpay 失败方向原始状态集合。
     * <p>
     * 单位：无；格式：Worldpay lastEvent/outcome 归一化文本；不允许为空；非敏感字段。
     * 数据来源：WPGXML/WPGJSON 协议状态；命中后返回渠道统一失败状态。
     * </p>
     */
    private static final Set<String> FAILED_STATUSES = Set.of(
            "REFUSED", "ERROR", "FAILED", "FAILURE", "CANCELLED", "CANCELED", "DECLINED", "EXPIRED"
    );

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
            "CANCEL_REQUESTED", "CANCEL_OR_REFUND_REQUESTED"
    );

    /**
     * 将 WorldPay 原始状态映射为渠道统一状态。
     * <p>
     * 该映射只表达“渠道事件方向”，不表达平台资金终态；例如 WorldPay 一步支付或请款的 AUTHORISED
     * 仍需等待 CAPTURED/SETTLED 回调或 Inquiry 后，支付核心才允许推进成功。
     *
     * @param rawStatus WorldPay 原始状态
     * @return 渠道统一状态编码
     */
    public String map(String rawStatus) {
        String status = normalize(rawStatus);
        if (!StringUtils.hasText(status)) {
            return ChannelTradeStatus.PROCESSING.getCode();
        }
        if (SUCCESS_STATUSES.contains(status)) {
            return ChannelTradeStatus.SUCCESS.getCode();
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
     * 将 null 归一为空字符串，并把连字符和空格转换为下划线，便于匹配 Worldpay 状态集合。
     * </p>
     *
     * @param value Worldpay lastEvent、outcome、journalType 或错误状态
     * @return 大写下划线形式的状态文本
     */
    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
    }
}
