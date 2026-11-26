package com.scott.payment.finance.fee.model;

/**
 * 可随费用方案配置、由拒付预警事件计费的费用分类。
 */
public final class DisputeEventFee {

    public static final String RDR = "RDR_FEE";
    public static final String ETHOCA = "ETHOCA_ALERT_FEE";

    private DisputeEventFee() {
    }

    /**
     * 判断费用分类是否仅由预警事件触发，交易清分据此跳过这些规则。
     */
    public static boolean isCategory(String category) {
        return RDR.equalsIgnoreCase(category) || ETHOCA.equalsIgnoreCase(category);
    }
}
