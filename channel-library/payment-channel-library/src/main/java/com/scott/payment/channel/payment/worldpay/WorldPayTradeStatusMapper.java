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

    private static final Set<String> SUCCESS_STATUSES = Set.of(
            "AUTHORISED", "AUTHORIZED", "CAPTURED", "SETTLED", "REFUNDED", "SENT_FOR_REFUND"
    );

    private static final Set<String> FAILED_STATUSES = Set.of(
            "REFUSED", "ERROR", "FAILED", "FAILURE", "CANCELLED", "CANCELED", "DECLINED", "EXPIRED"
    );

    private static final Set<String> PENDING_STATUSES = Set.of(
            "PENDING", "PROCESSING", "SENT_FOR_AUTHORISATION", "SENT_FOR_AUTHORIZATION",
            "SHOPPER_REDIRECTED", "OPEN", "UNKNOWN"
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
     * 解析normalize，将原始输入转换为当前调用链需要的规范化结果。
     * <p>
     * 前置条件：调用方已传入 渠道适配库 中需要标准化的原始值。
     * 该方法完成金额、币种、时间、状态、路径或协议字段的规范化，不直接提交交易状态。
     * 异常边界：格式非法、精度不满足或枚举不支持时抛出当前模块约定异常。
     * </p>
     * @param value 待标准化的文本、编码或说明值，允许为空时由当前方法按默认规则处理
     * @return 构造、转换或解析后的业务值
     */
    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
    }
}
