package com.scott.payment.channel.payment.mpgs;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MpgsApiOperation
 * @date : 2026-07-12 00:00
 * @email : scott_x@163.com
 * @description : MPGS API 操作常量，位于 payment-channel-library 渠道实现层，用于收敛 MPGS 请求中的 apiOperation 值。
 * @status : create
 */
public final class MpgsApiOperation {

    /**
     * MPGS 一步支付操作。
     */
    public static final String PAY = "PAY";

    /**
     * MPGS 授权操作，平台 AUTHORIZATION 和 PRE_AUTHORIZATION 都映射为该操作。
     */
    public static final String AUTHORIZE = "AUTHORIZE";

    /**
     * MPGS 更新授权操作，平台 INCREMENTAL_AUTHORIZATION 映射为该操作。
     */
    public static final String UPDATE_AUTHORIZATION = "UPDATE_AUTHORIZATION";

    /**
     * MPGS 请款操作。
     */
    public static final String CAPTURE = "CAPTURE";

    /**
     * MPGS 退款操作。
     */
    public static final String REFUND = "REFUND";

    /**
     * MPGS 撤销操作，平台 VOID 和 REVERSAL 暂统一映射为该操作。
     */
    public static final String VOID = "VOID";

    /**
     * MPGS 查询操作，当前实现使用 GET 交易 URL，不进入 JSON 请求体。
     */
    public static final String RETRIEVE = "RETRIEVE";

    /**
     * MPGS 3DS 认证初始化操作。
     */
    public static final String INITIATE_AUTHENTICATION = "INITIATE_AUTHENTICATION";

    /**
     * MPGS 3DS 持卡人认证操作。
     */
    public static final String AUTHENTICATE_PAYER = "AUTHENTICATE_PAYER";

    /**
     * MPGS 卡支付资金来源类型。
     */
    public static final String CARD = "CARD";

    private MpgsApiOperation() {
    }
}
