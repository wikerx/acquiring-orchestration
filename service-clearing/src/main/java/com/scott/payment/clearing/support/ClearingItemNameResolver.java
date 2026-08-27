package com.scott.payment.clearing.support;

import org.springframework.util.StringUtils;

import java.util.Locale;
import java.util.Map;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ClearingItemNameResolver
 * @date : 2026-08-27 14:20
 * @email : scott_x@163.com
 * @description : 清分事实专业名称解析器；只生成展示名称，不改变稳定编码、费用规则、币种、金额或快照哈希。
 * @status : create
 */
public final class ClearingItemNameResolver {

    private static final Map<String, String> FEE_NAMES = Map.of(
            "TRANSACTION_FEE", "交易手续费",
            "REFUND_FEE", "退款手续费",
            "DISPUTE_FEE", "拒付手续费",
            "SETTLEMENT_PROCESSING_FEE", "结算处理费",
            "SETTLEMENT_FX_FEE", "结算货币兑换费");

    private static final Map<String, String> RISK_NAMES = Map.of(
            "INTERNAL", "内风控手续费",
            "EXTERNAL", "外风控手续费",
            "THREE_DS", "3DS手续费");

    private static final Map<String, String> RESERVE_NAMES = Map.of(
            "HOLD", "保证金扣留",
            "RETURN", "保证金返还",
            "RELEASE", "保证金释放",
            "ADJUSTMENT", "保证金调整");

    private ClearingItemNameResolver() {
    }

    /**
     * 解析交易清分明细名称；未知编码原样返回，避免丢失新类型信息。
     *
     * @param itemType PRINCIPAL、PLATFORM_FEE 或 FEE_REVERSAL
     * @param feeCategory 稳定费用类别编码
     * @param riskServiceType 稳定风控服务编码
     * @return 专业中文名称或未知稳定编码
     */
    public static String transaction(String itemType,
                                     String feeCategory,
                                     String riskServiceType) {
        String normalizedItemType = normalize(itemType);
        if ("PRINCIPAL".equals(normalizedItemType)) {
            return "交易本金";
        }
        if ("FEE_REVERSAL".equals(normalizedItemType)) {
            return "对应手续费冲回";
        }
        String normalizedCategory = normalize(feeCategory);
        if ("RISK_FEE".equals(normalizedCategory)) {
            return RISK_NAMES.getOrDefault(normalize(riskServiceType), "风控手续费");
        }
        if (normalizedCategory == null) {
            return normalizedItemType;
        }
        return FEE_NAMES.getOrDefault(normalizedCategory, normalizedCategory);
    }

    /**
     * 解析独立保证金清分事实名称。
     *
     * @param reserveActionType HOLD、RETURN、RELEASE 或 ADJUSTMENT
     * @return 专业中文名称或未知稳定编码
     */
    public static String reserve(String reserveActionType) {
        String normalized = normalize(reserveActionType);
        if (normalized == null) {
            return null;
        }
        return RESERVE_NAMES.getOrDefault(normalized, normalized);
    }

    private static String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : null;
    }
}
