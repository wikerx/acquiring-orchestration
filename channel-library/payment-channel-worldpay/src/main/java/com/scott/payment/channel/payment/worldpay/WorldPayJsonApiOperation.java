package com.scott.payment.channel.payment.worldpay;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : WorldPayJsonApiOperation
 * @date : 2026-07-26 00:00
 * @email : scott_x@163.com
 * @description : WorldPay JSON API 操作常量，位于 payment-channel-worldpay 渠道适配层，用于统一 WPGJSON 请求体 operation 字段和日志 operation 字段。
 * @status : create
 */
final class WorldPayJsonApiOperation {

    /**
     * 支付操作，表示持卡人一次性授权并请款的渠道交易。
     */
    static final String PAYMENT = "PAYMENT";

    /**
     * 授权操作，表示只冻结额度、不立即请款的渠道交易。
     */
    static final String AUTHORIZE = "AUTHORIZE";

    /**
     * 预授权操作，平台语义与授权一致，但保留 PRE_AUTHORIZE 便于渠道侧和后台排查区分。
     */
    static final String PRE_AUTHORIZE = "PRE_AUTHORIZE";

    /**
     * 请款操作，表示对既有授权或支付订单发起资金捕获。
     */
    static final String CAPTURE = "CAPTURE";

    /**
     * 退款操作，表示对既有支付或请款发起退还资金。
     */
    static final String REFUND = "REFUND";

    /**
     * 撤销操作，表示取消未完成或可撤销的授权、支付动作。
     */
    static final String VOID = "VOID";

    /**
     * 查询操作，表示按渠道订单号和渠道交易号查询交易状态。
     */
    static final String QUERY = "QUERY";

    private WorldPayJsonApiOperation() {
    }
}
