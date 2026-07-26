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
     * 定义 normalize 数据访问或对象转换入口，返回调用方需要的持久化记录或映射结果。
     * <p>
     * 层级边界：渠道适配层；输入来源、输出结构和异常语义由 WorldPayTradeStatusMapper 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param value 待校验或转换的原始值
     * @return 标准化后的业务字段值
     */
    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
    }
}
